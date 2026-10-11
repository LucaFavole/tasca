package app.tasca

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.tasca.data.Ledger
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Once a day at 23:00: records the regular payments due today, then reminds to log the day's expenses.
 * Each run books the next one for the following 23:00, so the time never drifts.
 */
class DailyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Ledger.get(applicationContext).postDueRecurring()
        LeftWidget.refresh(applicationContext)
        if (Reminder.isOn(applicationContext)) Reminder.show(applicationContext)
        Daily.schedule(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }
}

object Daily {
    private val AT: LocalTime = LocalTime.of(23, 0)

    /** [ExistingWorkPolicy.KEEP] leaves a run that is already booked alone, so calling it on every launch is safe. */
    fun schedule(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
        val now = ZonedDateTime.now()
        val today = now.with(AT)
        val next = if (today.isAfter(now)) today else today.plusDays(1)
        val run = OneTimeWorkRequestBuilder<DailyWorker>().setInitialDelay(Duration.between(now, next)).build()
        WorkManager.getInstance(context).enqueueUniqueWork("daily", policy, run)
    }
}

object Reminder {
    const val ID = 1
    private const val CHANNEL = "reminder"
    private const val KEY = "reminder"

    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun isOn(context: Context) = prefs(context).getBoolean(KEY, true)
    fun setOn(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY, on).apply()

    fun allowed(context: Context) = android.os.Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(context: Context) {
        if (!allowed(context)) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Have you recorded all of today's expenses?")
            .setContentText("Tap to check your day")
            .setContentIntent(QuickAdd.OPEN.pending(context))
            .addAction(0, "Add expense", QuickAdd.KEYPAD.pending(context))
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(ID, n)
    }
}
