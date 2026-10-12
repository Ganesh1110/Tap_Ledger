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
}
