package com.tapledger.data.repository

import com.tapledger.core.database.dao.AccountDao
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.util.DateTimeUtils
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao
) {
    fun getAllActiveAccounts(): Flow<List<AccountEntity>> = accountDao.getAllActiveAccounts()

    suspend fun getAllAccountsSync(): List<AccountEntity> = accountDao.getAllAccountsSync()

    suspend fun getAccountById(id: Long): AccountEntity? = accountDao.getById(id)

    suspend fun findByLast4(last4: String): AccountEntity? = accountDao.findByLast4(last4)

    suspend fun createAccount(
        name: String,
        type: AccountType,
        identifierLast4: String?,
        openingBalancePaise: Long,
        openingBalanceDate: Long = DateTimeUtils.nowEpochMillis()
    ): Long {
        val now = DateTimeUtils.nowEpochMillis()
        val entity = AccountEntity(
            name = name,
            type = type,
            identifierLast4 = identifierLast4,
            openingBalancePaise = openingBalancePaise,
            openingBalanceDate = openingBalanceDate,
            isActive = true,
            createdAt = now,
            updatedAt = now
        )
        return accountDao.insert(entity)
    }

    suspend fun updateAccount(account: AccountEntity) {
        val updated = account.copy(updatedAt = DateTimeUtils.nowEpochMillis())
        accountDao.update(updated)
    }
}
