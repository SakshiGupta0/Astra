// Today: greeting, panchang for where you live, your Moon transit, current period and what's next. No AI in M2.
package app.astra.ui.today

import app.astra.App
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.astra.R
import app.astra.core.Copy
import app.astra.core.Graha
import app.astra.core.NowPosition
import app.astra.core.Panchang
import app.astra.core.Timeline
import app.astra.ui.Fmt
import app.astra.ui.Session
import app.astra.ui.error
import app.astra.ui.rememberCalc
import app.astra.ui.valueOrNull
import app.astra.ui.components.AIcon
import app.astra.ui.components.AstraCard
import app.astra.ui.components.KeyValue
import app.astra.ui.components.Loading
import app.astra.ui.components.Notice
import app.astra.ui.components.Pill
import app.astra.ui.components.SectionTitle
import app.astra.ui.components.Starfield
import app.astra.ui.theme.Astra
import app.astra.ui.tracker.toneColor
import app.astra.ui.tracker.toneGlyph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TodayScreen(app: App, session: Session) {
    val c = Astra.colors
    val z = session.zone
    val pCalc by rememberCalc(session) { session.panchangToday }
    val posCalc by rememberCalc(session) { session.positions }
    val tlCalc by rememberCalc(session) { session.timelineNow }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val aiLine by produceState<Pair<String, Int>?>(TodayLineCache.get(ctx, session.today.toString()), session, app.backend) {
        if (value == null && app.backend.available) value = runCatching {
            app.backend.syncFacts(withContext(Dispatchers.Default) { session.factsJson() }, session.today.toString())
            val r = app.backend.todayLine()
            TodayLineCache.put(ctx, session.today.toString(), r.answer, r.storyUsed.size)
            r.answer to r.storyUsed.size
        }.getOrNull()
    }
    val panchang = pCalc.valueOrNull
    val positions = posCalc.valueOrNull
    val timeline = tlCalc.valueOrNull
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(150.dp).background(c.backgroundAlt)) {
            Starfield(Modifier.fillMaxSize(), count = 45, seed = session.today.dayOfYear)
            Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
                val name = session.profile.name.ifBlank { null }
                Text(if (name != null) "Namaste, $name" else "Namaste", style = Astra.type.title, color = c.text, modifier = Modifier.testTag("greeting"))
                Text("${session.today.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}, ${Fmt.date(session.at, z)} · ${session.profile.currentPlace.label.substringBefore(',')}",
                    style = Astra.type.label, color = c.textSecondary)
            }
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            (pCalc.error ?: posCalc.error ?: tlCalc.error)?.let { Notice("Something went wrong in a calculation ($it). Please send this message to the ASTRA team.") }
            // Your day
            AstraCard(Modifier.testTag("yourDay")) {
                Text("Your day", style = Astra.type.heading, color = c.text)
                val moon = positions?.firstOrNull { it.graha == Graha.MOON }
                aiLine?.let { (line, storyCount) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AIcon(R.drawable.ic_sparkles, null, tint = c.accent, size = 18.dp)
                        Text(line, style = Astra.type.body, color = c.text, modifier = Modifier.testTag("todayLine"))
                    }
                    if (storyCount > 0) Pill("From your story", c.lavender, filled = true)
                }
                if (moon == null) Loading("Reading the sky…") else Text(Copy.todayLine(moon.houseFromMoon, moon.nakshatra),
                    style = if (aiLine == null) Astra.type.body else Astra.type.label, color = if (aiLine == null) c.text else c.textSecondary)
                val tone = timeline?.tone?.firstOrNull { !session.at.isBefore(it.start) && session.at.isBefore(it.end) }
                val dn = session.chart.dasha.at(session.at)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    tone?.let { Pill("${toneGlyph(it.tone)} ${it.tone.label} period", toneColor(it.tone), filled = true) }
                    dn?.let { Pill("${it.maha.lord.english}–${it.antar.lord.english}", c.border) }
                }
                timeline?.events?.firstOrNull { it.major && it.instant.isAfter(session.at) }?.let { e ->
                    Text("Next: ${e.title} · ${Fmt.date(e.instant, z)} (${Fmt.until(session.at, e.instant)})", style = Astra.type.label, color = c.textSecondary)
                }
                Text(Copy.NOT_PREDICTION, style = Astra.type.meta, color = c.textSecondary)
            }

            SectionTitle("Panchang")
            val p = panchang
            val sunrise = p?.sunrise; val sunset = p?.sunset
            val tithi = p?.tithi; val nak = p?.nakshatra; val yoga = p?.yoga
            val rkStart = p?.rahuKaalStart; val rkEnd = p?.rahuKaalEnd
            when {
                p == null -> Loading("Calculating panchang…")
                sunrise == null || sunset == null || tithi == null || nak == null || yoga == null || rkStart == null || rkEnd == null ->
                    Notice(p.note ?: "Panchang isn't available for this place today.")
                else -> AstraCard(Modifier.testTag("panchang")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AIcon(R.drawable.ic_sunrise, "Sunrise", tint = c.accent, size = 18.dp); Text(Fmt.time(sunrise, z), style = Astra.type.label, color = c.text)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AIcon(R.drawable.ic_sunset, "Sunset", tint = c.lavender, size = 18.dp); Text(Fmt.time(sunset, z), style = Astra.type.label, color = c.text)
                        }
                    }
                    KeyValue("Vara", p.vara)
                    KeyValue("Tithi", tithi.value.label + (tithi.endsAt?.let { " until ${until(it, session)}" } ?: ""))
                    KeyValue("Nakshatra", nak.value.title + (nak.endsAt?.let { " until ${until(it, session)}" } ?: ""))
                    KeyValue("Yoga", yoga.value + (yoga.endsAt?.let { " until ${until(it, session)}" } ?: ""))
                    KeyValue("Rahu Kaal", "${Fmt.time(rkStart, z)} – ${Fmt.time(rkEnd, z)}")
                    Text("Values at sunrise in ${session.profile.currentPlace.label.substringBefore(',')}, with the time each one ends.", style = Astra.type.meta, color = c.textSecondary)
                }
            }
        }
    }
}

private fun until(i: java.time.Instant, s: Session): String {
    val sameDay = i.atZone(s.zone).toLocalDate() == s.today
    return if (sameDay) Fmt.time(i, s.zone) else "${Fmt.dateShort(i, s.zone)}, ${Fmt.time(i, s.zone)}"
}

/** Today's AI line, cached on the phone for the day (one per day; the server enforces it too). */
object TodayLineCache {
    private fun sp(ctx: android.content.Context) = ctx.getSharedPreferences("today_line", android.content.Context.MODE_PRIVATE)
    fun get(ctx: android.content.Context, day: String): Pair<String, Int>? =
        sp(ctx).takeIf { it.getString("day", null) == day }?.getString("text", null)?.let { it to sp(ctx).getInt("story", 0) }
    fun put(ctx: android.content.Context, day: String, text: String, story: Int) =
        sp(ctx).edit().putString("day", day).putString("text", text).putInt("story", story).apply()
}
