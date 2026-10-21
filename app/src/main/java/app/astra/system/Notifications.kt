// Daily notification job: runs once a day at the user's chosen time, works out what to say
// with the core NotifyPlanner (caps, quiet hours, no fear words) and posts it. No server needed.
package app.astra.system

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
import app.astra.App
import app.astra.MainActivity
import app.astra.R
import app.astra.core.Graha
import app.astra.core.NotifyKind
import app.astra.core.NotifyPlanner
import app.astra.core.PlannedNotice
import app.astra.ui.Session
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object Notifications {
    const val CH_DAILY = "astra_daily"
    const val CH_EVENTS = "astra_events"
    const val CH_CHECKINS = "astra_checkins"
    const val EXTRA_TAB = "open_tab"
    const val EXTRA_LOOP = "push_loop"
    private const val WORK = "astra_daily_note"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_DAILY, "Daily note and week ahead", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A short morning note: tithi, your Moon and Rahu Kaal. On Sundays, your week ahead."
        })
        nm.createNotificationChannel(NotificationChannel(CH_EVENTS, "Changes in your chart", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A planet changing sign for you, or a new dasha period. At most two a week."
        })
        nm.createNotificationChannel(NotificationChannel(CH_CHECKINS, "Story check-ins", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A gentle follow-up on an open question in My Story. At most one a day."
        })
    }

    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** (Re)schedules the daily job for the user's time; cancels it when notifications are off. */
    fun schedule(context: Context) {
        val app = context.applicationContext as App
        val wm = WorkManager.getInstance(context)
        if (app.notify.enabled != true) { wm.cancelUniqueWork(WORK); return }
        val zone = app.profiles.profile?.currentPlace?.zoneId?.let { ZoneId.of(it) } ?: ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        var next = now.toLocalDate().atTime(NotifyPlanner.fireTime(app.notify.prefs))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMinutes().coerceAtLeast(1)
        val req = PeriodicWorkRequestBuilder<DailyNoteWorker>(24, TimeUnit.HOURS, 30, TimeUnit.MINUTES)
            .setInitialDelay(delay, TimeUnit.MINUTES).build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun post(context: Context, n: PlannedNotice, id: Int) {
        if (!canPost(context)) return
        val tab = if (n.kind == NotifyKind.DAILY || n.kind == NotifyKind.CHECKIN) "TODAY" else "TRACKER"
        val loop = when (n.kind) { NotifyKind.DAILY -> "daily"; NotifyKind.WEEKLY -> "weekly"; NotifyKind.EVENT -> "event"; NotifyKind.CHECKIN -> "checkin" }
        val intent = Intent(context, MainActivity::class.java).putExtra(EXTRA_TAB, tab).putExtra(EXTRA_LOOP, loop)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val channel = when (n.kind) { NotifyKind.EVENT -> CH_EVENTS; NotifyKind.CHECKIN -> CH_CHECKINS; else -> CH_DAILY }
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_astra)
            .setContentTitle(n.title).setContentText(n.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.body))
            .setContentIntent(pi).setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
            (context.applicationContext as App).analytics.log("push_posted", "loop" to loop)
        } catch (_: SecurityException) { }
    }

    /** The work the daily job does, also callable from a test. Returns what was posted. */
    fun runDaily(context: Context): List<PlannedNotice> {
        val app = context.applicationContext as App
        val profile = app.profiles.profile ?: return emptyList()
        if (app.notify.enabled != true) return emptyList()
        val s = Session(profile)
        val moon = s.positions.firstOrNull { it.graha == Graha.MOON }?.houseFromMoon
        val week = NotifyPlanner.weekKey(s.today)
        val dailyDone = app.notify.lastDailyDate() == s.today.toString()
        // A check-in that falls due today (at most one a day, one per thread per gap).
        val checkin = app.rollCheckins(s.today)
        val planned = NotifyPlanner.forDay(s.today, s.zone, app.notify.prefs, s.timelineNow, s.panchangToday, moon,
            app.notify.sentEventKeys(), app.notify.eventAlertsInWeek(week), checkin)
            .filter { it.kind == NotifyKind.EVENT || it.kind == NotifyKind.CHECKIN || !dailyDone }
        planned.forEach { post(context, it, it.key.hashCode()) }
        if (planned.any { it.kind == NotifyKind.DAILY || it.kind == NotifyKind.WEEKLY }) app.notify.recordDaily(s.today.toString())
        app.analytics.flush()
        app.notify.recordEventAlerts(week, planned.filter { it.kind == NotifyKind.EVENT }.map { it.key })
        return planned
    }
}

class DailyNoteWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try { Notifications.runDaily(applicationContext); Result.success() } catch (e: Exception) { Result.retry() }
}
