package com.tapledger.data.statement

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StatementPdfParserTest {

    @Test
    fun testParseAmountPaiseInvariants() {
        assertThat(StatementCsvParser.parseAmount("1,234.50")).isEqualTo(1234_50L)
        assertThat(StatementCsvParser.parseAmount("₹5,000.00")).isEqualTo(5000_00L)
        assertThat(StatementCsvParser.parseAmount("Rs. 450.75")).isEqualTo(450_75L)
        assertThat(StatementCsvParser.parseAmount("INR 12,34,567.89")).isEqualTo(1234567_89L)
        assertThat(StatementCsvParser.parseAmount("-250.00")).isEqualTo(-250_00L)
        assertThat(StatementCsvParser.parseAmount("0.00")).isEqualTo(0L)
        assertThat(StatementCsvParser.parseAmount("-")).isEqualTo(0L)
    }

    @Test
    fun testBalanceDetectionFromText() {
        val sampleStatementHeader = """
            HDFC BANK STATEMENT
            Account Number: 1234567890
            Statement Period: 01-Jan-2026 to 31-Jan-2026
            Opening Balance: ₹ 15,250.50
            Closing Balance: ₹ 42,100.00
        """.trimIndent()

        val opening = StatementPdfParser.detectOpeningBalance(sampleStatementHeader)
        val closing = StatementPdfParser.detectClosingBalance(sampleStatementHeader)

        assertThat(opening).isEqualTo(15250_50L)
        assertThat(closing).isEqualTo(42100_00L)
    }

    @Test
    fun testParseTextToLinesTabular() {
        val statementBody = """
            Date        Narration                     Withdrawal    Deposit       Balance
            01/01/2026  UPI-SWIGGY-12345             450.00                      14,800.50
            05/01/2026  SALARY FOR DEC 2025                        50,000.00     64,800.50
            10/01/2026  ELECTRICITY BILL             2,100.00                    62,700.50
        """.trimIndent()

        val lines = StatementPdfParser.parseTextToLines(statementBody, importId = 42L)
        assertThat(lines).hasSize(3)

        // Line 1: Swiggy debit
        assertThat(lines[0].description).isEqualTo("UPI-SWIGGY-12345")
        assertThat(lines[0].debitPaise).isEqualTo(450_00L)
        assertThat(lines[0].creditPaise).isEqualTo(0L)
        assertThat(lines[0].balanceAfterPaise).isEqualTo(14800_50L)

        // Line 2: Salary credit
        assertThat(lines[1].description).isEqualTo("SALARY FOR DEC 2025")
        assertThat(lines[1].debitPaise).isEqualTo(0L)
        assertThat(lines[1].creditPaise).isEqualTo(50000_00L)
        assertThat(lines[1].balanceAfterPaise).isEqualTo(64800_50L)

        // Line 3: Electricity debit
        assertThat(lines[2].description).isEqualTo("ELECTRICITY BILL")
        assertThat(lines[2].debitPaise).isEqualTo(2100_00L)
        assertThat(lines[2].creditPaise).isEqualTo(0L)
        assertThat(lines[2].balanceAfterPaise).isEqualTo(62700_50L)
    }

    @Test
    fun testParseTextWithMultiLineNarration() {
        val statementWithContinuations = """
            Date        Narration                     Withdrawal    Deposit       Balance
            02/01/2026  UPI/SWIGGY/ORDER_98765        350.00                      12,000.00
                        BANGALORE IN
            08/01/2026  NEFT CR-XYZ CORP-REF12345                  25,000.00     37,000.00
                        JANUARY PERFORMANCE BONUS
        """.trimIndent()

        val lines = StatementPdfParser.parseTextToLines(statementWithContinuations, importId = 1L)
        assertThat(lines).hasSize(2)

        assertThat(lines[0].description).isEqualTo("UPI/SWIGGY/ORDER_98765 BANGALORE IN")
        assertThat(lines[0].debitPaise).isEqualTo(350_00L)
        assertThat(lines[0].balanceAfterPaise).isEqualTo(12000_00L)

        assertThat(lines[1].description).isEqualTo("NEFT CR-XYZ CORP-REF12345 JANUARY PERFORMANCE BONUS")
        assertThat(lines[1].creditPaise).isEqualTo(25000_00L)
        assertThat(lines[1].balanceAfterPaise).isEqualTo(37000_00L)
    }

    @Test
    fun testParseTextWithCrDrIndicators() {
        val iciciText = """
            15-Jan-2026 AMAZON PAY INDIA 1,200.00 DR 48,800.00
            20-Jan-2026 CASHBACK REVERSAL 50.00 CR 48,850.00
        """.trimIndent()

        val lines = StatementPdfParser.parseTextToLines(iciciText, importId = 2L)
        assertThat(lines).hasSize(2)

        assertThat(lines[0].debitPaise).isEqualTo(1200_00L)
        assertThat(lines[0].creditPaise).isEqualTo(0L)
        assertThat(lines[0].balanceAfterPaise).isEqualTo(48800_00L)

        assertThat(lines[1].debitPaise).isEqualTo(0L)
        assertThat(lines[1].creditPaise).isEqualTo(50_00L)
        assertThat(lines[1].balanceAfterPaise).isEqualTo(48850_00L)
    }

    @Test
    fun testNoiseLineFiltering() {
        val statementWithNoise = """
            Page 1 of 3
            Statement Summary
            Customer Care: 1800-123-456
            05/01/2026 GROCERIES STORE 450.00 0.00 9,550.00
            Opening Balance: 10,000.00
            Closing Balance: 9,550.00
        """.trimIndent()

        val lines = StatementPdfParser.parseTextToLines(statementWithNoise, importId = 3L)
        assertThat(lines).hasSize(1)
        assertThat(lines[0].description).isEqualTo("GROCERIES STORE")
        assertThat(lines[0].debitPaise).isEqualTo(450_00L)
    }

    @Test
    fun testPdfPasswordRequiredException() {
        val ex = PdfPasswordRequiredException()
        assertThat(ex.message).contains("password-protected")

        val customEx = PdfPasswordRequiredException("Custom password failure")
        assertThat(customEx.message).isEqualTo("Custom password failure")
    }

    @Test
    fun testParsePassbookWithEmbeddedValueDatesAndRefNumbers() {
        val passbookText = """
            Date        Narration                                                     Chq./Ref.No.      Value Dt    Withdrawal Amt.  Deposit Amt.  Closing Balance
            01 Oct 2026 SALARY INFOSPICA -SEPTEMBER 2 CA CONSULTANCY SERVICES LLP   0000130621514     01 Oct 2026                    50,000.00     1,25,000.00
            03 Oct 2026 UPI-VARGHESE 0000130621514 JOSEPH-VARGHESEJOSEPH7197@OKAXIS 130621514334     03 Oct 2026 500.00                          1,24,500.00
            03 Oct 2026 UPI-THE HIGHLAND PRODUCE-HIGHLANDPROD03MS@FBL               130623960786     03 Oct 2026 250.00                          1,24,250.00
            03 Oct 2026 UPI-KRIPARANI BENNYRAJ-9745739454@YBL                       130624567045     03 Oct 2026 300.00                          1,23,950.00
        """.trimIndent()

        val lines = StatementPdfParser.parseTextToLines(passbookText, importId = 99L)
        assertThat(lines).hasSize(4)

        // Salary credit
        assertThat(lines[0].description).contains("SALARY INFOSPICA")
        assertThat(lines[0].creditPaise).isEqualTo(50000_00L)
        assertThat(lines[0].debitPaise).isEqualTo(0L)
        assertThat(lines[0].balanceAfterPaise).isEqualTo(125000_00L)

        // UPI Varghese debit
        assertThat(lines[1].description).contains("UPI-VARGHESE")
        assertThat(lines[1].debitPaise).isEqualTo(500_00L)
        assertThat(lines[1].creditPaise).isEqualTo(0L)
        assertThat(lines[1].balanceAfterPaise).isEqualTo(124500_00L)

        // UPI The Highland debit
        assertThat(lines[2].debitPaise).isEqualTo(250_00L)
        assertThat(lines[2].balanceAfterPaise).isEqualTo(124250_00L)

        // UPI Kriparani debit
        assertThat(lines[3].debitPaise).isEqualTo(300_00L)
        assertThat(lines[3].balanceAfterPaise).isEqualTo(123950_00L)
    }

    @Test
    fun testMultiPageDisclaimersAndAddressFiltering() {
        val multiPageStatement = """
            04 Oct 2026 UPI-STATE TRANSPORT CORP-STATETRANSPORTCORP.BILLDESK@HDFCBANK-HDFCOMERUPI-130728321013-PAY 130728321013 04 Oct 2026 1,280.00 10,000.00
            04 Oct 2026 NEFT DR-IPOS0000DOP-POST HDFCH01302829581 SAVINGS-NETBANK, MUM-HDFCH01302829581-RD HDFC BANK LIMITED 04 Oct 2026 2,000.00 8,000.00
            Contents of this statement will be considered correct if no error is reported within 30 days of receipt of statement.
            The address on this statement is that on record with the Bank as at the day of requesting this statement.
            State account branch GSTN:32AAACH2702H1Z9 HDFC Bank GSTIN number details are available at https://www.hdfcbank.com/personal/making-payments/online-tax-payment/goods-and-service-tax.
            Registered Office Address: HDFC Bank House,Senapati Bapat Marg,Lower Parel,Mumbai 400013
            Account Branch : TECHNOPARK Address : PADMANABHAM, CLUB HOUSE ROAD, TECHNOPARK,KARIAVATTOM PO KAZHAKKOOTA
            MR GANESH JAYAPRAKASH City : TRIVANDRUM 695581 State : KERALA
            400 MARIYAMMAN KOIL STREET Phone no. : 18002600/18001600 ALLIKOTTAI SALEM SALEM
            OD Limit : 0.00 . Currency : INR Email : GANESHJAYAPRAKASH3@GMAIL.COM SALEM 636003
            Cust ID : 230916205 TAMIL NADU INDIA
            Account No : 50100608166362 COP POTENTIAL A/C
            Open Date : 16/05/2023 JOINT HOLDERS :
            Account Status : Regular RTGS/NEFT IFSC: HDFC0000683 MICR : 695240004 Branch Code : 683
            Nomination : Registered Account Type : INSTANT SAVING SALARY PREMIUM(161)
            From : 01/10/2026 To : 11/10/2026 Statement of Account
            Date Narration Chq./Ref.No. Value Dt Withdrawal Amt. Deposit Amt. Closing Balance
            05 Oct 2026 GROCERIES STORE 130728999999 05 Oct 2026 500.00 7,500.00
        """.trimIndent()

        val lines = StatementPdfParser.parseTextToLines(multiPageStatement, importId = 100L)
        assertThat(lines).hasSize(3)

        // Line 1: State Transport
        assertThat(lines[0].debitPaise).isEqualTo(1280_00L)

        // Line 2: NEFT DR Post
        assertThat(lines[1].description).isEqualTo("NEFT DR-IPOS0000DOP-POST HDFCH01302829581 SAVINGS-NETBANK, MUM-HDFCH01302829581-RD HDFC BANK LIMITED")
        assertThat(lines[1].debitPaise).isEqualTo(2000_00L)
        assertThat(lines[1].balanceAfterPaise).isEqualTo(8000_00L)

        // Line 3: Groceries Store on page 2
        assertThat(lines[2].description).isEqualTo("GROCERIES STORE")
        assertThat(lines[2].debitPaise).isEqualTo(500_00L)
        assertThat(lines[2].balanceAfterPaise).isEqualTo(7500_00L)
    }
}
