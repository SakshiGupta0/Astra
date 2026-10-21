// Content-free usage events (PRD section 16). Events are queued in a small file on the phone and sent in batches
// to the AI server, which accepts only known event names and short codes. Never questions, answers, story or birth details.
package app.astra.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.astra.BuildConfig
import app.astra.core.Json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class Analytics(context: Context, private val cloud: () -> Cloud?, private val scope: CoroutineScope) {
    private val sp = context.getSharedPreferences("privacy", Context.MODE_PRIVATE)
    private val file = File(context.filesDir, "events.jsonl")
    private val lock = Mutex()

    /** Usage stats on/off (Me → Privacy). On by default for the beta, disclosed in the privacy notice. */
    private var on by mutableStateOf(sp.getBoolean("analytics", true))
    val enabled: Boolean get() = on

    /** Screen the user is on, for content-free error reports. */
    @Volatile var screen: String = "start"

    val installDate: LocalDate = sp.getString("installed", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: LocalDate.now().also { sp.edit().putString("installed", it.toString()).apply() }

    fun dayIndex(today: LocalDate = LocalDate.now()): Int = ChronoUnit.DAYS.between(installDate, today).toInt()

    fun setEnabled(value: Boolean) {
        sp.edit().putBoolean("analytics", value).apply(); on = value
        if (!value) runCatching { file.delete() }
    }

    /** Records an event. Property values must be short codes, numbers or booleans. */
    fun log(name: String, vararg props: Pair<String, Any?>) {
        if (!enabled) return
        val line = Json.write(mapOf("name" to name, "at" to Instant.now().toString(), "props" to props.filter { it.second != null }.toMap()))
        synchronized(file) { runCatching { file.appendText(line + "\n") } }
        if (pending() >= 15) flush()
    }

    private fun pending(): Int = synchronized(file) { runCatching { if (file.exists()) file.readLines().size else 0 }.getOrDefault(0) }

    /** Sends queued events; keeps them for later if offline. */
    fun flush() {
        val c = cloud() ?: return
        if (!enabled || !c.available) return
        scope.launch {
            lock.withLock {
                val lines = synchronized(file) { runCatching { if (file.exists()) file.readLines().filter { it.isNotBlank() } else emptyList() }.getOrDefault(emptyList()) }
                if (lines.isEmpty()) return@withLock
                for (batch in lines.chunked(50)) {
                    val events = batch.mapNotNull { runCatching { Json.parse(it) }.getOrNull() }
                    val ok = runCatching { c.post("/events", mapOf("app" to BuildConfig.VERSION_NAME, "events" to events)) in 200..299 }.getOrDefault(false)
                    if (!ok) return@withLock
                    synchronized(file) {
                        val rest = runCatching { file.readLines().filter { it.isNotBlank() } }.getOrDefault(emptyList()).drop(batch.size)
                        runCatching { if (rest.isEmpty()) file.delete() else file.writeText(rest.joinToString("\n", postfix = "\n")) }
                    }
                }
            }
        }
    }

    fun clear() { synchronized(file) { runCatching { file.delete() } } }

    /** Records a crash as type and screen only, then lets Android handle it as usual. */
    fun installCrashCapture() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { log("app_error", "screen" to screen.take(40), "kind" to (e.javaClass.simpleName.ifBlank { "Error" }).take(40)) }
            previous?.uncaughtException(t, e)
        }
    }
}
