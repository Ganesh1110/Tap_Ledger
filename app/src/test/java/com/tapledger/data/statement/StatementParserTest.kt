package com.tapledger.data.statement

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.model.StatementMatchStatus
import com.tapledger.core.util.DateTimeUtils
import org.junit.Test
import java.time.LocalDate

class StatementParserTest {

    @Test
    fun testDialectNormalization() {
        // 1. HDFC Bank CSV Dialect
        val hdfcCsv = """
            HDFC BANK STATEMENT FOR ACCOUNT 1234567890
            Generated on 01/02/2024
            Date,Narration,Chq./Ref.No.,Value Dt,Withdrawal Amt.,Deposit Amt.,Closing Balance
            01/01/2024,UPI-SWIGGY-123456,123456789012,01/01/2024,450.00,,9550.00
            05/01/2024,SALARY CREDITED,SAL123,05/01/2024,,50000.00,59550.00
        """.trimIndent()

        val hdfcLines = StatementCsvParser.parseCsv(hdfcCsv, importId = 1)
        assertThat(hdfcLines).hasSize(2)
        assertThat(hdfcLines[0].description).isEqualTo("UPI-SWIGGY-123456")
        assertThat(hdfcLines[0].reference).isEqualTo("123456789012")
        assertThat(hdfcLines[0].debitPaise).isEqualTo(450_00L)
        assertThat(hdfcLines[0].creditPaise).isEqualTo(0L)
        assertThat(hdfcLines[0].balanceAfterPaise).isEqualTo(9550_00L)

        assertThat(hdfcLines[1].description).isEqualTo("SALARY CREDITED")
        assertThat(hdfcLines[1].reference).isEqualTo("SAL123")
        assertThat(hdfcLines[1].debitPaise).isEqualTo(0L)
        assertThat(hdfcLines[1].creditPaise).isEqualTo(50_000_00L)
        assertThat(hdfcLines[1].balanceAfterPaise).isEqualTo(59550_00L)

        // 2. SBI (State Bank of India) Dialect
        val sbiCsv = """
            Txn Date,Value Date,Description,Ref No./Cheque No.,Debit,Credit,Balance
            02-Jan-2024,02-Jan-2024,TRANSFER TO RAM,REF999,"1,234.50",,"48,765.50"
            10-Jan-2024,10-Jan-2024,INTEREST CREDIT,INT11,,250.00,49,015.50
        """.trimIndent()

        val sbiLines = StatementCsvParser.parseCsv(sbiCsv, importId = 2)
        assertThat(sbiLines).hasSize(2)
        assertThat(sbiLines[0].description).isEqualTo("TRANSFER TO RAM")
        assertThat(sbiLines[0].reference).isEqualTo("REF999")
        assertThat(sbiLines[0].debitPaise).isEqualTo(1234_50L)
        assertThat(sbiLines[0].creditPaise).isEqualTo(0L)
        assertThat(sbiLines[0].balanceAfterPaise).isEqualTo(48765_50L)

        assertThat(sbiLines[1].creditPaise).isEqualTo(250_00L)

        // 3. ICICI Bank Dialect (with Cr/Dr type column)
        val iciciCsv = """
            Transaction Date,Value Date,Description,Cheque Number,Cr/Dr,Transaction Amount(INR),Available Balance(INR)
            15/01/2024,15/01/2024,AMAZON PAY,987654,DR,"1,200.00","47,800.00"
            20/01/2024,20/01/2024,CASHBACK,CB11,CR,50.00,47850.00
        """.trimIndent()

        val iciciLines = StatementCsvParser.parseCsv(iciciCsv, importId = 3)
        assertThat(iciciLines).hasSize(2)
        assertThat(iciciLines[0].description).isEqualTo("AMAZON PAY")
        assertThat(iciciLines[0].reference).isEqualTo("987654")
        assertThat(iciciLines[0].debitPaise).isEqualTo(1200_00L)
        assertThat(iciciLines[0].creditPaise).isEqualTo(0L)
        assertThat(iciciLines[0].balanceAfterPaise).isEqualTo(47800_00L)

        assertThat(iciciLines[1].debitPaise).isEqualTo(0L)
        assertThat(iciciLines[1].creditPaise).isEqualTo(50_00L)

        // 4. Axis Bank Dialect
        val axisCsv = """
            Tran Date,CHQNO,PARTICULAR,DR,CR,BAL
            10-01-2024,AX11,RESTAURANT,500.00,,"47,300.00"
            12-01-2024,AX12,REFUND,,100.00,47400.00
        """.trimIndent()

        val axisLines = StatementCsvParser.parseCsv(axisCsv, importId = 4)
        assertThat(axisLines).hasSize(2)
        assertThat(axisLines[0].description).isEqualTo("RESTAURANT")
        assertThat(axisLines[0].reference).isEqualTo("AX11")
        assertThat(axisLines[0].debitPaise).isEqualTo(500_00L)
        assertThat(axisLines[1].creditPaise).isEqualTo(100_00L)

        // 5. Zero-Float Paise Parsing with Indian Grouping
        assertThat(StatementCsvParser.parseAmount("12,34,567.89")).isEqualTo(1234567_89L)
        assertThat(StatementCsvParser.parseAmount("Rs. 500/-")).isEqualTo(500_00L)
        assertThat(StatementCsvParser.parseAmount("INR 10.50")).isEqualTo(10_50L)
        assertThat(StatementCsvParser.parseAmount("-50.00")).isEqualTo(-50_00L)
        assertThat(StatementCsvParser.parseAmount("(100.00)")).isEqualTo(-100_00L)
        assertThat(StatementCsvParser.parseAmount("0.00")).isEqualTo(0L)
        assertThat(StatementCsvParser.parseAmount("-")).isEqualTo(0L)
        assertThat(StatementCsvParser.parseAmount("nil")).isEqualTo(0L)
    }
}
