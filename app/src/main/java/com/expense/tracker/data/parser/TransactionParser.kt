package com.expense.tracker.data.parser

import com.expense.tracker.data.db.TransactionEntity
import com.expense.tracker.data.db.TxnType

/**
 * Shared parser for Pakistani bank / wallet alerts (Allied, Meezan, HBL, UBL,
 * NayaPay, JazzCash, etc.). Label-based fields like "Beneficiary Name" apply to
 * every bank — not Allied only.
 */
object TransactionParser {

    private val amountRegex = Regex(
        """(?:Rs\.?|PKR\.?|₨)\s*:?\s*([\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    private val debitKeywords = listOf(
        "debited", "debit transaction", "have been sent", "has been sent",
        "amount sent", "you sent", "sent from your", "sent to", "withdrawn",
        "withdrawal", "paid", "purchase", "payment of", "transferred to",
        "payment made", "funds transferred", "debit"
    )
    private val creditKeywords = listOf(
        "credited", "credit transaction", "have been received", "has been received",
        "amount received", "you got", "received", "deposited", "deposit",
        "transferred from", "funds received", "credit"
    )

    private val accountRegexes = listOf(
        Regex(
            """(?:a/c|account|acct|card)\s*(?:no\.?|number|#)?\s*:?\s*(?:ending\s*(?:in|with)?)?\s*[Xx*\d]*(\d{4})\b""",
            RegexOption.IGNORE_CASE
        ),
        Regex("""\b(?:IBAN|Raast\s*ID)\s*[/:.]?\s*[A-Z0-9*]*?(\d{4})\b""", RegexOption.IGNORE_CASE),
        Regex("""\bxxx+(\d{4})\b""", RegexOption.IGNORE_CASE),
        Regex("""\*{2,}(\d{4})\b""")
    )

    private const val NAME_END =
        """(?=\s+on\b|\s+via\b|\s+ref\b|\s+for\b|\s+with\b|[.,]|\s*\r?\n|\s*[^\x00-\x7F]|\s*$)"""

    /** Labeled fields used by many Pakistani banks / wallets. */
    private val labeledNameRegexes = listOf(
        Regex(
            """(?:Beneficiary|Receiver|Recipient|Payee|Merchant|Customer)\s*Name\s*:?\s*([A-Z][A-Za-z0-9 .&'\-]{2,50}?)(?=\s*\r?\n|\s*[A-Z][a-z]+\s*:|\s*$)""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """(?:Sender|Remitter|Payer|Source)\s*Name\s*:?\s*([A-Z][A-Za-z0-9 .&'\-]{2,50}?)(?=\s*\r?\n|\s*[A-Z][a-z]+\s*:|\s*$)""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """(?:Beneficiary|Receiver|Destination|Source)\s*Acc(?:ount)?\.?\s*Title\s*:?\s*:?\s*([A-Z][A-Za-z0-9 .&'\-]{2,50}?)(?=\s*\r?\n|\s*[A-Z][a-z]+\s*:|\s*AC#|\s*PK\d{2}|\s*$)""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """Beneficiary Account(?:\s*Title)?(?:\s*:)+\s*([A-Z][A-Za-z0-9.&'\- ]{1,40}?)(?=\s+AC#|\s+PK\d{2}|\s*\r?\n|\s*$)"""
        ),
        Regex(
            """(?:To|From)\s*Account\s*Title\s*:?\s*([A-Z][A-Za-z0-9 .&'\-]{2,50}?)(?=\s*\r?\n|\s*$)""",
            RegexOption.IGNORE_CASE
        )
    )

    private val purchaseDescRegex = Regex(
        """(?:Transaction\s+description|description|narration|particulars)\s*:?\s*(?:PURCHASE\s+|POS\s+|ATM\s+)?([A-Z0-9][A-Za-z0-9 .&'\-]{2,50}?)(?=\s*\r?\n|\s*Instrument|\s*Cheque|\s*Fee|\s*TID|\s*$)""",
        RegexOption.IGNORE_CASE
    )

    private val creditInlineRegexes = listOf(
        Regex("""\bfrom\s+VPA\s+([\w.\-@]+)""", RegexOption.IGNORE_CASE),
        Regex("""\bfrom\s+([\w.\-]+@[\w]+)""", RegexOption.IGNORE_CASE),
        Regex("""Source Acc\.?\s*Title\s*:?\s*([A-Z][A-Za-z .'\-]{2,40}?)$NAME_END"""),
        Regex("""\byou got\s+(?:Rs\.?|PKR\.?|₨)?\s*[\d,]+\.?\d*\s+from\s+([A-Z][A-Za-z .'\-]{2,50}?)$NAME_END""", RegexOption.IGNORE_CASE),
        Regex("""\breceived\s+(?:from|by)\s+(?:Mr\.?\s+|Ms\.?\s+|M/s\.?\s+)?([A-Z][A-Za-z .'\-]{2,50}?)$NAME_END""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:from|by)\s+(?:Mr\.?\s+|Ms\.?\s+|M/s\.?\s+)?([A-Z][A-Za-z '\-]{2,40}?)$NAME_END""")
    )

    private val debitInlineRegexes = listOf(
        purchaseDescRegex,
        Regex("""\bto\s+VPA\s+([\w.\-@]+)""", RegexOption.IGNORE_CASE),
        Regex("""\bto\s+([\w.\-]+@[\w]+)""", RegexOption.IGNORE_CASE),
        Regex("""Destination Acc\.?\s*Title\s*:?\s*([A-Z][A-Za-z .'\-]{2,40}?)$NAME_END"""),
        Regex("""\byou sent\s+(?:Rs\.?|PKR\.?|₨)?\s*[\d,]+\.?\d*\s+to\s+([A-Z][A-Za-z .'\-]{2,50}?)$NAME_END""", RegexOption.IGNORE_CASE),
        Regex("""\bat\s+([A-Z0-9][\w .&'\-*]{2,40}?)$NAME_END"""),
        Regex("""\bto\s+(?:Mr\.?\s+|Ms\.?\s+|M/s\.?\s+)?([A-Z][A-Za-z '\-]{2,40}?)$NAME_END""")
    )

    private val subjectNameRegexes = listOf(
        Regex("""you (?:got|received)\s+(?:Rs\.?|PKR\.?|₨)?\s*[\d,]+\.?\d*\s+from\s+(.+?)(?:\s*[^\x00-\x7F]|\s*$)""", RegexOption.IGNORE_CASE),
        Regex("""you sent\s+(?:Rs\.?|PKR\.?|₨)?\s*[\d,]+\.?\d*\s+to\s+(.+?)(?:\s*[^\x00-\x7F]|\s*$)""", RegexOption.IGNORE_CASE),
        Regex("""(?:payment|transfer)\s+to\s+([A-Z][A-Za-z .'\-]{2,50})""", RegexOption.IGNORE_CASE),
        Regex("""(?:payment|transfer)\s+from\s+([A-Z][A-Za-z .'\-]{2,50})""", RegexOption.IGNORE_CASE)
    )

    private val counterpartyStopWords = setOf(
        "your", "account", "the", "bank", "info", "credit", "debit", "call", "sms",
        "net", "banking", "upi", "neft", "imps", "rtgs", "atm", "linked", "raast",
        "transfer", "transaction", "description", "purchase", "dear", "customer",
        "branch", "through", "myabl", "mobile", "app", "pakistan", "limited"
    )

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
            else -> return null
        }

        val accountLast4 = accountRegexes.firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1) }

        val counterparty = extractCounterparty(type, text, subject)

        return TransactionEntity(
            id = messageId,
            bank = bank,
            accountLast4 = accountLast4,
            amount = amount,
            type = type,
            counterparty = counterparty,
            timestamp = timestamp,
            subject = subject,
            categoryId = null
        )
    }

    private fun extractCounterparty(type: TxnType, text: String, subject: String): String? {
        // 1) Labeled fields (Beneficiary Name, Sender Name, Acc Title, …) — all banks
        val labeled = labeledNameRegexes.firstNotNullOfOrNull { regex ->
            regex.findAll(text).map { it.groupValues[1].trim() }.firstOrNull(::isGoodName)
        }
        if (labeled != null) return cleanName(labeled)

        // 2) Direction-specific inline phrasing
        val inline = (if (type == TxnType.DEBIT) debitInlineRegexes else creditInlineRegexes)
            .firstNotNullOfOrNull { regex ->
                regex.findAll(text).map { it.groupValues[1].trim() }.firstOrNull(::isGoodName)
            }
        if (inline != null) return cleanName(inline)

        // 3) Subject line (NayaPay-style and similar)
        val fromSubject = subjectNameRegexes.firstNotNullOfOrNull { regex ->
            regex.find(subject)?.groupValues?.get(1)?.trim()?.takeIf(::isGoodName)
        }
        return fromSubject?.let(::cleanName)
    }

    private fun isGoodName(candidate: String): Boolean {
        if (candidate.length !in 3..60) return false
        if (candidate.all { it.isDigit() || it == '*' || it == 'X' || it == 'x' || it == ' ' }) return false
        return candidate.lowercase().split(Regex("""[\s./\-]+""")).none { it in counterpartyStopWords }
    }

    private fun cleanName(name: String): String =
        name.trimEnd('.', ',', ' ', '\t').replace(Regex("""\s+"""), " ")
}
