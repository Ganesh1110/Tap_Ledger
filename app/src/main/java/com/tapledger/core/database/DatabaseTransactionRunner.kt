package com.tapledger.core.database

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton

interface DatabaseTransactionRunner {
    suspend fun <R> run(block: suspend () -> R): R
}

@Singleton
class RoomDatabaseTransactionRunner @Inject constructor(
    private val database: TapLedgerDatabase
) : DatabaseTransactionRunner {
    override suspend fun <R> run(block: suspend () -> R): R = database.withTransaction(block)
}
