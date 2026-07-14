package com.expense.tracker.data.mail

import com.expense.tracker.data.db.TransactionEntity
import com.expense.tracker.data.parser.BankRegistry
import com.expense.tracker.data.parser.TransactionParser
import com.sun.mail.imap.IMAPFolder
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

    suspend fun fetchTransactions(days: Int = 60): List<TransactionEntity> =
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
                    val messages = folder.search(buildSearchTerm(days))
                    val profile = FetchProfile().apply {
                        add(FetchProfile.Item.ENVELOPE)
                        add("Message-ID")
                    }
                    folder.fetch(messages, profile)
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

    private fun buildSearchTerm(days: Int): SearchTerm {
        val cutoff = Date(System.currentTimeMillis() - days * 86_400_000L)
        val fromTerms = BankRegistry.domainToBank.keys
            .map { FromStringTerm(it) as SearchTerm }
            .toTypedArray()
        return AndTerm(ReceivedDateTerm(ComparisonTerm.GE, cutoff), OrTerm(fromTerms))
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
