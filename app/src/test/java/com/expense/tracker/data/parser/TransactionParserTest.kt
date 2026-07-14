package com.expense.tracker.data.parser

import com.expense.tracker.data.db.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Real (anonymized-ish) alert emails from the user's inbox. */
class TransactionParserTest {

    private val nayaPayFooter = """
        Do not reply to this email. Responses to this email are not monitored.
        NayaPay (Private) Limited is authorised and regulated by the State Bank of Pakistan as an Electronic Money Institution.
        NayaPay, its employees or agents will never ask you for your password, MPIN, OTP or debit card PIN, via call, email, SMS, web links or social media.
    """.trimIndent()

    @Test
    fun `nayapay received email parses as credit with sender name`() {
        val txn = TransactionParser.parse(
            messageId = "1",
            fromHeader = "NayaPay <service@nayapay.com>",
            subject = "You got Rs. 1,900 from Muhammad Mudassar Munir Khan \uD83C\uDF89",
            body = """
                NayaPay
                Cha-Ching!
                Muhammad Mudassar Munir Khan
                Meezan-9374
                14 Jul 2026, 05:29 PM
                Rs. 1,900
                AMOUNT DETAILS
                Amount Received
                Rs. 1,900
                Service Fee (Incl. Tax)
                Rs. 0
                ADDITIONAL INFORMATION
                Source Acc. Title
                Muhammad Mudassar Munir Khan
                Source Bank
                Meezan Bank
                Channel
                Raast
            """.trimIndent() + "\n" + nayaPayFooter,
            timestamp = 1000L
        )
        assertNotNull(txn)
        assertEquals(TxnType.CREDIT, txn!!.type)
        assertEquals(1900.0, txn.amount, 0.01)
        assertEquals("NayaPay", txn.bank)
        assertEquals("Muhammad Mudassar Munir Khan", txn.counterparty)
    }

    @Test
    fun `nayapay sent email parses as debit with recipient name`() {
        val txn = TransactionParser.parse(
            messageId = "2",
            fromHeader = "NayaPay <service@nayapay.com>",
            subject = "You sent Rs. 1,000 to Muhammad Amin \uD83D\uDCB8",
            body = """
                NayaPay
                Muhammad Amin
                JazzCash/Mobilink MFB-7099
                14 Jul 2026, 05:39 PM
                - Rs. 1,000
                AMOUNT DETAILS
                Amount Sent
                Rs. 1,000
                ADDITIONAL INFORMATION
                Destination Acc. Title
                Muhammad Amin
                Destination Bank
                JazzCash - Mobilink Microfinance Bank
                Channel
                Raast
            """.trimIndent() + "\n" + nayaPayFooter,
            timestamp = 1000L
        )
        assertNotNull(txn)
        assertEquals(TxnType.DEBIT, txn!!.type)
        assertEquals(1000.0, txn.amount, 0.01)
        assertEquals("Muhammad Amin", txn.counterparty)
    }

    @Test
    fun `meezan received email parses as credit with beneficiary`() {
        val txn = TransactionParser.parse(
            messageId = "3",
            fromHeader = "Meezan Bank Alert <no-reply@meezanbank.com>",
            subject = "Transaction Alert",
            body = """
                Dear Customer,
                PKR 2,000.00 received to your account xxx9374 with the following details:
                Beneficiary Account : M.MUNIR AC# RAAST PYMT PK62ABPA00100
                Branch : I-8 MARKAZ BR ISD
                Transaction Date : 14-Jul-2026
                For Any Inquiry, Please do not hesitate to contact us at 111-331-331
                Copyright © 2005, Meezan Bank Limited
            """.trimIndent(),
            timestamp = 1000L
        )
        assertNotNull(txn)
        assertEquals(TxnType.CREDIT, txn!!.type)
        assertEquals(2000.0, txn.amount, 0.01)
        assertEquals("Meezan Bank", txn.bank)
        assertEquals("9374", txn.accountLast4)
        assertEquals("M.MUNIR", txn.counterparty)
    }

    @Test
    fun `meezan sent email parses as debit with beneficiary`() {
        val txn = TransactionParser.parse(
            messageId = "4",
            fromHeader = "Meezan Bank Alert <no-reply@meezanbank.com>",
            subject = "Transaction Alert",
            body = """
                Dear Customer,
                PKR 1,900.00 sent from your account xxx9374 with the following details:
                Beneficiary Account Title: : M.MUDASIR
                Branch : I-8 MARKAZ BR ISD
                Transaction Date : 14-Jul-2026
                DISCLAIMER:
                "The dissemination, distribution, copying or disclosure of this message, or its contents is strictly prohibited unless authorized by Meezan Bank Limited."
            """.trimIndent(),
            timestamp = 1000L
        )
        assertNotNull(txn)
        assertEquals(TxnType.DEBIT, txn!!.type)
        assertEquals(1900.0, txn.amount, 0.01)
        assertEquals("M.MUDASIR", txn.counterparty)
    }

    @Test
    fun `otp and promo emails are skipped`() {
        val txn = TransactionParser.parse(
            messageId = "5",
            fromHeader = "Meezan Bank Alert <no-reply@meezanbank.com>",
            subject = "One Time Password",
            body = "Your OTP is 123456. Do not share it with anyone.",
            timestamp = 1000L
        )
        assertEquals(null, txn)
    }
}
