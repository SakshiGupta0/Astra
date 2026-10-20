// Onboarding (also used to edit birth details). Target: under 3 minutes. No account needed in M2.
package app.astra.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.astra.R
import app.astra.core.BirthTimeStatus
import app.astra.core.Profile
import app.astra.core.ReplyLanguage
import app.astra.core.SavedPlace
import app.astra.core.ThemeChoice
import app.astra.ui.components.AField
import app.astra.ui.components.AIcon
import app.astra.ui.components.ChoiceChips
import app.astra.ui.components.Notice
import app.astra.ui.components.PlaceSearch
import app.astra.ui.components.PrimaryButton
import app.astra.ui.components.Segmented
import app.astra.ui.components.Starfield
import app.astra.ui.theme.Astra
import java.time.LocalDate
import java.time.LocalTime

private enum class Step { WELCOME, NAME, DATE, TIME, PLACE, NOW, LANGUAGE }

/** Mutable draft while the flow is open. */
private class Draft(p: Profile?) {
    var name by mutableStateOf(p?.name ?: "")
    var day by mutableStateOf(p?.birthDate?.dayOfMonth?.toString() ?: "")
    var month by mutableStateOf(p?.birthDate?.monthValue?.toString() ?: "")
    var year by mutableStateOf(p?.birthDate?.year?.toString() ?: "")
    var status by mutableStateOf(p?.timeStatus)
    var hour by mutableStateOf(p?.birthTime?.let { ((it.hour + 11) % 12 + 1).toString() } ?: "")
    var minute by mutableStateOf(p?.birthTime?.minute?.let { "%02d".format(it) } ?: "")
    var pm by mutableStateOf(p?.birthTime?.let { it.hour >= 12 } ?: false)
    var window by mutableIntStateOf(p?.windowMinutes?.takeIf { it > 0 } ?: 60)
    var birthPlace by mutableStateOf(p?.birthPlace)
    var nowPlace by mutableStateOf(p?.currentPlace)
    var sameAsBirth by mutableStateOf(p != null && p.currentPlace == p.birthPlace)
    var language by mutableStateOf(p?.language ?: ReplyLanguage.EN)
    val offset = p?.utcOffsetOverrideMinutes
    val theme = p?.theme ?: ThemeChoice.DARK

    fun date(): LocalDate? = runCatching { LocalDate.of(year.trim().toInt(), month.trim().toInt(), day.trim().toInt()) }.getOrNull()
    fun dateError(): String? {
        if (day.isBlank() || month.isBlank() || year.length < 4) return null
        val d = date() ?: return "That date doesn't exist. Check the day and month."
        if (d.year < 1900) return "Please enter a date from 1900 onwards."
        if (d.isAfter(LocalDate.now())) return "That date is in the future."
        return null
    }
    fun time(): LocalTime? {
        val h = hour.trim().toIntOrNull() ?: return null
        val m = minute.trim().toIntOrNull() ?: return null
        if (h !in 1..12 || m !in 0..59) return null
        return LocalTime.of((h % 12) + if (pm) 12 else 0, m)
    }
    fun timeError(): String? = if (hour.isNotBlank() && minute.isNotBlank() && time() == null) "Use hours 1–12 and minutes 0–59." else null

    fun build(): Profile? {
        val d = date() ?: return null
        val st = status ?: return null
        val bp = birthPlace ?: return null
        val np = if (sameAsBirth) bp else nowPlace ?: return null
        val t = if (st == BirthTimeStatus.UNKNOWN) null else time() ?: return null
        return runCatching {
            Profile(name.trim(), d, t, st, if (st == BirthTimeStatus.APPROXIMATE) window else 0, bp, offset, np, language, theme).also { it.birthInput() }
        }.getOrNull()
    }
}

@Composable
fun OnboardingFlow(initial: Profile?, onDone: (Profile) -> Unit, onCancel: (() -> Unit)? = null) {
    val draft = remember { Draft(initial) }
    val steps = remember { if (initial == null) Step.entries.toList() else Step.entries.drop(1) }
    var i by remember { mutableIntStateOf(0) }
    val step = steps[i]
    val c = Astra.colors

    fun back() { if (i > 0) i-- else onCancel?.invoke() }
    BackHandler(enabled = i > 0 || onCancel != null) { back() }

    Box(Modifier.fillMaxSize().background(c.background)) {
        if (step == Step.WELCOME) { Welcome { i++ }; return@Box }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (i > 0 || onCancel != null) IconButton(onClick = { back() }) { AIcon(R.drawable.ic_chevron_left, "Back") } else Spacer(Modifier.size(48.dp))
                val visible = steps.filter { it != Step.WELCOME }
                LinearProgressIndicator(
                    progress = { (visible.indexOf(step) + 1f) / visible.size },
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = c.accent, trackColor = c.border
                )
                Text("${visible.indexOf(step) + 1} of ${visible.size}", style = Astra.type.meta, color = c.textSecondary, modifier = Modifier.padding(end = 12.dp))
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (step) {
                    Step.NAME -> NameStep(draft)
                    Step.DATE -> DateStep(draft)
                    Step.TIME -> TimeStep(draft)
                    Step.PLACE -> { Title("Where were you born?", "Your birthplace sets the time zone and the rising sign. Search works offline."); PlaceSearch(draft.birthPlace, { draft.birthPlace = it }) }
                    Step.NOW -> NowStep(draft)
                    Step.LANGUAGE -> LanguageStep(draft)
                    Step.WELCOME -> {}
                }
            }
            val valid = when (step) {
                Step.NAME -> true
                Step.DATE -> draft.date() != null && draft.dateError() == null
                Step.TIME -> draft.status == BirthTimeStatus.UNKNOWN || (draft.status != null && draft.time() != null)
                Step.PLACE -> draft.birthPlace != null
                Step.NOW -> draft.sameAsBirth || draft.nowPlace != null
                Step.LANGUAGE -> draft.build() != null
                Step.WELCOME -> true
            }
            val last = i == steps.lastIndex
            PrimaryButton(
                if (last) (if (initial == null) "Show my chart" else "Save changes") else "Continue",
                onClick = { if (last) draft.build()?.let(onDone) else i++ },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().padding(20.dp).testTag("continue")
            )
        }
    }
}

@Composable
private fun Title(title: String, sub: String) {
    Text(title, style = Astra.type.title, color = Astra.colors.text)
    Text(sub, style = Astra.type.body, color = Astra.colors.textSecondary)
}

@Composable
private fun Welcome(onStart: () -> Unit) {
    val c = Astra.colors
    Box(Modifier.fillMaxSize().background(c.background)) {
        Starfield(Modifier.fillMaxSize(), count = 90)
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(132.dp))
            Text("ASTRA", style = Astra.type.display, color = c.accent)
            Spacer(Modifier.height(8.dp))
            Text("Tell your story once.", style = Astra.type.heading, color = c.text, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(
                "Your Vedic chart, the Graha Tracker and today's panchang, calculated on your phone.",
                style = Astra.type.body, color = c.textSecondary, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(40.dp))
            PrimaryButton("Get started", onStart, Modifier.fillMaxWidth().testTag("getStarted"))
            Spacer(Modifier.height(12.dp))
            Text("No account needed to start.", style = Astra.type.meta, color = c.textSecondary)
        }
    }
}

@Composable
private fun NameStep(d: Draft) {
    Title("What should we call you?", "Optional. Used only to greet you.")
    AField(d.name, { d.name = it.take(40) }, "Your name", tag = "name")
}

@Composable
private fun DateStep(d: Draft) {
    Title("When were you born?", "Your date of birth sets the planets' positions in your chart.")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AField(d.day, { d.day = it.filter(Char::isDigit).take(2) }, "Day", Modifier.weight(1f), KeyboardType.Number, placeholder = "DD", tag = "day")
        AField(d.month, { d.month = it.filter(Char::isDigit).take(2) }, "Month", Modifier.weight(1f), KeyboardType.Number, placeholder = "MM", tag = "month")
        AField(d.year, { d.year = it.filter(Char::isDigit).take(4) }, "Year", Modifier.weight(1.4f), KeyboardType.Number, placeholder = "YYYY", tag = "year")
    }
    d.dateError()?.let { Text(it, style = Astra.type.label, color = Astra.colors.error) }
}

@Composable
private fun TimeStep(d: Draft) {
    Title("What time were you born?", "Birth time sets your Lagna (rising sign) and houses. If you don't know it exactly, ASTRA will only show what it can calculate reliably.")
    ChoiceChips(
        listOf(BirthTimeStatus.KNOWN to "I know the exact time", BirthTimeStatus.APPROXIMATE to "I know roughly", BirthTimeStatus.UNKNOWN to "I don't know"),
        d.status, { d.status = it }
    )
    if (d.status == BirthTimeStatus.KNOWN || d.status == BirthTimeStatus.APPROXIMATE) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AField(d.hour, { d.hour = it.filter(Char::isDigit).take(2) }, "Hour", Modifier.weight(1f), KeyboardType.Number, placeholder = "1–12", tag = "hour")
            AField(d.minute, { d.minute = it.filter(Char::isDigit).take(2) }, "Minute", Modifier.weight(1f), KeyboardType.Number, placeholder = "00", tag = "minute")
            Segmented(listOf("AM", "PM"), if (d.pm) 1 else 0, { d.pm = it == 1 }, Modifier.weight(1.2f))
        }
        d.timeError()?.let { Text(it, style = Astra.type.label, color = Astra.colors.error) }
    }
    if (d.status == BirthTimeStatus.APPROXIMATE) {
        Text("How sure are you?", style = Astra.type.bodyStrong, color = Astra.colors.text)
        ChoiceChips(listOf(30 to "Within 30 minutes", 60 to "Within an hour", 120 to "Within 2 hours"), d.window, { d.window = it })
    }
    if (d.status == BirthTimeStatus.UNKNOWN) {
        Notice("That's fine. Your Lagna and houses won't be shown, and your chart will start from your Moon sign. The tracker, dasha and panchang still work.")
    }
}

@Composable
private fun NowStep(d: Draft) {
    Title("Where do you live now?", "Used for today's panchang: sunrise, tithi and Rahu Kaal.")
    ChoiceChips(listOf(true to "Same as my birthplace", false to "Somewhere else"), d.sameAsBirth, { d.sameAsBirth = it })
    if (!d.sameAsBirth) PlaceSearch(d.nowPlace, { d.nowPlace = it }, label = "Town or city you live in")
}

@Composable
private fun LanguageStep(d: Draft) {
    Title("Which language should ASTRA reply in?", "For written readings and answers when Ask ASTRA arrives. The app itself is in English for now.")
    ChoiceChips(ReplyLanguage.entries.map { it to it.label }, d.language, { d.language = it })
    if (d.build() == null) Notice("Something earlier needs another look. Go back and check your date, time and places.")
}

