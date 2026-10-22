package com.tapledger.di

import android.content.Context
import androidx.room.Room
import com.tapledger.core.database.TapLedgerDatabase
import com.tapledger.core.database.dao.AccountDao
import com.tapledger.core.database.dao.AdvanceLedgerDao
import com.tapledger.core.database.dao.AuditLogDao
import com.tapledger.core.database.dao.BudgetAdjustmentDao
import com.tapledger.core.database.dao.BudgetAllocationDao
import com.tapledger.core.database.dao.BudgetPeriodDao
import com.tapledger.core.database.dao.CategoryDao
import com.tapledger.core.database.dao.MerchantMapDao
import com.tapledger.core.database.dao.MonthCloseDao
import com.tapledger.core.database.dao.StatementImportDao
import com.tapledger.core.database.dao.StatementLineDao
import com.tapledger.core.database.dao.TransactionAllocationDao
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.core.database.dao.UnparsedAlertDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TapLedgerDatabase {
        return Room.databaseBuilder(
            context,
            TapLedgerDatabase::class.java,
            TapLedgerDatabase.DATABASE_NAME
        )
            .addMigrations(
                com.tapledger.core.database.MIGRATION_1_2,
                com.tapledger.core.database.MIGRATION_2_3,
                com.tapledger.core.database.MIGRATION_3_4,
                com.tapledger.core.database.MIGRATION_4_5
            )
            .addCallback(com.tapledger.core.database.DatabaseTriggers.Callback)
            .build()
    }


    @Provides
    fun provideAccountDao(db: TapLedgerDatabase): AccountDao = db.accountDao()

    @Provides
    fun provideCategoryDao(db: TapLedgerDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideBudgetPeriodDao(db: TapLedgerDatabase): BudgetPeriodDao = db.budgetPeriodDao()

    @Provides
    fun provideBudgetAllocationDao(db: TapLedgerDatabase): BudgetAllocationDao = db.budgetAllocationDao()

    @Provides
    fun provideBudgetAdjustmentDao(db: TapLedgerDatabase): BudgetAdjustmentDao = db.budgetAdjustmentDao()

    @Provides
    fun provideTransactionDao(db: TapLedgerDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideTransactionAllocationDao(db: TapLedgerDatabase): TransactionAllocationDao = db.transactionAllocationDao()

    @Provides
    fun provideAdvanceLedgerDao(db: TapLedgerDatabase): AdvanceLedgerDao = db.advanceLedgerDao()

    @Provides
    fun provideMerchantMapDao(db: TapLedgerDatabase): MerchantMapDao = db.merchantMapDao()

    @Provides
    fun provideUnparsedAlertDao(db: TapLedgerDatabase): UnparsedAlertDao = db.unparsedAlertDao()

    @Provides
    fun provideStatementImportDao(db: TapLedgerDatabase): StatementImportDao = db.statementImportDao()

    @Provides
    fun provideStatementLineDao(db: TapLedgerDatabase): StatementLineDao = db.statementLineDao()

    @Provides
    fun provideMonthCloseDao(db: TapLedgerDatabase): MonthCloseDao = db.monthCloseDao()

    @Provides
    fun provideAuditLogDao(db: TapLedgerDatabase): AuditLogDao = db.auditLogDao()

    @Provides
    fun provideSavingsGoalDao(db: TapLedgerDatabase): com.tapledger.core.database.dao.SavingsGoalDao = db.savingsGoalDao()

    @Provides
    fun provideSavingsContributionDao(db: TapLedgerDatabase): com.tapledger.core.database.dao.SavingsContributionDao = db.savingsContributionDao()

    @Provides
    fun provideSavingsPlanEntryDao(db: TapLedgerDatabase): com.tapledger.core.database.dao.SavingsPlanEntryDao = db.savingsPlanEntryDao()

    @Provides
    fun provideRecurringCommitmentDao(db: TapLedgerDatabase): com.tapledger.core.database.dao.RecurringCommitmentDao = db.recurringCommitmentDao()

    @Provides
    fun provideCommitmentPaymentDao(db: TapLedgerDatabase): com.tapledger.core.database.dao.CommitmentPaymentDao = db.commitmentPaymentDao()

    @Provides
    fun provideFixedDepositDao(db: TapLedgerDatabase): com.tapledger.core.database.dao.FixedDepositDao = db.fixedDepositDao()

    @Provides
    fun provideSavingsTransferPlanDao(db: TapLedgerDatabase): com.tapledger.core.database.dao.SavingsTransferPlanDao = db.savingsTransferPlanDao()

    @Provides
    @Singleton
    fun provideDatabaseTransactionRunner(runner: com.tapledger.core.database.RoomDatabaseTransactionRunner): com.tapledger.core.database.DatabaseTransactionRunner = runner
}

