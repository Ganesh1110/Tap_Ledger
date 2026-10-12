package com.tapledger.data.statement

import com.tapledger.core.database.entity.StatementLineEntity

/**
 * Universal interface for bank statement parsers conforming to IMPLEMENTATION_PLAN.md §24.
 * Enables interchangeable parsing of CSV, PDF, and future statement formats.
 */
interface StatementParser {
    fun parse(
        content: String,
        importId: Long,
        createdAt: Long
    ): List<StatementLineEntity>
}
