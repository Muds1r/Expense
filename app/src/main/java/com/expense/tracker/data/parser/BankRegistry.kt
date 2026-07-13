package com.expense.tracker.data.parser

/**
 * Maps sender email domains to display names of banks.
 * Add your banks here — the Gmail query in [com.expense.tracker.data.gmail.GmailClient]
 * is built from this list, so adding a domain automatically includes it in sync.
 */
object BankRegistry {

    val domainToBank: Map<String, String> = mapOf(
        "hdfcbank.net" to "HDFC Bank",
        "hdfcbank.com" to "HDFC Bank",
        "icicibank.com" to "ICICI Bank",
        "axisbank.com" to "Axis Bank",
        "sbi.co.in" to "SBI",
        "alerts.sbi.co.in" to "SBI",
        "kotak.com" to "Kotak Bank",
        "pnb.co.in" to "PNB",
        "idfcfirstbank.com" to "IDFC First Bank",
        "yesbank.in" to "Yes Bank",
        "indusind.com" to "IndusInd Bank",
        "bankofbaroda.co.in" to "Bank of Baroda",
        "unionbankofindia.bank" to "Union Bank",
        "canarabank.com" to "Canara Bank",
        "federalbank.co.in" to "Federal Bank",
        "aubank.in" to "AU Bank",
        "paytmbank.com" to "Paytm Bank"
    )

    /** Resolve a bank name from the From: header, e.g. "HDFC Bank <alerts@hdfcbank.net>". */
    fun bankFromSender(fromHeader: String): String? {
        val email = Regex("[\\w.+-]+@([\\w.-]+)").find(fromHeader)?.groupValues?.get(1)?.lowercase()
            ?: return null
        // Match the longest suffix so "alerts.sbi.co.in" wins over "sbi.co.in".
        return domainToBank.entries
            .filter { email == it.key || email.endsWith("." + it.key) }
            .maxByOrNull { it.key.length }
            ?.value
    }

    /** Gmail search query fragment matching all known bank domains. */
    fun senderQuery(): String =
        domainToBank.keys.joinToString(" OR ", prefix = "from:(", postfix = ")") { it }
}
