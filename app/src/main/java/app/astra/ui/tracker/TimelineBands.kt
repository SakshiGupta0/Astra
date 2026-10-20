// Horizontal timeline of periods: dasha, tone, Saturn, Jupiter, Rahu–Ketu and retrogrades.
package app.astra.ui.tracker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.core.Copy
import app.astra.core.Gochar
import app.astra.core.Graha
import app.astra.core.SaturnPhase
import app.astra.core.Timeline
import app.astra.core.Tone
import app.astra.core.Vimshottari
import app.astra.core.ordinal
import app.astra.ui.Fmt
import app.astra.ui.Session
import app.astra.ui.theme.Astra
import app.astra.ui.theme.Inter
import java.time.Instant
import java.time.ZonedDateTime

/** One tappable segment of a band. */
data class BandItem(val row: Int, val start: Instant, val end: Instant, val label: String, val title: String, val lines: List<String>, val color: Color, val dashed: Boolean = false)

private val ROWS = listOf("Dasha", "Tone", "Saturn", "Jupiter", "Rahu·Ketu", "Retro")

@Composable
fun TimelineBands(session: Session, tl: Timeline, onSelect: (BandItem) -> Unit, modifier: Modifier = Modifier) {
    val c = Astra.colors
    val z = session.zone
    val items = remember(tl, c) { buildItems(tl, c.gold, c.lavender, c.supportive, c.mixed, c.demanding, c.textSecondary) }
    val months = remember(tl) {
        val out = ArrayList<Instant>()
        var m = ZonedDateTime.ofInstant(tl.from, z).withDayOfMonth(1).toLocalDate().atStartOfDay(z)
        while (m.toInstant().isBefore(tl.to)) { out += m.toInstant(); m = m.plusMonths(1) }
        out
    }
    val monthW = 64.dp; val rowH = 34.dp; val headerH = 26.dp; val labelW = 76.dp
    val totalW = monthW * months.size
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    val span = (tl.to.toEpochMilli() - tl.from.toEpochMilli()).toFloat()
    LaunchedEffect(tl) {
        val frac = (session.at.toEpochMilli() - tl.from.toEpochMilli()) / span
        with(density) { scroll.scrollTo(((totalW.toPx() * frac) - 40.dp.toPx()).toInt().coerceAtLeast(0)) }
    }
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontFamily = Inter, fontSize = 11.sp, color = c.text)
    val metaStyle = TextStyle(fontFamily = Inter, fontSize = 10.sp, color = c.textSecondary)

    Row(modifier.padding(start = 20.dp)) {
        Column(Modifier.width(labelW).padding(top = headerH)) {
            ROWS.forEach { Box(Modifier.height(rowH)) { Text(it, style = Astra.type.meta, color = c.textSecondary, modifier = Modifier.padding(top = 9.dp)) } }
        }
        Box(Modifier.horizontalScroll(scroll)) {
            Canvas(
                Modifier.width(totalW).height(headerH + rowH * ROWS.size)
                    .semantics { contentDescription = "Timeline of your periods. Use the list below for the same information." }
                    .pointerInput(items) {
                        detectTapGestures { pos ->
                            val row = ((pos.y - headerH.toPx()) / rowH.toPx()).toInt()
                            val t = tl.from.toEpochMilli() + (pos.x / size.width * span).toLong()
                            items.filter { it.row == row && t >= it.start.toEpochMilli() && t < it.end.toEpochMilli() }.lastOrNull()?.let(onSelect)
                        }
                    }
            ) {
                fun x(i: Instant) = (i.toEpochMilli() - tl.from.toEpochMilli()) / span * size.width
                val hH = headerH.toPx(); val rH = rowH.toPx()
                // month grid
                months.forEach { m ->
                    val mx = x(m)
                    drawLine(c.border.copy(alpha = 0.5f), Offset(mx, hH - 4f), Offset(mx, size.height), 1f)
                    drawLabel(measurer, Fmt.monthYear(m, z), metaStyle, Offset(mx + 4f, 2f), monthW.toPx() - 6f)
                }
                // bands
                items.forEach { it.draw(this, ::x, hH, rH, measurer, labelStyle) }
                // today
                val tx = x(session.at)
                drawLine(c.gold, Offset(tx, hH - 6f), Offset(tx, size.height), 3f)
                drawLabel(measurer, "Today", labelStyle.copy(color = c.gold), Offset(tx + 4f, hH - 16f), 60.dp.toPx())
            }
        }
    }
}

private fun BandItem.draw(s: DrawScope, x: (Instant) -> Float, hH: Float, rH: Float, m: TextMeasurer, style: TextStyle) = with(s) {
    val x0 = x(start); val x1 = x(end)
    val top = hH + row * rH + rH * 0.15f
    val h = rH * 0.7f
    val w = (x1 - x0 - 2f).coerceAtLeast(2f)
    drawRoundRect(color.copy(alpha = 0.32f), Offset(x0 + 1f, top), Size(w, h), CornerRadius(8f, 8f))
    drawRoundRect(color.copy(alpha = 0.85f), Offset(x0 + 1f, top), Size(w, h), CornerRadius(8f, 8f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null))
    if (w > 28f) drawLabel(m, label, style, Offset(x0 + 8f, top + h / 2 - 8f), w - 12f)
}

private fun DrawScope.drawLabel(m: TextMeasurer, text: String, style: TextStyle, at: Offset, maxW: Float) {
    if (maxW < 10f) return
    val layout = m.measure(text, style, overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = maxW.toInt()))
    drawText(layout, topLeft = at)
}

private fun buildItems(tl: Timeline, gold: Color, lavender: Color, sup: Color, mix: Color, dem: Color, neutral: Color): List<BandItem> {
    val out = ArrayList<BandItem>()
    tl.antardashas.forEach { a ->
        val maha = a.parentLords.first()
        out += BandItem(0, maxOf(a.start, tl.from), minOf(a.end, tl.to), "${Fmt.abbrev(maha)}–${Fmt.abbrev(a.lord)}",
            "${maha.english}–${a.lord.english} period",
            listOf("Sub-period of ${a.lord.english} within your ${maha.english} mahadasha (${Vimshottari.YEARS.getValue(maha).toInt()}-year major period).",
                "${maha.english}: ${Copy.GRAHA_THEME.getValue(maha)}. ${a.lord.english}: ${Copy.GRAHA_THEME.getValue(a.lord)}."),
            if (a.lord == maha) gold else lavender)
    }
    tl.tone.forEach { t ->
        val col = when (t.tone) { Tone.SUPPORTIVE -> sup; Tone.MIXED -> mix; Tone.DEMANDING -> dem }
        out += BandItem(1, t.start, t.end, "${toneGlyph(t.tone)} ${t.tone.label}", "${t.tone.label} period", listOf(Copy.TONE_TEXT.getValue(t.tone)) + t.factors, col, dashed = t.tone == Tone.DEMANDING)
    }
    tl.saturn.forEach { s ->
        val label = if (s.phase == SaturnPhase.OTHER) "Sa ${ordinal(s.house)}" else s.phase.label.substringBefore(" (").replace("Sade Sati — ", "Sade Sati: ")
        out += BandItem(2, s.start, s.end, label, if (s.phase == SaturnPhase.OTHER) "Saturn ${ordinal(s.house)} from your Moon" else s.phase.label,
            listOf(Copy.SATURN_PHASE_TEXT.getValue(s.phase), Copy.houseFromMoonLine(Graha.SATURN, s.house)),
            if (s.phase == SaturnPhase.OTHER) neutral else lavender)
    }
    tl.signIntervals.getValue(Graha.JUPITER).forEach { j ->
        val fav = Gochar.isFavourable(Graha.JUPITER, j.houseFromMoon)
        out += BandItem(3, j.start, j.end, "Ju ${ordinal(j.houseFromMoon)}${if (fav) " ★" else ""}", "Jupiter in ${j.rashi.sanskrit}",
            listOf(Copy.houseFromMoonLine(Graha.JUPITER, j.houseFromMoon)) + if (fav) listOf("Traditionally a supportive position for Jupiter.") else emptyList(),
            if (fav) gold else neutral)
    }
    val ketu = tl.signIntervals.getValue(Graha.KETU)
    tl.signIntervals.getValue(Graha.RAHU).forEachIndexed { i, r ->
        val k = ketu[i]
        out += BandItem(4, r.start, r.end, "Ra ${ordinal(r.houseFromMoon)} · Ke ${ordinal(k.houseFromMoon)}", "Rahu in ${r.rashi.sanskrit}, Ketu in ${k.rashi.sanskrit}",
            listOf(Copy.houseFromMoonLine(Graha.RAHU, r.houseFromMoon), Copy.houseFromMoonLine(Graha.KETU, k.houseFromMoon)), lavender)
    }
    tl.retrogrades.forEach { r ->
        out += BandItem(5, maxOf(r.start, tl.from), minOf(r.end, tl.to), "${Fmt.abbrev(r.graha)} R", "${r.graha.english} retrograde",
            listOf(Copy.RETROGRADE_TEXT, "${r.graha.english}: ${Copy.GRAHA_THEME.getValue(r.graha)}."), lavender, dashed = true)
    }
    return out
}
