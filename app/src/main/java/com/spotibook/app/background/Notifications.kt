package com.spotibook.app.background

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.spotibook.app.*
import com.spotibook.app.R
import com.spotibook.app.data.Settings
import kotlinx.coroutines.flow.first
import java.time.*
import java.util.concurrent.TimeUnit

object Notifications {
    fun channels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("reading", "Pengingat membaca", NotificationManager.IMPORTANCE_DEFAULT))
        manager.createNotificationChannel(NotificationChannel("focus_complete", "Sesi fokus selesai", NotificationManager.IMPORTANCE_HIGH).apply { setSound(null,null);enableVibration(false) })
    }
    fun allowed(context:Context):Boolean = (Build.VERSION.SDK_INT<33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) && context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    fun show(context: Context, id: Int, channel: String, title: String, body: String, destination: String = "home") {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val intent = Intent(context, MainActivity::class.java).putExtra("destination", destination).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        context.getSystemService(NotificationManager::class.java).notify(id, NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body)).setContentIntent(pending).setAutoCancel(true).build())
    }
}
object Reminders {
    fun schedule(context: Context, settings: Settings) {
        val work = WorkManager.getInstance(context)
        if (!settings.reminder && !settings.streakReminder) { work.cancelUniqueWork("reading-reminder"); return }
        val now = ZonedDateTime.now(); var next = now.withHour(settings.reminderHour).withMinute(settings.reminderMinute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        work.enqueueUniqueWork("reading-reminder", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS).build())
    }
}
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as SpotibookApp).repository; val s = repo.preferences.settings.first()
        val today = LocalDate.now().toString(); val read = repo.dao.allSessions().filter { it.date == today }.sumOf { it.millis }
        if ((s.reminder && read < s.dailyMinutes * 60_000L) || (s.streakReminder && read < s.qualifyingMinutes * 60_000L)) {
            Notifications.show(applicationContext, 100, "reading", "Pengingat membaca", "Target membaca hari ini ${s.dailyMinutes} menit. Buka buku untuk melanjutkan.")
        }
        // APPEND avoids replacing/cancelling the currently executing worker.
        if (s.reminder || s.streakReminder) {
            val now = ZonedDateTime.now(); var next = now.withHour(s.reminderHour).withMinute(s.reminderMinute).withSecond(0).withNano(0)
            if (!next.isAfter(now.plusMinutes(1))) next = next.plusDays(1)
            WorkManager.getInstance(applicationContext).enqueueUniqueWork("reading-reminder", ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS).build())
        }
        Widgets.update(applicationContext)
        return Result.success()
    }
}
