package com.expense.tracker.data.parser

import com.expense.tracker.data.db.TransactionEntity
import com.expense.tracker.data.db.TxnType

/**
 * Generic parser for Pakistani bank / wallet transaction alert emails.
 *
 * Banks phrase alerts differently but almost all follow the shape:
 *   "<Rs/PKR> <amount> <debited/credited/...> ... <to/from/beneficiary>"
 * We extract each piece with tolerant regexes instead of one brittle mega-pattern.
 */
object TransactionParser {

    private val amountRegex = Regex(
        """(?:Rs\.?|PKR\.?|₨)\s*:?\s*([\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    private val debitKeywords = listOf(
        "debited", "debit transaction", "have been sent", "has been sent",
        "spent", "you sent", "sent from your", "sent to", "withdrawn",
        "withdrawal", "paid", "purchase", "payment of", "transferred to", "debit"
    )
    private val creditKeywords = listOf(
        "credited", "credit transaction", "have been received", "has been received",
        "received", "you got", "deposited", "deposit", "transferred from", "credit"
    )

    private val accountRegex = Regex(
        """(?:a/c|account|acct|card)\s*(?:no\.?)?\s*:?\s*(?:ending\s*(?:in|with)?)?\s*[Xx*\d]*(\d{4})\b""",
        RegexOption.IGNORE_CASE
    )

    // Name endings: "on <date>", "via", "ref", punctuation, newline, emoji, or end.
    private const val NAME_END = """(?=\s+on\b|\s+via\b|\s+ref\b|[.,]|\s*\r?\n|\s*[^\x00-\x7F]|\s*$)"""

    // Allied / myABL: "Beneficiary Name : SAIFULLAH AKHTAR"
    private val beneficiaryNameRegex = Regex(
        """Beneficiary Name\s*:?\s*([A-Z][A-Za-z .'\-]{2,50}?)(?=\s*\r?\n|\s*Beneficiary|\s*Fee|\s*Transaction|\s*$)""",
        RegexOption.IGNORE_CASE
    )

    // Meezan: "Beneficiary Account : M.MUNIR AC# ..." / "Beneficiary Account Title: : M.MUDASIR"
    private val beneficiaryAccountRegex = Regex(
        """Beneficiary Account(?:\s*Title)?(?:\s*:)+\s*([A-Z][A-Za-z0-9.&'\- ]{1,40}?)(?=\s+AC#|\s+PK\d{2}|\s*\r?\n|\s*$)"""
    )

    // "Transaction description : PURCHASE SAFFRON FOODIES RAWALPINDI PBPK"
    private val purchaseDescRegex = Regex(
        """(?:Transaction\s+description|description)\s*:?\s*(?:PURCHASE\s+)?([A-Z0-9][A-Za-z0-9 .&'\-]{2,50}?)(?=\s*\r?\n|\s*Instrument|\s*Cheque|\s*Fee|\s*$)""",
        RegexOption.IGNORE_CASE
    )

    private val creditCounterpartyRegexes = listOf(
        beneficiaryNameRegex,
        beneficiaryAccountRegex,
        Regex("""\bfrom\s+VPA\s+([\w.\-@]+)""", RegexOption.IGNORE_CASE),
        Regex("""\bfrom\s+([\w.\-]+@[\w]+)""", RegexOption.IGNORE_CASE),
        Regex("""Source Acc\.?\s*Title\s*:?\s*([A-Z][A-Za-z .'\-]{2,40}?)$NAME_END"""),
        Regex("""\b(?:from|by)\s+(?:Mr\.?\s+|Ms\.?\s+|M/s\.?\s+)?([A-Z][A-Za-z '\-]{2,40}?)$NAME_END""")
    )

    private val debitCounterpartyRegexes = listOf(
        beneficiaryNameRegex,
        beneficiaryAccountRegex,
        purchaseDescRegex,
        Regex("""\bto\s+VPA\s+([\w.\-@]+)""", RegexOption.IGNORE_CASE),
        Regex("""\bto\s+([\w.\-]+@[\w]+)""", RegexOption.IGNORE_CASE),
        Regex("""Destination Acc\.?\s*Title\s*:?\s*([A-Z][A-Za-z .'\-]{2,40}?)$NAME_END"""),
        Regex("""\bat\s+([A-Z0-9][\w .&'\-*]{2,40}?)$NAME_END"""),
        Regex("""\bto\s+(?:Mr\.?\s+|Ms\.?\s+|M/s\.?\s+)?([A-Z][A-Za-z '\-]{2,40}?)$NAME_END""")
    )

    private val counterpartyStopWords = setOf(
        "your", "account", "the", "bank", "info", "credit", "debit", "call", "sms",
        "net", "banking", "upi", "neft", "imps", "rtgs", "atm", "linked", "raast",
        "transfer", "transaction", "description", "purchase"
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

        val accountLast4 = accountRegex.find(text)?.groupValues?.get(1)

        val regexes = if (type == TxnType.DEBIT) debitCounterpartyRegexes else creditCounterpartyRegexes
        val counterparty = regexes.firstNotNullOfOrNull { regex ->
            regex.findAll(text)
                .map { it.groupValues[1].trim() }
                .firstOrNull { candidate ->
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
            subject = subject,
            categoryId = null
        )
    }
}
