package com.tapledger.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tapledger.core.database.dao.AccountDao
import com.tapledger.core.database.dao.LoanDao
import com.tapledger.core.database.dao.RecurringCommitmentDao
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.preferences.LoanPreferences
import com.tapledger.domain.calculator.AccountBalanceCalculator
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Offline WorkManager worker that posts local reminders a day before the due date (Phase 3).
 * Runs daily without requiring network connectivity.
 */
class CommitmentReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CommitmentWorkerEntryPoint {
        fun recurringCommitmentDao(): RecurringCommitmentDao
        fun loanDao(): LoanDao
        fun accountDao(): AccountDao
        fun transactionDao(): TransactionDao
        fun loanPreferences(): LoanPreferences
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            CommitmentWorkerEntryPoint::class.java
        )
        val commitmentDao = entryPoint.recurringCommitmentDao()
        val loanDao = entryPoint.loanDao()
        val accountDao = entryPoint.accountDao()
        val transactionDao = entryPoint.transactionDao()
        val loanPreferences = entryPoint.loanPreferences()

        val loanConfig = loanPreferences.getConfig()
        val isLoanTrackerEnabled = loanConfig.loanTrackerEnabled
        val emiGuardEnabled = loanConfig.emiBalanceGuardEnabled
        val daysPrior = loanConfig.emiBalanceGuardDaysPrior.coerceIn(1, 7)

        val tomorrowDay = LocalDate.now().plusDays(1).dayOfMonth
        val guardTargetDay = LocalDate.now().plusDays(daysPrior.toLong()).dayOfMonth

        val activeCommitments = commitmentDao.getAllActiveSync()
        val dueTomorrow = activeCommitments.filter { it.dueDay == tomorrowDay }

        val activeLoans = if (isLoanTrackerEnabled) loanDao.getAllActiveSync() else emptyList()
        val targetDayForLoan = if (emiGuardEnabled) guardTargetDay else tomorrowDay
        val loansDue = activeLoans.filter { it.emiDay == targetDayForLoan && it.isActive && it.principalPaise > 0L }

        if (dueTomorrow.isNotEmpty() || loansDue.isNotEmpty()) {
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = CHANNEL_ID

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Commitment & EMI Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Reminders for upcoming SIP, RD, loan EMIs, and recurring commitments"
                }
                notificationManager.createNotificationChannel(channel)
            }

            for (commitment in dueTomorrow) {
                val formattedAmount = MoneyFormatter.formatPaiseToRupees(commitment.amountPaise)
                val notification = NotificationCompat.Builder(applicationContext, channelId)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Upcoming ${commitment.type} Reminder")
                    .setContentText("${commitment.name}: $formattedAmount is due tomorrow")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .build()

                notificationManager.notify(
                    commitment.id.toInt() + NOTIFICATION_ID_BASE,
                    notification
                )
            }

            for (loan in loansDue) {
                val formattedEmi = MoneyFormatter.formatPaiseToRupees(loan.emiAmountPaise)
                val bankAccount = loan.linkedBankAccountId?.let { accountDao.getById(it) }

                val (title, content) = if (emiGuardEnabled && bankAccount != null) {
                    val txs = transactionDao.getBalanceTransactionsForAccount(bankAccount.id, 0L)
                    val balanceSummary = AccountBalanceCalculator.calculateAccountBalance(bankAccount, txs)
                    val balancePaise = balanceSummary.currentBalancePaise

                    if (balancePaise >= loan.emiAmountPaise) {
                        "EMI Balance Guard: ${loan.name}" to
                                "Your $formattedEmi EMI is due in $daysPrior day${if (daysPrior > 1) "s" else ""}. Current balance in ${bankAccount.name}: ${MoneyFormatter.formatPaiseToRupees(balancePaise)} (Sufficient funds)"
                    } else {
                        val shortfallPaise = loan.emiAmountPaise - balancePaise
                        "⚠️ Low Balance Alert: ${loan.name} EMI" to
                                "Your $formattedEmi EMI is due in $daysPrior day${if (daysPrior > 1) "s" else ""}, but balance in ${bankAccount.name} is only ${MoneyFormatter.formatPaiseToRupees(balancePaise)}. Add ${MoneyFormatter.formatPaiseToRupees(shortfallPaise)} to prevent NACH bounce fees."
                    }
                } else {
                    val dayText = if (daysPrior == 1 || !emiGuardEnabled) "tomorrow" else "in $daysPrior days"
                    "Upcoming Loan EMI Reminder" to "${loan.name}: $formattedEmi EMI is due $dayText"
                }

                val notification = NotificationCompat.Builder(applicationContext, channelId)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(content)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .build()

                notificationManager.notify(
                    loan.id.toInt() + LOAN_NOTIFICATION_ID_BASE,
                    notification
                )
            }
        }

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "commitment_due_reminders"
        const val CHANNEL_ID = "tapledger_commitment_reminders"
        private const val NOTIFICATION_ID_BASE = 50000
        private const val LOAN_NOTIFICATION_ID_BASE = 60000

        fun enqueueDailyCheck(context: Context) {
            val constraints = Constraints.Builder().build()
            val request = PeriodicWorkRequestBuilder<CommitmentReminderWorker>(
                24, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
