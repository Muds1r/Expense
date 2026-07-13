package com.expense.tracker.data.gmail

import android.accounts.Account
import android.content.Context
import android.util.Base64
import com.expense.tracker.data.db.TransactionEntity
import com.expense.tracker.data.parser.BankRegistry
import com.expense.tracker.data.parser.TransactionParser
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import com.google.api.services.gmail.model.Message
import com.google.api.services.gmail.model.MessagePart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fetches bank alert emails via the Gmail API using the READONLY scope only —
 * the app cannot modify or delete anything in Gmail.
 */
class GmailClient(context: Context, account: Account) {

    private val service: Gmail

    init {
        val credential = GoogleAccountCredential.usingOAuth2(
            context.applicationContext,
            listOf(GmailScopes.GMAIL_READONLY)
        )
        credential.selectedAccount = account
        service = Gmail.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("ExpenseTracker")
            .build()
    }

    /**
     * Fetch and parse all bank alert emails from the last [days] days.
     * Runs entirely on IO; throws UserRecoverableAuthIOException if consent is needed.
     */
    suspend fun fetchTransactions(days: Int = 60): List<TransactionEntity> =
        withContext(Dispatchers.IO) {
            val query = "${BankRegistry.senderQuery()} newer_than:${days}d"
            val results = mutableListOf<TransactionEntity>()
            var pageToken: String? = null

            do {
                val response = service.users().messages().list("me")
                    .setQ(query)
                    .setMaxResults(100)
                    .setPageToken(pageToken)
                    .execute()

                for (ref in response.messages.orEmpty()) {
                    val message = service.users().messages().get("me", ref.id)
                        .setFormat("full")
                        .execute()
                    parseMessage(message)?.let { results.add(it) }
                }
                pageToken = response.nextPageToken
            } while (pageToken != null)

            results
        }

    private fun parseMessage(message: Message): TransactionEntity? {
        val headers = message.payload?.headers.orEmpty()
        val from = headers.firstOrNull { it.name.equals("From", true) }?.value ?: return null
        val subject = headers.firstOrNull { it.name.equals("Subject", true) }?.value ?: ""
        val body = extractBody(message.payload).ifBlank { message.snippet ?: "" }

        return TransactionParser.parse(
            messageId = message.id,
            fromHeader = from,
            subject = subject,
            body = body,
            timestamp = message.internalDate ?: return null
        )
    }

    private fun extractBody(part: MessagePart?): String {
        if (part == null) return ""
        val sb = StringBuilder()
        part.body?.data?.let { data ->
            val decoded = runCatching {
                String(Base64.decode(data, Base64.URL_SAFE))
            }.getOrDefault("")
            sb.append(if (part.mimeType == "text/html") stripHtml(decoded) else decoded)
            sb.append('\n')
        }
        part.parts?.forEach { sb.append(extractBody(it)) }
        return sb.toString()
    }

    private fun stripHtml(html: String): String =
        html.replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace(Regex(" {2,}"), " ")
}
