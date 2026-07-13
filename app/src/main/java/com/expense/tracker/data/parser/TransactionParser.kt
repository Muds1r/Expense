package com.expense.tracker.data.parser

import com.expense.tracker.data.db.TransactionEntity
import com.expense.tracker.data.db.TxnType

/**
 * Generic parser for Indian bank transaction alert emails.
 *
 * Banks phrase alerts differently but almost all follow the shape:
 *   "<Rs/INR> <amount> <debited/credited/...> ... <to/from/at> <counterparty>"
 * We extract each piece with tolerant regexes instead of one brittle mega-pattern.
 */
object TransactionParser {

    private val amountRegex = Regex(
        """(?:Rs\.?|INR|₹)\s*([\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    private val debitKeywords = listOf(
        "debited", "spent", "sent", "withdrawn", "paid", "purchase", "payment of", "debit"
    )
    private val creditKeywords = listOf(
        "credited", "received", "deposited", "credit"
    )

    private val accountRegex = Regex(
        """(?:a/c|account|acct|card)\s*(?:no\.?)?\s*(?:ending\s*(?:in|with)?)?\s*[Xx*]*(\d{4})""",
        RegexOption.IGNORE_CASE
    )

    // "to VPA merchant@upi", "from john@okhdfcbank", "at AMAZON", "to Mr JOHN DOE", "by NEFT from ACME CORP"
    private val counterpartyRegexes = listOf(
        Regex("""(?:to|from)\s+VPA\s+([\w.\-@]+)""", RegexOption.IGNORE_CASE),
        Regex("""(?:to|from)\s+([\w.\-]+@[\w]+)""", RegexOption.IGNORE_CASE),
        Regex("""\bat\s+([A-Z0-9][\w .&'\-*]{2,40}?)(?:\s+on\b|\.|,|$)"""),
        Regex(
            """(?:to|from|by)\s+(?:Mr\.?\s+|Ms\.?\s+|M/s\.?\s+)?([A-Z][A-Za-z .&'\-]{2,40}?)(?:\s+on\b|\s+via\b|\s+ref\b|\.|,|$)"""
        )
    )

    // Words that the counterparty regex can capture by mistake.
    private val counterpartyStopWords = setOf(
        "your", "account", "the", "bank", "info", "credit", "debit", "call", "sms",
        "net", "banking", "upi", "neft", "imps", "rtgs", "atm", "linked"
    )

    /**
     * @param messageId Gmail message ID (used as primary key)
     * @param fromHeader value of the From: header
     * @param subject email subject
     * @param body plain-text body (or snippet as fallback)
     * @param timestamp email internal date, epoch millis
     * @return parsed transaction, or null if this email doesn't look like a transaction alert
     */
    fun parse(
        messageId: String,
        fromHeader: String,
        subject: String,
        body: String,
        timestamp: Long
    ): TransactionEntity? {
        val bank = BankRegistry.bankFromSender(fromHeader) ?: return null
        val text = (subject + "\n" + body).replace("\u00a0", " ")

        val amountMatch = amountRegex.find(text) ?: return null
        val amount = amountMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        if (amount <= 0.0) return null

        val lower = text.lowercase()
        val debitIdx = debitKeywords.mapNotNull { lower.indexOf(it).takeIf { i -> i >= 0 } }.minOrNull()
        val creditIdx = creditKeywords.mapNotNull { lower.indexOf(it).takeIf { i -> i >= 0 } }.minOrNull()
        val type = when {
            debitIdx != null && (creditIdx == null || debitIdx < creditIdx) -> TxnType.DEBIT
            creditIdx != null -> TxnType.CREDIT
            else -> return null // Not a transaction alert (OTP, promo, statement, etc.)
        }

        val accountLast4 = accountRegex.find(text)?.groupValues?.get(1)

        val counterparty = counterpartyRegexes.firstNotNullOfOrNull { regex ->
            regex.find(text)?.groupValues?.get(1)?.trim()?.takeIf { candidate ->
                candidate.length in 3..60 &&
                    candidate.lowercase().split(" ").none { it in counterpartyStopWords }
            }
        }

        return TransactionEntity(
            id = messageId,
            bank = bank,
            accountLast4 = accountLast4,
            amount = amount,
            type = type,
            counterparty = counterparty?.trimEnd('.', ',', ' '),
            timestamp = timestamp,
            subject = subject
        )
    }
}
