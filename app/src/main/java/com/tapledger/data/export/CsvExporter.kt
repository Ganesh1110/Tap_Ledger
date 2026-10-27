package com.tapledger.data.export

import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.database.entity.TransactionAllocationEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.util.DateTimeUtils

object CsvExporter {

    fun exportTransactionsToCsv(
        transactions: List<TransactionEntity>,
        accountsMap: Map<Long, AccountEntity>,
        allocationsMap: Map<Long, List<TransactionAllocationEntity>>,
        categoriesMap: Map<Long, CategoryEntity>
    ): String {
        val sb = StringBuilder()
        sb.append("Transaction ID,Date (IST),Account,Type,Direction,Amount (INR),Category,Merchant,UPI Reference,Status,Notes\n")

        for (tx in transactions.sortedByDescending { it.occurredAt }) {
            val accountName = tx.accountId?.let { accountsMap[it]?.name } ?: "Unassigned"
            val dateStr = DateTimeUtils.formatDateTime(tx.occurredAt)
            val wholeRupees = tx.amountPaise / 100L
            val paiseFraction = kotlin.math.abs(tx.amountPaise % 100L)
            val amountStr = "$wholeRupees.${if (paiseFraction < 10L) "0$paiseFraction" else "$paiseFraction"}"

            val txAllocs = allocationsMap[tx.id] ?: emptyList()
            val categoryNames = if (txAllocs.isNotEmpty()) {
                txAllocs.mapNotNull { categoriesMap[it.categoryId]?.name }.joinToString("; ")
            } else {
                ""
            }

            val fields = listOf(
                tx.id.toString(),
                escapeCsv(dateStr),
                escapeCsv(accountName),
                escapeCsv(tx.kind.name),
                escapeCsv(tx.direction.name),
                amountStr,
                escapeCsv(categoryNames),
                escapeCsv(tx.merchantRaw ?: ""),
                escapeCsv(tx.upiReference ?: ""),
                escapeCsv(tx.status.name),
                escapeCsv(tx.notes ?: "")
            )
            sb.append(fields.joinToString(",")).append("\n")
        }
        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        // Guard against CSV / spreadsheet formula injection (CWE-1236)
        var sanitized = value
        if (sanitized.isNotEmpty()) {
            val firstChar = sanitized[0]
            if (firstChar == '=' || firstChar == '+' || firstChar == '-' || firstChar == '@' || firstChar == '\t' || firstChar == '\r') {
                sanitized = "'$sanitized"
            }
        }
        return if (sanitized.contains(',') || sanitized.contains('"') || sanitized.contains('\n') || sanitized.contains('\r')) {
            "\"" + sanitized.replace("\"", "\"\"") + "\""
        } else {
            sanitized
        }
    }
}
