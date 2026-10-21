// Notifications in Me: on/off per loop, time, separate opt-in for demanding-period alerts (PRD F-08).
package app.astra.ui.me

import app.astra.core.NotifyPrefs
import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.Text
import app.astra.App
import app.astra.core.NotifyPlanner
import app.astra.system.Notifications
import app.astra.ui.components.AstraCard
import app.astra.ui.components.Notice
import app.astra.ui.components.SectionTitle
import app.astra.ui.components.TextLink
import app.astra.ui.theme.Astra
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatTime(t: LocalTime): String = t.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)).lowercase()

/** Turns notifications on, asking Android for permission first where needed. */
@Composable
fun rememberEnableNotifications(app: App, onResult: (Boolean) -> Unit = {}): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        app.notify.setEnabled(granted); Notifications.schedule(ctx); onResult(granted)
    }
    return {
        if (Build.VERSION.SDK_INT >= 33 && !Notifications.canPost(ctx)) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else { app.notify.setEnabled(true); Notifications.schedule(ctx); onResult(true) }
    }
}

@Composable
fun NotifySection(app: App) {
    val c = Astra.colors
    val ctx = LocalContext.current
    val on = app.notify.enabled == true
    val p = app.notify.prefs
    val enable = rememberEnableNotifications(app) { granted -> app.analytics.log("push_permission", "granted" to granted, "ask_point" to "settings") }
    fun save(next: NotifyPrefs) { app.notify.save(next); Notifications.schedule(ctx) }
    SectionTitle("Notifications")
    AstraCard(Modifier.testTag("notifyCard")) {
        ToggleRow("Notifications", if (on) "Daily at ${formatTime(NotifyPlanner.fireTime(p))}" else "Off", on,
            { if (it) enable() else { app.notify.setEnabled(false); Notifications.schedule(ctx) } }, tag = "notifySwitch")
        if (on && !Notifications.canPost(ctx)) Notice("Android is blocking ASTRA's notifications. Allow them in your phone's settings for ASTRA.")
        if (on) {
            ToggleRow("Morning note", "Tithi, your Moon and Rahu Kaal", p.daily, { save(p.copy(daily = it)) })
            TextLink("Time: ${formatTime(p.time)} · change", {
                TimePickerDialog(ctx, { _, h, m -> save(p.copy(time = LocalTime.of(h, m))) }, p.time.hour, p.time.minute, false).show()
            })
            ToggleRow("Week ahead on Sundays", "Replaces the morning note that day", p.weekly, { save(p.copy(weekly = it)) })
            ToggleRow("Changes in your chart", "A week before and on the day. At most two a week.", p.events, { save(p.copy(events = it)) })
            ToggleRow("Story check-ins", "A gentle follow-up on an open question in My Story. At most one a day.", p.checkins, { save(p.copy(checkins = it)) })
            if (p.events) ToggleRow("Include demanding periods", "Off by default. Only tells you when a demanding stretch begins.", p.demandingAlerts, { save(p.copy(demandingAlerts = it)) })
            Text("Quiet between 10 pm and 7 am.", style = Astra.type.meta, color = c.textSecondary)
        }
    }
}
