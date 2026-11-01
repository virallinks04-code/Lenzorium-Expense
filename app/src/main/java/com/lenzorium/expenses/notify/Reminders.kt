package com.lenzorium.expenses.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lenzorium.expenses.LenzoriumApp
import com.lenzorium.expenses.MainActivity
import com.lenzorium.expenses.R
import com.lenzorium.expenses.data.Category
import com.lenzorium.expenses.domain.BudgetLevel
import com.lenzorium.expenses.domain.BudgetStatus
import com.lenzorium.expenses.domain.PlanMath
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.concurrent.TimeUnit

/**
 * Local notifications only. Nothing leaves the phone. Notifications never show amounts, and on the
 * lock screen they show only a generic line ("You have a bill due"), so balances stay private.
 */
object Notifier {
    const val CH_BILLS = "bills"
    const val CH_BUDGETS = "budgets"
    const val EXTRA_ROUTE = "open_route"
    private const val ID_BILLS = 1001
    private const val ID_BUDGET_BASE = 2000

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_BILLS, "Bill reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Reminds you when a bill, subscription or regular income is due."
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        })
        nm.createNotificationChannel(NotificationChannel(CH_BUDGETS, "Budget warnings", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Tells you when a monthly budget reaches 80% or is used up."
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        })
    }

    private fun open(ctx: Context, route: String, code: Int): PendingIntent =
        PendingIntent.getActivity(
            ctx, code,
            Intent(ctx, MainActivity::class.java).putExtra(EXTRA_ROUTE, route).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun post(ctx: Context, id: Int, channel: String, title: String, text: String, publicText: String, route: String) {
        if (!canNotify(ctx)) return
        val public = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Lenzorium Expenses")
            .setContentText(publicText)
            .build()
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF4A9EDB.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setContentIntent(open(ctx, route, id))
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(id, n) } catch (_: SecurityException) { }
    }

    fun billsDue(ctx: Context, names: List<String>, overdue: Int) {
        if (names.isEmpty()) return
        val title = if (names.size == 1) "${names[0]} is due" else "${names.size} bills are due"
        val text = buildString {
            append(names.take(4).joinToString(", "))
            if (names.size > 4) append(" and ${names.size - 4} more")
            append(". Open the app to mark them paid or skip.")
            if (overdue > 0) append(" $overdue overdue.")
        }
        post(ctx, ID_BILLS, CH_BILLS, title, text, "You have a bill due", "bills")
    }

    /** Posts a warning the first time a budget crosses 80% and again at 100% each month. */
    fun budgetCrossings(ctx: Context, statuses: List<BudgetStatus>, categories: Map<Long, Category>) {
        val store = (ctx.applicationContext as LenzoriumApp).repository.settings
        if (!store.current.budgetAlerts || !canNotify(ctx)) return
        val month = YearMonth.now().toString()
        for (s in statuses) {
            val step = when (s.level) { BudgetLevel.OVER -> 100; BudgetLevel.NEAR -> 80; BudgetLevel.OK -> continue }
            if (s.available <= 0) continue
            val key = "$month:${s.budget.id}:$step"
            if (store.alreadyAlerted(key)) continue
            store.markAlerted(key)
            val name = s.budget.categoryId?.let { categories[it]?.name } ?: "Monthly"
            val (title, text) = if (step == 100) "$name budget used up" to "You've spent more than your $name budget this month."
            else "$name budget at ${(s.fraction * 100).toInt()}%" to "You've used most of your $name budget this month."
            post(ctx, ID_BUDGET_BASE + s.budget.id.toInt(), CH_BUDGETS, title, text, "A budget needs attention", "budgets")
        }
    }

    /** Runs the daily check once a day around 9am. Safe to call on every app start. */
    fun schedule(ctx: Context) {
        val now = LocalDateTime.now()
        var at = now.toLocalDate().atTime(9, 0)
        if (!at.isAfter(now)) at = at.plusDays(1)
        val req = PeriodicWorkRequestBuilder<DailyCheckWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, at).toMinutes(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("daily-check", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

class DailyCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as LenzoriumApp).repository
        val s = repo.settings.current
        if (!s.setupDone) return Result.success()
        val today = LocalDate.now().toEpochDay()
        if (s.remindersEnabled) {
            val due = PlanMath.dueForReminder(repo.recurring.first(), today)
            Notifier.billsDue(applicationContext, due.map { it.name }, due.count { it.nextDueEpochDay < today })
        }
        val txns = repo.txns.first()
        val statuses = repo.budgets.first().map { PlanMath.status(it, txns, YearMonth.now()) }
        Notifier.budgetCrossings(applicationContext, statuses, repo.categories.first().associateBy { it.id })
        return Result.success()
    }
}
