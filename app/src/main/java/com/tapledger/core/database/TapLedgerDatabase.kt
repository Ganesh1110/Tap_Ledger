package com.tapledger.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tapledger.core.database.converter.Converters
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
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.AdvanceLedgerEntity
import com.tapledger.core.database.entity.AuditLogEntity
import com.tapledger.core.database.entity.BudgetAdjustmentEntity
import com.tapledger.core.database.entity.BudgetAllocationEntity
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.database.entity.MerchantMapEntity
import com.tapledger.core.database.entity.MonthCloseEntity
import com.tapledger.core.database.entity.StatementImportEntity
import com.tapledger.core.database.entity.StatementLineEntity
import com.tapledger.core.database.entity.TransactionAllocationEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.database.entity.UnparsedAlertEntity

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        BudgetPeriodEntity::class,
        BudgetAllocationEntity::class,
        BudgetAdjustmentEntity::class,
        TransactionEntity::class,
        TransactionAllocationEntity::class,
        AdvanceLedgerEntity::class,
        MerchantMapEntity::class,
        UnparsedAlertEntity::class,
        StatementImportEntity::class,
        StatementLineEntity::class,
        MonthCloseEntity::class,
        AuditLogEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class TapLedgerDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetPeriodDao(): BudgetPeriodDao
    abstract fun budgetAllocationDao(): BudgetAllocationDao
    abstract fun budgetAdjustmentDao(): BudgetAdjustmentDao
    abstract fun transactionDao(): TransactionDao
    abstract fun transactionAllocationDao(): TransactionAllocationDao
    abstract fun advanceLedgerDao(): AdvanceLedgerDao
    abstract fun merchantMapDao(): MerchantMapDao
    abstract fun unparsedAlertDao(): UnparsedAlertDao
    abstract fun statementImportDao(): StatementImportDao
    abstract fun statementLineDao(): StatementLineDao
    abstract fun monthCloseDao(): MonthCloseDao
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        const val DATABASE_NAME = "tapledger.db"
    }
}
