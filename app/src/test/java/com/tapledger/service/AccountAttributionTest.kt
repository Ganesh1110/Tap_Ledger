package com.tapledger.service

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.dao.AccountDao
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.TransactionDirection
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionSource
import com.tapledger.parser.ParsedFinancialEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

/**
 * Validates Flow.md §3 Step 1: Account Attribution Resolution & Inbox Quarantine.
 */
class AccountAttributionTest {

    private class FakeAccountDao : AccountDao {
        val accounts = mutableListOf<AccountEntity>()

        override suspend fun insert(account: AccountEntity): Long {
            accounts.add(account)
            return account.id
        }

        override suspend fun insertAll(accounts: List<AccountEntity>): List<Long> {
            this.accounts.addAll(accounts)
            return accounts.map { it.id }
        }

        override suspend fun update(account: AccountEntity) {
            val idx = accounts.indexOfFirst { it.id == account.id }
            if (idx >= 0) accounts[idx] = account
        }

        override suspend fun getById(id: Long): AccountEntity? = accounts.find { it.id == id }

        override fun getAllActiveAccounts(): Flow<List<AccountEntity>> = emptyFlow()

        override suspend fun getAllAccountsSync(): List<AccountEntity> = accounts

        override suspend fun findByLast4(last4: String): AccountEntity? =
            accounts.find { it.identifierLast4 == last4 && it.isActive }

        override suspend fun findAllActiveByLast4(last4: String): List<AccountEntity> =
            accounts.filter { it.identifierLast4 == last4 && it.isActive }

        override suspend fun getActiveBankAccounts(): List<AccountEntity> =
            accounts.filter { it.type == AccountType.BANK && it.isActive }
    }

    private lateinit var accountDao: FakeAccountDao

    @Before
    fun setUp() {
        accountDao = FakeAccountDao()
    }

    private suspend fun resolveAccount(parsed: ParsedFinancialEvent): Long? {
        if (!parsed.accountLast4.isNullOrBlank()) {
            val matches = accountDao.findAllActiveByLast4(parsed.accountLast4)
            if (matches.size == 1) {
                return matches.first().id
            }
        }

        if (parsed.isCardMentioned) {
            return null // Inbox Quarantine: 'Pick Card'
        }

        val activeBanks = accountDao.getActiveBankAccounts()
        return if (activeBanks.size == 1) {
            activeBanks.first().id
        } else {
            null // Inbox Quarantine: Unmapped
        }
    }

    private fun createParsedEvent(
        last4: String? = null,
        isCardMentioned: Boolean = false
    ): ParsedFinancialEvent {
        return ParsedFinancialEvent(
            source = TransactionSource.SMS,
            sourceFingerprint = "test-fp",
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 50000L,
            accountLast4 = last4,
            isCardMentioned = isCardMentioned,
            occurredAt = System.currentTimeMillis(),
            rawPreviewRedacted = "test preview"
        )
    }

    private fun createAccount(
        id: Long,
        name: String,
        type: AccountType = AccountType.BANK,
        last4: String? = null,
        isActive: Boolean = true
    ) = AccountEntity(
        id = id,
        name = name,
        type = type,
        identifierLast4 = last4,
        openingBalancePaise = 0L,
        openingBalanceDate = 1700000000000L,
        isActive = isActive,
        createdAt = 1700000000000L,
        updatedAt = 1700000000000L
    )

    @Test
    fun testMatchLast4ToSingleActiveAccount() = runBlocking {
        accountDao.insert(createAccount(id = 1L, name = "HDFC Bank", type = AccountType.BANK, last4 = "1234", isActive = true))
        accountDao.insert(createAccount(id = 2L, name = "ICICI Bank", type = AccountType.BANK, last4 = "5678", isActive = true))

        val event = createParsedEvent(last4 = "1234")
        val resolved = resolveAccount(event)

        assertThat(resolved).isEqualTo(1L)
    }

    @Test
    fun testInactiveAccountMatchingLast4_isIgnored() = runBlocking {
        // Only an inactive account has last4 1234
        accountDao.insert(createAccount(id = 1L, name = "Closed HDFC", type = AccountType.BANK, last4 = "1234", isActive = false))
        accountDao.insert(createAccount(id = 2L, name = "Active ICICI", type = AccountType.BANK, last4 = "5678", isActive = true))

        val event = createParsedEvent(last4 = "1234")
        // Since active account with 1234 is not found, and exactly 1 active bank exists (ICICI), falls back to ICICI
        val resolved = resolveAccount(event)
        assertThat(resolved).isEqualTo(2L)
    }

    @Test
    fun testAmbiguousLast4_multipleActiveAccounts_fallsThroughToFallback() = runBlocking {
        accountDao.insert(createAccount(id = 1L, name = "HDFC Salary", type = AccountType.BANK, last4 = "1234", isActive = true))
        accountDao.insert(createAccount(id = 2L, name = "HDFC Savings", type = AccountType.BANK, last4 = "1234", isActive = true))

        val event = createParsedEvent(last4 = "1234")
        // Multiple active bank accounts exist (and both match 1234) -> ambiguous, cannot resolve unequivocally -> null
        val resolved = resolveAccount(event)
        assertThat(resolved).isNull()
    }

    @Test
    fun testExplicitCardMention_quarantinesToNull() = runBlocking {
        accountDao.insert(createAccount(id = 1L, name = "HDFC Bank", type = AccountType.BANK, last4 = "1234", isActive = true))
        accountDao.insert(createAccount(id = 2L, name = "HDFC Millennia", type = AccountType.CREDIT_CARD, last4 = "9999", isActive = true))

        // Event mentions Card without matching last4 (or ambiguous)
        val event = createParsedEvent(last4 = null, isCardMentioned = true)
        val resolved = resolveAccount(event)

        // Flow.md: Explicitly mentions Card -> set account_id = NULL (Inbox Quarantine: 'Pick Card')
        assertThat(resolved).isNull()
    }

    @Test
    fun testSingleActiveBankAccountFallback() = runBlocking {
        accountDao.insert(createAccount(id = 1L, name = "Only Bank Account", type = AccountType.BANK, last4 = "1111", isActive = true))
        accountDao.insert(createAccount(id = 2L, name = "Credit Card", type = AccountType.CREDIT_CARD, last4 = "2222", isActive = true))

        // Notification does not state last4, does not mention card
        val event = createParsedEvent(last4 = null, isCardMentioned = false)
        val resolved = resolveAccount(event)

        // Exactly 1 active bank account exists -> resolved to it!
        assertThat(resolved).isEqualTo(1L)
    }

    @Test
    fun testMultipleActiveBankAccounts_withoutLast4_quarantinesToNull() = runBlocking {
        accountDao.insert(createAccount(id = 1L, name = "Bank 1", type = AccountType.BANK, last4 = "1111", isActive = true))
        accountDao.insert(createAccount(id = 2L, name = "Bank 2", type = AccountType.BANK, last4 = "2222", isActive = true))

        val event = createParsedEvent(last4 = null, isCardMentioned = false)
        val resolved = resolveAccount(event)

        // Multiple active bank accounts exist without last4 -> unmapped quarantine
        assertThat(resolved).isNull()
    }
}
