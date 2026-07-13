package com.expense.tracker.data.parser

/**
 * Maps sender email domains to display names of Pakistani banks, digital banks,
 * microfinance banks, and wallets. The Gmail query in
 * [com.expense.tracker.data.gmail.GmailClient] is built from this list, so adding
 * a domain automatically includes it in sync.
 *
 * Matching is by domain suffix, so "alerts.hbl.com" or "no-reply.mcb.com.pk"
 * match automatically. If a bank's alerts aren't picked up, open one of its
 * emails in Gmail, check the sender's domain, and add it here.
 */
object BankRegistry {

    val domainToBank: Map<String, String> = mapOf(
        // Commercial banks
        "abl.com" to "Allied Bank",
        "hbl.com" to "HBL",
        "ubl.com.pk" to "UBL",
        "ubldigital.com" to "UBL",
        "mcb.com.pk" to "MCB Bank",
        "nbp.com.pk" to "National Bank of Pakistan",
        "meezanbank.com" to "Meezan Bank",
        "bankalfalah.com" to "Bank Alfalah",
        "bankalhabib.com" to "Bank AL Habib",
        "askaribank.com.pk" to "Askari Bank",
        "askaribank.com" to "Askari Bank",
        "faysalbank.com" to "Faysal Bank",
        "sc.com" to "Standard Chartered",
        "jsbl.com" to "JS Bank",
        "bop.com.pk" to "Bank of Punjab",
        "soneribank.com" to "Soneri Bank",
        "habibmetro.com" to "Habib Metropolitan Bank",
        "bok.com.pk" to "Bank of Khyber",
        "bankislami.com.pk" to "BankIslami",
        "dibpak.com" to "Dubai Islamic Bank",
        "albaraka.com.pk" to "Al Baraka Bank",
        "mcbislamicbank.com" to "MCB Islamic Bank",
        "fwbl.com.pk" to "First Women Bank",
        "samba.com.pk" to "Samba Bank",
        "sindhbank.com.pk" to "Sindh Bank",
        "ztbl.com.pk" to "Zarai Taraqiati Bank",
        "bankmakramah.com" to "Bank Makramah",
        "summitbank.com.pk" to "Bank Makramah",

        // Digital-only banks
        "easypaisa.com.pk" to "Easypaisa Bank",
        "telenorbank.pk" to "Easypaisa Bank",
        "mashreq.com" to "Mashreq Bank",
        "mashreqbank.com" to "Mashreq Bank",
        "raqami.com.pk" to "Raqami",
        "hugobank.com.pk" to "HugoBank",
        "buraq.com.pk" to "Buraq Bank",

        // Microfinance banks
        "advanspakistan.com" to "Advans Pakistan",
        "apnabank.com.pk" to "APNA Microfinance Bank",
        "finca.pk" to "FINCA Microfinance Bank",
        "hblmfb.com" to "HBL Microfinance Bank",
        "khushhalibank.com.pk" to "Khushhali Microfinance Bank",
        "mobilinkbank.com" to "Mobilink Microfinance Bank",
        "nrspbank.com" to "NRSP Microfinance Bank",
        "pomicro.com" to "Pak Oman Microfinance Bank",
        "sindhmfb.com" to "Sindh Microfinance Bank",
        "ubank.com.pk" to "U Microfinance Bank",

        // Wallets and branchless banking
        "jazzcash.com.pk" to "JazzCash",
        "sadapay.pk" to "SadaPay",
        "nayapay.com" to "NayaPay",
        "upaisa.com.pk" to "UPaisa",
        "zindigi.com" to "Zindigi"
        // Konnect by HBL sends from hbl.com, already covered above.
    )

    /** Resolve a bank name from the From: header, e.g. "HBL <InfoDesk@hbl.com>". */
    fun bankFromSender(fromHeader: String): String? {
        val email = Regex("[\\w.+-]+@([\\w.-]+)").find(fromHeader)?.groupValues?.get(1)?.lowercase()
            ?: return null
        // Match the longest suffix so a more specific domain wins.
        return domainToBank.entries
            .filter { email == it.key || email.endsWith("." + it.key) }
            .maxByOrNull { it.key.length }
            ?.value
    }

    /** Gmail search query fragment matching all known bank domains. */
    fun senderQuery(): String =
        domainToBank.keys.joinToString(" OR ", prefix = "from:(", postfix = ")") { it }
}
