package com.tapledger.data.export

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.database.entity.TransactionAllocationEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.TransactionDirection
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionSource
import com.tapledger.core.model.TransactionStatus
import org.junit.Test

class CsvExporterTest {

    @Test
    fun testExportTransactionsToCsv_formatsCorrectlyWithEscaping() {
        val account = AccountEntity(
            id = 1L,
            name = "HDFC, Salary A/C",
            type = AccountType.BANK,
            identifierLast4 = "1234",
            openingBalancePaise = 0L,
            openingBalanceDate = 1700000000000L,
            isActive = true,
            createdAt = 1700000000000L,
            updatedAt = 1700000000000L
        )
        val category = CategoryEntity(
            id = 10L,
            name = "Dining & \"Food\"",
            defaultBucket = BudgetBucket.WANTS,
            defaultSortOrder = 1
        )
        val tx = TransactionEntity(
            id = 101L,
            accountId = 1L,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 250_50L,
            merchantRaw = "Cafe, Coffee Day",
            upiReference = "UPI12345678",
            occurredAt = 1700000000000L,
            createdAt = 1700000000000L,
            updatedAt = 1700000000000L,
            status = TransactionStatus.CATEGORIZED,
            source = TransactionSource.MANUAL,
            notes = "Met Alice & Bob"
        )
        val alloc = TransactionAllocationEntity(
            id = 501L,
            transactionId = 101L,
            categoryId = 10L,
            amountPaise = 250_50L
        )

        val csv = CsvExporter.exportTransactionsToCsv(
            transactions = listOf(tx),
            accountsMap = mapOf(1L to account),
            allocationsMap = mapOf(101L to listOf(alloc)),
            categoriesMap = mapOf(10L to category)
        )

        val lines = csv.trim().split("\n")
        assertThat(lines).hasSize(2)
        assertThat(lines[0]).isEqualTo("Transaction ID,Date (IST),Account,Type,Direction,Amount (INR),Category,Merchant,UPI Reference,Status,Notes")
        // Check quotes around fields with commas and double quotes
        assertThat(lines[1]).contains("\"HDFC, Salary A/C\"")
        assertThat(lines[1]).contains("250.50")
        assertThat(lines[1]).contains("\"Dining & \"\"Food\"\"\"")
        assertThat(lines[1]).contains("\"Cafe, Coffee Day\"")
        assertThat(lines[1]).contains("UPI12345678")
        assertThat(lines[1]).contains("CATEGORIZED")
    }
}
