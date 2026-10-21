package app.astra.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.astra.core.NotifyPrefs
import java.time.LocalTime

/** Notification settings and the small log that enforces caps (event keys sent, alerts per week). */
class NotifyRepository(context: Context) {
    private val sp = context.getSharedPreferences("notify", Context.MODE_PRIVATE)

    /** Null until the user has answered the "morning note" offer. */
    var enabled: Boolean? by mutableStateOf(if (sp.contains("enabled")) sp.getBoolean("enabled", false) else null)
        private set

    var prefs: NotifyPrefs by mutableStateOf(load())
        private set

    private fun load() = NotifyPrefs(
        daily = sp.getBoolean("daily", true), weekly = sp.getBoolean("weekly", true), events = sp.getBoolean("events", true),
        demandingAlerts = sp.getBoolean("demanding", false), checkins = sp.getBoolean("checkins", true),
        time = runCatching { LocalTime.parse(sp.getString("time", "07:30")) }.getOrDefault(LocalTime.of(7, 30))
    )

    /** Back to first-run state (the offer is shown again). */
    fun reset() { sp.edit().clear().apply(); enabled = null; prefs = load() }

    fun setEnabled(on: Boolean) { sp.edit().putBoolean("enabled", on).apply(); enabled = on }

    fun save(p: NotifyPrefs) {
        sp.edit().putBoolean("daily", p.daily).putBoolean("weekly", p.weekly).putBoolean("events", p.events)
            .putBoolean("demanding", p.demandingAlerts).putBoolean("checkins", p.checkins).putString("time", p.time.toString()).apply()
        prefs = p
    }

    fun sentEventKeys(): Set<String> = sp.getStringSet("sent", emptySet()) ?: emptySet()
    fun eventAlertsInWeek(week: String): Int = if (sp.getString("week", "") == week) sp.getInt("weekCount", 0) else 0
    fun recordEventAlerts(week: String, keys: List<String>) {
        if (keys.isEmpty()) return
        val count = eventAlertsInWeek(week) + keys.size
        val sent = (sentEventKeys() + keys).toList().takeLast(200).toSet()
        sp.edit().putString("week", week).putInt("weekCount", count).putStringSet("sent", sent).apply()
    }
    fun lastDailyDate(): String? = sp.getString("lastDaily", null)
    fun recordDaily(date: String) { sp.edit().putString("lastDaily", date).apply() }
}
