// Graha Tracker: where every planet is now, where you are in your periods, and what's coming (PRD F-04).
package app.astra.ui.tracker

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.astra.R
import app.astra.core.Copy
import app.astra.core.Graha
import app.astra.core.NowPosition
import app.astra.core.SaturnPhase
import app.astra.core.Timeline
import app.astra.core.Tone
import app.astra.core.ordinal
import app.astra.ui.Fmt
import app.astra.ui.Session
import app.astra.ui.components.AIcon
import app.astra.ui.components.AstraCard
import app.astra.ui.components.Loading
import app.astra.ui.components.Notice
import app.astra.ui.error
import app.astra.ui.rememberCalc
import app.astra.ui.valueOrNull
import app.astra.ui.components.Pill
import app.astra.ui.components.SectionTitle
import app.astra.ui.components.Segmented
import app.astra.ui.theme.Astra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TrackerScreen(session: Session) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val posCalc by rememberCalc(session) { session.positions }
    val tlCalc by rememberCalc(session) { session.timelineNow }
    val positions = posCalc.valueOrNull
    val timeline = tlCalc.valueOrNull
    val c = Astra.colors
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
            Text("Graha Tracker", style = Astra.type.title, color = c.text)
            Text("${Fmt.date(session.at, session.zone)} · ${session.profile.currentPlace.label.substringBefore(',')}", style = Astra.type.meta, color = c.textSecondary)
            Segmented(listOf("Now", "Timeline"), tab, { tab = it }, Modifier.fillMaxWidth().padding(vertical = 12.dp))
            (posCalc.error ?: tlCalc.error)?.let { Notice("Something went wrong in a calculation ($it). Please send this message to the ASTRA team.") }
        }
        if (tab == 0) NowTab(session, positions, timeline) else TimelineTab(session, timeline)
    }
}

@Composable
fun toneColor(t: Tone) = when (t) { Tone.SUPPORTIVE -> Astra.colors.supportive; Tone.MIXED -> Astra.colors.mixed; Tone.DEMANDING -> Astra.colors.demanding }
fun toneGlyph(t: Tone) = when (t) { Tone.SUPPORTIVE -> "▲"; Tone.MIXED -> "◆"; Tone.DEMANDING -> "▼" }

@Composable
private fun NowTab(session: Session, positions: List<NowPosition>?, timeline: Timeline?) {
    val c = Astra.colors
    val z = session.zone
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Where you are
        AstraCard(Modifier.testTag("whereYouAre")) {
            Text("Where you are", style = Astra.type.heading, color = c.text)
            val dn = session.chart.dasha.at(session.at)
            if (dn != null) {
                Text("${dn.maha.lord.english} mahadasha", style = Astra.type.bodyStrong, color = c.accent, modifier = Modifier.testTag("mahadasha"))
                Text("${dn.antar.lord.english} sub-period until ${Fmt.date(dn.antar.end, z)} (${Fmt.until(session.at, dn.antar.end)})", style = Astra.type.body, color = c.text)
                Text("${dn.pratyantar.lord.english} pratyantar until ${Fmt.date(dn.pratyantar.end, z)} · mahadasha ends ${Fmt.date(dn.maha.end, z)}", style = Astra.type.meta, color = c.textSecondary)
                if (session.chart.dashaUncertaintyDays > 0 || session.chart.dashaLordUncertain)
                    Text(if (session.chart.dashaLordUncertain) "Your dasha sequence depends on your unknown birth time." else "Dates may shift by up to ${"%.0f".format(session.chart.dashaUncertaintyDays)} days because of your birth-time window.",
                        style = Astra.type.meta, color = c.lavender)
            }
            val tone = timeline?.tone?.firstOrNull { !session.at.isBefore(it.start) && session.at.isBefore(it.end) }
            if (tone != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("${toneGlyph(tone.tone)} ${tone.tone.label}", toneColor(tone.tone), filled = true, modifier = Modifier.testTag("tone"))
                    val segs = timeline!!.tone
                    val nextChange = segs.drop(segs.indexOf(tone) + 1).firstOrNull { it.tone != tone.tone }
                    Text(nextChange?.let { "until ${Fmt.date(it.start, z)}" } ?: "", style = Astra.type.meta, color = c.textSecondary)
                }
                Text(Copy.TONE_TEXT.getValue(tone.tone), style = Astra.type.label, color = c.text)
                tone.factors.forEach { Text("• $it", style = Astra.type.meta, color = c.textSecondary) }
            } else Loading("Working out your period…")
            val saturn = positions?.firstOrNull { it.graha == Graha.SATURN }
            val phase = saturn?.let { SaturnPhase.ofHouse(it.houseFromMoon) }
            if (phase != null && phase != SaturnPhase.OTHER) {
                Pill(phase.label, c.lavender)
                Text(Copy.SATURN_PHASE_TEXT.getValue(phase), style = Astra.type.meta, color = c.textSecondary)
            }
            timeline?.events?.firstOrNull { it.major && it.instant.isAfter(session.at) }?.let { e ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AIcon(R.drawable.ic_calendar_days, null, tint = c.accent, size = 18.dp)
                    Text("Next: ${e.title}, ${Fmt.date(e.instant, z)} (${Fmt.until(session.at, e.instant)})", style = Astra.type.label, color = c.text)
                }
            }
        }

        SectionTitle("Planets now")
        if (positions == null) Loading("Calculating positions…")
        positions?.forEach { PlanetRow(it, session) }
        Text("${Copy.NOT_PREDICTION} Houses are counted from your Moon sign${if (session.chart.lagnaLon != null) " (and from your Lagna)" else ""}. Lahiri ayanamsha, whole-sign houses, mean nodes.",
            style = Astra.type.meta, color = c.textSecondary, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun PlanetRow(p: NowPosition, session: Session) {
    val c = Astra.colors
    var open by remember { mutableStateOf(false) }
    AstraCard(Modifier.animateContentSize().testTag("planet_${p.graha.name}"), onClick = { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).border(1.dp, c.accent.copy(alpha = 0.6f), CircleShape), contentAlignment = Alignment.Center) {
                Text(Fmt.abbrev(p.graha), style = Astra.type.bodyStrong, color = c.accent)
            }
            Column(Modifier.weight(1f)) {
                Text("${p.graha.english} · ${p.graha.sanskrit}", style = Astra.type.bodyStrong, color = c.text)
                Text("${p.rashi.sanskrit} ${Fmt.degree(p.lon)} · ${p.nakshatra.title}", style = Astra.type.label, color = c.textSecondary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${ordinal(p.houseFromMoon)} from Moon", style = Astra.type.label, color = c.text)
                if (p.favourableFromMoon) Text("★ supportive", style = Astra.type.meta, color = c.accent)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (p.retrograde && p.graha != Graha.RAHU && p.graha != Graha.KETU) Pill("Retrograde", c.lavender)
            p.nextSignChange?.let { Pill("→ ${p.nextRashi?.sanskrit} ${Fmt.dateShort(it, session.zone)}", c.border) }
        }
        if (open) {
            Text("${p.graha.english} represents ${Copy.GRAHA_THEME.getValue(p.graha)} in Vedic tradition.", style = Astra.type.label, color = c.text)
            Text(Copy.houseFromMoonLine(p.graha, p.houseFromMoon), style = Astra.type.label, color = c.text)
            p.houseFromLagna?.let { Text("From your Lagna it is in the ${ordinal(it)} house.", style = Astra.type.meta, color = c.textSecondary) }
            if (p.retrograde && p.graha != Graha.RAHU && p.graha != Graha.KETU) Text(Copy.RETROGRADE_TEXT, style = Astra.type.meta, color = c.textSecondary)
            p.nextSignChange?.let { Text("Moves into ${p.nextRashi?.sanskrit} on ${Fmt.date(it, session.zone)} (${Fmt.until(session.at, it)}).", style = Astra.type.meta, color = c.textSecondary) }
        }
    }
}

@Composable
private fun TimelineTab(session: Session, timeline: Timeline?) {
    if (timeline == null) { Loading("Plotting 3 years of transits…"); return }
    var showMinor by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<BandItem?>(null) }
    val c = Astra.colors
    val z = session.zone
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TimelineBands(session, timeline, onSelect = { selected = it }, modifier = Modifier.testTag("timelineBands"))
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Legend()
            selected?.let { item ->
                AstraCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.title, style = Astra.type.heading, color = c.text, modifier = Modifier.weight(1f))
                        Box(Modifier.size(40.dp).clickable { selected = null }, contentAlignment = Alignment.Center) { AIcon(R.drawable.ic_x, "Close", size = 18.dp) }
                    }
                    Text("${Fmt.date(item.start, z)} – ${Fmt.date(item.end, z)}", style = Astra.type.label, color = c.textSecondary)
                    item.lines.forEach { Text(it, style = Astra.type.label, color = c.text) }
                    Text(Copy.NOT_PREDICTION, style = Astra.type.meta, color = c.textSecondary)
                }
            }
            SectionTitle("Coming up") {
                Box(Modifier.clickable { showMinor = !showMinor }.padding(8.dp)) {
                    Text(if (showMinor) "Major only" else "Show all", style = Astra.type.label, color = c.lavender)
                }
            }
            val upcoming = timeline.events.filter { it.instant.isAfter(session.at) && (showMinor || it.major) }.take(if (showMinor) 60 else 25)
            if (upcoming.isEmpty()) Text("No events in this window.", style = Astra.type.label, color = c.textSecondary)
            upcoming.forEach { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.size(width = 64.dp, height = 40.dp)) {
                        Text(Fmt.dateShort(e.instant, z), style = Astra.type.label, color = if (e.major) c.accent else c.textSecondary)
                        Text(e.instant.atZone(z).year.toString(), style = Astra.type.meta, color = c.textSecondary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(e.title, style = Astra.type.bodyStrong, color = c.text)
                        Text(e.detail, style = Astra.type.meta, color = c.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend() {
    val c = Astra.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Tone.entries.forEach { Pill("${toneGlyph(it)} ${it.label}", toneColor(it), filled = true) }
    }
    Text("Tap any band for details. Scroll sideways: 12 months back, 24 ahead.", style = Astra.type.meta, color = c.textSecondary, textAlign = TextAlign.Start)
}
