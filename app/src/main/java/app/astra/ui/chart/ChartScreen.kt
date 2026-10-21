// Your chart: North Indian chart, placements, dasha timeline and method disclosure (PRD F-02, F-03).
package app.astra.ui.chart

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.core.ChartSnapshot
import app.astra.core.Copy
import app.astra.core.DashaPeriod
import app.astra.core.Ephemeris
import app.astra.core.Graha
import app.astra.core.Houses
import app.astra.core.LagnaStatus
import app.astra.core.Rashi
import app.astra.ui.Fmt
import app.astra.ui.Session
import app.astra.ui.components.AstraCard
import app.astra.ui.components.KeyValue
import app.astra.ui.components.Notice
import app.astra.ui.components.Pill
import app.astra.ui.components.SectionTitle
import app.astra.ui.theme.Astra
import app.astra.ui.theme.Inter

@Composable
fun ChartScreen(session: Session, embedded: Boolean = false) {
    val chart = session.chart
    val c = Astra.colors
    val moonChart = chart.lagnaLon == null
    Column(Modifier.fillMaxSize().then(if (embedded) Modifier else Modifier.statusBarsPadding()).verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = if (embedded) 0.dp else 12.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!embedded) Text("Your chart", style = Astra.type.title, color = c.text)
        Text("Vedic (sidereal) · born in ${session.profile.birthPlace.label}", style = Astra.type.meta, color = c.textSecondary)
        chart.warnings.forEach { Notice(it) }

        AstraCard {
            Text(if (moonChart) "Moon chart (Chandra lagna)" else "Birth chart (Rasi)", style = Astra.type.heading, color = c.text)
            NorthIndianChart(chart, Modifier.fillMaxWidth().aspectRatio(1f).padding(top = 4.dp))
            if (moonChart) Text(Copy.MOON_CHART_NOTE, style = Astra.type.meta, color = c.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Lagna", style = Astra.type.meta, color = c.textSecondary)
                    Text(lagnaText(chart), style = Astra.type.bodyStrong, color = c.text, modifier = Modifier.testTag("lagna"))
                }
                Column(Modifier.weight(1f)) {
                    Text("Moon sign", style = Astra.type.meta, color = c.textSecondary)
                    Text(Fmt.sign(chart.moon.rashi) + if (!chart.moonSignCertain) " (uncertain)" else "", style = Astra.type.bodyStrong, color = c.text)
                }
            }
            Column {
                Text("Birth nakshatra", style = Astra.type.meta, color = c.textSecondary)
                Text("${chart.moon.nakshatra.title}, pada ${chart.moon.pada}" + if (!chart.moonNakshatraCertain) " (uncertain)" else "", style = Astra.type.bodyStrong, color = c.text)
            }
        }

        SectionTitle("Placements")
        AstraCard {
            Row { listOf("Graha" to 0.26f, "Sign" to 0.34f, "Nakshatra" to 0.26f, "House" to 0.14f).forEach { (h, w) -> Text(h, style = Astra.type.meta, color = c.textSecondary, modifier = Modifier.weight(w)) } }
            chart.placements.forEach { p ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text(p.graha.english + if (p.retrograde && p.graha != Graha.RAHU && p.graha != Graha.KETU) " ℞" else "", style = Astra.type.label, color = c.text, modifier = Modifier.weight(0.26f))
                    Text("${p.rashi.sanskrit} ${Fmt.degree(p.lon)}", style = Astra.type.label, color = c.text, modifier = Modifier.weight(0.34f))
                    Text("${p.nakshatra.title} ${p.pada}", style = Astra.type.label, color = c.text, modifier = Modifier.weight(0.26f))
                    Text((p.houseFromLagna ?: p.houseFromMoon).toString(), style = Astra.type.label, color = c.text, modifier = Modifier.weight(0.14f), textAlign = TextAlign.Center)
                }
            }
            Text(if (moonChart) "Houses counted from your Moon sign." else "Whole-sign houses from your Lagna. ℞ = retrograde.", style = Astra.type.meta, color = c.textSecondary)
        }

        SectionTitle("Dasha periods")
        DashaList(session)

        SectionTitle("How this was calculated")
        AstraCard {
            chart.settings.forEach { (k, v) -> KeyValue(k.replaceFirstChar { it.uppercase() }, v) }
            KeyValue("Birth time used", "${Fmt.date(chart.instant, java.time.ZoneId.of(chart.input.zoneId))}, ${Fmt.time(chart.instant, java.time.ZoneId.of(chart.input.zoneId))}")
            KeyValue("Calculation", chart.calcVersion.substringBefore(":"))
            Text("Lahiri is the most widely used ayanamsha in India; other conventions exist and give slightly different degrees.", style = Astra.type.meta, color = c.textSecondary)
        }
    }
}

private fun lagnaText(chart: ChartSnapshot) = when (chart.lagnaStatus) {
    LagnaStatus.EXACT -> "${Fmt.sign(chart.lagnaRashi!!)} ${Fmt.degree(chart.lagnaLon!!)}"
    LagnaStatus.APPROXIMATE -> "${Fmt.sign(chart.lagnaRashi!!)} (approximate)"
    LagnaStatus.UNCERTAIN -> chart.lagnaCandidates.joinToString(" or ") { it.sanskrit } + " (uncertain)"
    LagnaStatus.UNAVAILABLE -> "Needs birth time"
}

@Composable
private fun DashaList(session: Session) {
    val c = Astra.colors
    val z = session.zone
    val d = session.chart.dasha
    val now = d.at(session.at)
    var openMaha by remember { mutableStateOf(now?.maha?.lord) }
    AstraCard(Modifier.animateContentSize()) {
        Text("Born in ${d.birthNakshatra.title} (ruled by ${d.firstLord.english}): ${"%.1f".format(d.balanceYears)} years of ${d.firstLord.english} mahadasha remained at birth.",
            style = Astra.type.meta, color = c.textSecondary)
        d.mahadashas.take(9).forEach { m ->
            val current = m.contains(session.at)
            Row(Modifier.fillMaxWidth().clickable { openMaha = if (openMaha == m.lord) null else m.lord }.padding(vertical = 8.dp)) {
                Text(m.lord.english, style = if (current) Astra.type.bodyStrong else Astra.type.body, color = if (current) c.accent else c.text, modifier = Modifier.weight(0.35f))
                Text("${Fmt.date(m.start, z)} – ${Fmt.date(m.end, z)}", style = Astra.type.label, color = c.textSecondary, modifier = Modifier.weight(0.55f))
                if (current) Pill("Now", c.accent)
            }
            if (openMaha == m.lord) d.subPeriods(m).forEach { a -> SubRow(a, session, depth = 1) }
        }
    }
}

@Composable
private fun SubRow(p: DashaPeriod, session: Session, depth: Int) {
    val c = Astra.colors
    val current = p.contains(session.at)
    Row(Modifier.fillMaxWidth().padding(start = (16 * depth).dp, top = 3.dp, bottom = 3.dp)) {
        Text(p.path.drop(1).joinToString("–") { it.english }, style = Astra.type.label, color = if (current) c.accent else c.text, modifier = Modifier.weight(0.4f))
        Text("${Fmt.date(p.start, session.zone)} – ${Fmt.date(p.end, session.zone)}", style = Astra.type.meta, color = c.textSecondary, modifier = Modifier.weight(0.6f))
    }
    if (current && depth == 1) session.chart.dasha.subPeriods(p).forEach { SubRow(it, session, 2) }
}

/** North Indian (diamond) chart. House 1 at the top centre, houses run anticlockwise. Sign numbers mark each house. */
@Composable
fun NorthIndianChart(chart: ChartSnapshot, modifier: Modifier = Modifier) {
    val c = Astra.colors
    val measurer = rememberTextMeasurer()
    val ascLon = chart.lagnaLon ?: chart.moon.lon
    val ascSign = Ephemeris.signOf(ascLon)
    val byHouse = chart.placements.groupBy { Houses.fromLon(it.lon, ascLon) }
    val desc = (1..12).joinToString("; ") { h ->
        val sign = Rashi.entries[(ascSign + h - 1) % 12].sanskrit
        "House $h, $sign: " + (byHouse[h]?.joinToString { it.graha.english } ?: "empty")
    }
    Canvas(modifier.semantics { contentDescription = desc }) {
        val w = size.width; val h = size.height
        val line = Stroke(width = 1.6f)
        drawRect(c.accent.copy(alpha = 0.8f), style = line)
        drawLine(c.accent.copy(alpha = 0.8f), Offset(0f, 0f), Offset(w, h), 1.6f)
        drawLine(c.accent.copy(alpha = 0.8f), Offset(w, 0f), Offset(0f, h), 1.6f)
        val diamond = Path().apply { moveTo(w / 2, 0f); lineTo(w, h / 2); lineTo(w / 2, h); lineTo(0f, h / 2); close() }
        drawPath(diamond, c.accent.copy(alpha = 0.8f), style = line)
        // centres for planets and positions for sign numbers, as fractions of the square
        val centres = mapOf(1 to (0.5f to 0.27f), 2 to (0.25f to 0.11f), 3 to (0.11f to 0.25f), 4 to (0.27f to 0.5f), 5 to (0.11f to 0.75f), 6 to (0.25f to 0.89f),
            7 to (0.5f to 0.73f), 8 to (0.75f to 0.89f), 9 to (0.89f to 0.75f), 10 to (0.73f to 0.5f), 11 to (0.89f to 0.25f), 12 to (0.75f to 0.11f))
        val signPos = mapOf(1 to (0.5f to 0.44f), 2 to (0.25f to 0.21f), 3 to (0.21f to 0.25f), 4 to (0.44f to 0.5f), 5 to (0.21f to 0.75f), 6 to (0.25f to 0.79f),
            7 to (0.5f to 0.56f), 8 to (0.75f to 0.79f), 9 to (0.79f to 0.75f), 10 to (0.56f to 0.5f), 11 to (0.79f to 0.25f), 12 to (0.75f to 0.21f))
        val signStyle = TextStyle(fontFamily = Inter, fontSize = 11.sp, color = c.textSecondary)
        val planetStyle = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = c.text)
        val ascStyle = planetStyle.copy(color = c.accent)
        for (house in 1..12) {
            val sign = (ascSign + house - 1) % 12 + 1
            val (sx, sy) = signPos.getValue(house)
            val sl = measurer.measure(sign.toString(), signStyle)
            drawText(sl, topLeft = Offset(sx * w - sl.size.width / 2, sy * h - sl.size.height / 2))
            val labels = buildList {
                if (house == 1 && chart.lagnaLon != null) add("As" to true)
                byHouse[house]?.forEach { add((Fmt.abbrev(it.graha) + if (it.retrograde && it.graha != Graha.RAHU && it.graha != Graha.KETU) "℞" else "") to false) }
            }
            if (labels.isEmpty()) continue
            val (cx, cy) = centres.getValue(house)
            val maxW = (w * 0.2f).toInt()
            val text = labels.joinToString(" ") { it.first }
            val layout = measurer.measure(text, if (labels.first().second && labels.size == 1) ascStyle else planetStyle, constraints = Constraints(maxWidth = maxW))
            drawText(layout, topLeft = Offset(cx * w - layout.size.width / 2, cy * h - layout.size.height / 2))
        }
    }
}
