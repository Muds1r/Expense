package com.expense.tracker.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.expense.tracker.data.SettingsStore
import com.expense.tracker.data.db.AppDatabase
import com.expense.tracker.data.db.TxnType
import kotlinx.coroutines.flow.first
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

/**
 * After each successful sync, compare this calendar month's spending per
 * category against its budget and notify at 80% and 100%.
 */
object BudgetAlerts {

    private const val CHANNEL_ID = "budget"
    private const val BASE_NOTIFICATION_ID = 2000

    suspend fun checkAndNotify(context: Context) {
        val dao = AppDatabase.get(context).transactionDao()
        val settings = SettingsStore(context)
        val categories = dao.allCategories().first().filter {
            val b = it.budgetAmount
            b != null && b > 0
        }
        if (categories.isEmpty()) return

        val (monthStart, monthEnd) = currentMonthBounds()
        val periodKey = periodKey(monthStart)

        ensureChannel(context)

        for (category in categories) {
            val budget = category.budgetAmount ?: continue
            val txns = dao.transactionsForCategory(category.id, monthStart, monthEnd).first()
            val spent = txns.filter { it.type == TxnType.DEBIT }.sumOf { it.amount }
            if (spent <= 0) continue

            val ratio = spent / budget
            val previous = settings.budgetAlertLevel(category.id, periodKey)
            val level = when {
                ratio >= 1.0 -> 100
                ratio >= 0.8 -> 80
                else -> continue
            }
            if (level <= previous) continue

            settings.setBudgetAlertLevel(category.id, periodKey, level)
            val title = if (level >= 100) {
                "${category.name}: budget reached"
            } else {
                "${category.name}: 80% of budget used"
            }
            val body = if (level >= 100) {
                "Spent ${money(spent)} of ${money(budget)} this month."
            } else {
                "Spent ${money(spent)} of ${money(budget)} — ${((ratio) * 100).toInt()}% used."
            }
            post(context, BASE_NOTIFICATION_ID + category.id.toInt(), title, body)
        }
    }

    private fun currentMonthBounds(): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = Calendar.getInstance().apply {
            timeInMillis = start.timeInMillis
            add(Calendar.MONTH, 1)
            add(Calendar.MILLISECOND, -1)
        }
        return start.timeInMillis to end.timeInMillis
    }

    private fun periodKey(monthStart: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = monthStart }
        return "${c.get(Calendar.YEAR)}-${c.get(Calendar.MONTH) + 1}"
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Budget alerts", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun post(context: Context, id: Int, title: String, body: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission denied on Android 13+ — sync still succeeded.
        }
    }

    private fun money(amount: Double): String =
        NumberFormat.getCurrencyInstance(Locale("en", "PK")).format(amount)
}
