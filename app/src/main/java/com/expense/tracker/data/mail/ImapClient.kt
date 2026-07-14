package com.expense.tracker.data.mail

import com.expense.tracker.data.db.TransactionEntity
import com.expense.tracker.data.parser.BankRegistry
import com.expense.tracker.data.parser.TransactionParser
import com.sun.mail.iap.Argument
import com.sun.mail.iap.ProtocolException
import com.sun.mail.imap.IMAPFolder
import com.sun.mail.imap.protocol.IMAPResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Properties
import javax.mail.FetchProfile
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.search.AndTerm
import javax.mail.search.ComparisonTerm
import javax.mail.search.FromStringTerm
import javax.mail.search.OrTerm
import javax.mail.search.ReceivedDateTerm
import javax.mail.search.SearchTerm

/**
 * Fetches bank alert emails from Gmail over IMAP using a Google App Password.
 *
 * No OAuth, no Google Cloud project, no expiring grants — the app password works
 * until you revoke it at myaccount.google.com/apppasswords. The folder is opened
 * READ_ONLY, so nothing in the mailbox can be modified, flagged, or deleted.
 */
class ImapClient(private val email: String, private val appPassword: String) {

    /**
     * Quick credential check: connects and opens the mailbox, nothing more.
     * Throws AuthenticationFailedException on a bad email/app password.
     */
    suspend fun verifyLogin(): Unit = withContext(Dispatchers.IO) {
        val store = Session.getInstance(Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.ssl.enable", "true")
            put("mail.imaps.connectiontimeout", "20000")
            put("mail.imaps.timeout", "30000")
        }).getStore("imaps")
        store.connect("imap.gmail.com", 993, email, appPassword)
        store.close()
    }

    /** Fetch and parse all bank alert emails received after [sinceEpochMs]. */
    suspend fun fetchTransactions(sinceEpochMs: Long): List<TransactionEntity> =
        withContext(Dispatchers.IO) {
            val props = Properties().apply {
                put("mail.store.protocol", "imaps")
                put("mail.imaps.ssl.enable", "true")
                put("mail.imaps.connectiontimeout", "20000")
                put("mail.imaps.timeout", "60000")
            }
            val store = Session.getInstance(props).getStore("imaps")
            store.connect("imap.gmail.com", 993, email, appPassword)
            try {
                val folder = findAllMailFolder(store)
                folder.open(Folder.READ_ONLY)
                try {
                    val messages = searchBankMessages(folder, sinceEpochMs)
                    val profile = FetchProfile().apply {
                        add(FetchProfile.Item.ENVELOPE)
                        add("Message-ID")
                    }
                    folder.fetch(messages.toTypedArray(), profile)
                    messages.mapNotNull { parseMessage(it) }
                } finally {
                    folder.close(false)
                }
            } finally {
                store.close()
            }
        }

    /**
     * Prefer Gmail's "All Mail" folder (covers archived mail, any label) by its
     * \All attribute, which works regardless of the account's display language.
     */
    private fun findAllMailFolder(store: javax.mail.Store): Folder {
        val gmailRoot = store.defaultFolder.list("[Gmail]*").firstOrNull()
        val allMail = gmailRoot?.list("%")?.firstOrNull { folder ->
            (folder as? IMAPFolder)?.attributes?.contains("\\All") == true
        }
        return allMail ?: store.getFolder("INBOX")
    }

    /**
     * Prefer Gmail's X-GM-RAW search extension: it runs the exact same query
     * syntax as the Gmail search box in one server-side call, which is both
     * fast and reliable (IMAP OR terms are flaky on Gmail and silently drop
     * senders). Falls back to chunked standard IMAP search if unavailable.
     * Every result is re-checked client-side against the sender's domain by
     * the parser, so server-side over-matching is harmless.
     */
    private fun searchBankMessages(folder: Folder, sinceEpochMs: Long): List<Message> {
        if (folder is IMAPFolder) {
            try {
                return gmailRawSearch(folder, sinceEpochMs).toList()
            } catch (e: Exception) {
                // Not Gmail or extension rejected — use the standard path below.
            }
        }
        return chunkedSearch(folder, sinceEpochMs)
    }

    private fun gmailRawSearch(folder: IMAPFolder, sinceEpochMs: Long): Array<Message> {
        // Gmail accepts epoch seconds in after:, which avoids timezone issues.
        val query = "from:(" + BankRegistry.domainToBank.keys.joinToString(" OR ") + ")" +
            " after:" + sinceEpochMs / 1000

        @Suppress("UNCHECKED_CAST")
        val numbers = folder.doCommand { protocol ->
            val args = Argument()
            args.writeAtom("X-GM-RAW")
            args.writeString(query)
            val responses = protocol.command("SEARCH", args)
            if (!responses.last().isOK) {
                throw ProtocolException("X-GM-RAW SEARCH failed: " + responses.last())
            }
            val result = mutableListOf<Int>()
            responses.forEach { r ->
                if (r is IMAPResponse && r.keyEquals("SEARCH")) {
                    // Untagged response looks like "* SEARCH 4 8 15 16".
                    r.toString().split(' ').forEach { token ->
                        token.toIntOrNull()?.let(result::add)
                    }
                }
            }
            result
        } as List<Int>

        return folder.getMessages(numbers.toIntArray())
    }

    private fun chunkedSearch(folder: Folder, sinceEpochMs: Long): List<Message> {
        val since: SearchTerm = ReceivedDateTerm(ComparisonTerm.GE, Date(sinceEpochMs))
        val seen = mutableSetOf<Int>()
        val results = mutableListOf<Message>()

        BankRegistry.domainToBank.keys.chunked(6).forEach { domains ->
            val fromTerms = domains.map { FromStringTerm(it) as SearchTerm }
            val term = if (fromTerms.size == 1) fromTerms[0] else OrTerm(fromTerms.toTypedArray())
            folder.search(AndTerm(since, term)).forEach { message ->
                if (seen.add(message.messageNumber)) results.add(message)
            }
        }
        return results
    }

    private fun parseMessage(message: Message): TransactionEntity? {
        return try {
            val from = message.from?.firstOrNull()?.toString() ?: return null
            val timestamp = (message.receivedDate ?: message.sentDate)?.time ?: return null
            val subject = message.subject ?: ""
            val id = message.getHeader("Message-ID")?.firstOrNull()
                ?: "$from|$subject|$timestamp".hashCode().toString()

            TransactionParser.parse(
                messageId = id,
                fromHeader = from,
                subject = subject,
                body = extractText(message),
                timestamp = timestamp
            )
        } catch (e: Exception) {
            null // Skip malformed messages rather than failing the whole sync.
        }
    }

    private fun extractText(part: Part): String = try {
        when {
            part.isMimeType("text/plain") -> part.content as? String ?: ""
            part.isMimeType("text/html") -> stripHtml(part.content as? String ?: "")
            part.isMimeType("multipart/*") -> {
                val multipart = part.content as Multipart
                (0 until multipart.count).joinToString("\n") { extractText(multipart.getBodyPart(it)) }
            }
            else -> ""
        }
    } catch (e: Exception) {
        ""
    }

    private fun stripHtml(html: String): String =
        html.replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace(Regex(" {2,}"), " ")

    companion object {
        /** App passwords are shown as "xxxx xxxx xxxx xxxx" — strip the spaces. */
        fun cleanAppPassword(raw: String): String = raw.replace(" ", "").trim()
    }
}
