package app.astra.ui.profile

import app.astra.ui.Session
import app.astra.ui.me.StorySection
import app.astra.ui.me.NotifySection
import app.astra.ui.me.AccountSection
import app.astra.ui.me.PrivacySection
import app.astra.ui.me.PrivacyNoticeScreen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.astra.App
import app.astra.BuildConfig
import app.astra.core.BirthTimeStatus
import app.astra.core.CoreVersion
import app.astra.core.Profile
import app.astra.core.ReplyLanguage
import app.astra.core.ThemeChoice
import app.astra.ui.components.AField
import app.astra.ui.components.AstraCard
import app.astra.ui.components.ChoiceChips
import app.astra.ui.components.KeyValue
import app.astra.ui.components.Notice
import app.astra.ui.components.SecondaryButton
import app.astra.ui.components.SectionTitle
import app.astra.ui.components.Segmented
import app.astra.ui.components.TextLink
import app.astra.ui.theme.Astra
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment

@Composable
fun ProfileScreen(app: App, session: Session, onEdit: () -> Unit) {
    val p = session.profile
    val c = Astra.colors
    var confirmDelete by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(p.utcOffsetOverrideMinutes != null) }
    var notice by remember { mutableStateOf(false) }
    if (notice) { PrivacyNoticeScreen(onBack = { notice = false }); return }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(Modifier.widthIn(max = 760.dp).fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Me", style = Astra.type.hero, color = c.text)
        app.profiles.lastError?.let { Notice(it) }

        StorySection(app, session)
        NotifySection(app)
        AccountSection(app)
        PrivacySection(app, onOpenNotice = { notice = true })

        SectionTitle("Birth details")

        AstraCard {
            Text(p.name.ifBlank { "Your birth details" }, style = Astra.type.heading, color = c.text)
            KeyValue("Date of birth", p.birthDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)))
            KeyValue("Time of birth", when (p.timeStatus) {
                BirthTimeStatus.KNOWN -> p.birthTime!!.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)).lowercase()
                BirthTimeStatus.APPROXIMATE -> "around " + p.birthTime!!.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)).lowercase() + " (±${p.windowMinutes} min)"
                BirthTimeStatus.UNKNOWN -> "Not known"
            })
            KeyValue("Birthplace", p.birthPlace.label)
            KeyValue("Lives in", p.currentPlace.label)
            SecondaryButton("Edit birth details", onEdit, Modifier.fillMaxWidth().padding(top = 4.dp).testTag("editProfile"))
        }

        SectionTitle("Appearance")
        Segmented(ThemeChoice.entries.map { it.label }, p.theme.ordinal, { app.profiles.save(p.copy(theme = ThemeChoice.entries[it])) }, Modifier.fillMaxWidth())

        SectionTitle("Reply language")
        Text("For Ask ASTRA answers and your daily line. The app itself is in English for now.", style = Astra.type.meta, color = c.textSecondary)
        ChoiceChips(ReplyLanguage.entries.map { it to it.label }, p.language, { app.profiles.save(p.copy(language = it)) })

        SectionTitle("Advanced")
        if (!advanced) TextLink("My birth record uses a different clock time", { advanced = true })
        else OffsetEditor(app, p)

        SectionTitle("About")
        AstraCard {
            KeyValue("App version", BuildConfig.VERSION_NAME)
            KeyValue("Calculation", CoreVersion.CALC.substringBefore(":"))
            Text(if (app.backend.available) "Charts, the tracker, panchang and card draws are calculated on this phone. To answer your questions, ASTRA keeps a copy of your calculated chart, your card draws and the story items you allow in your own private space on the server; nothing is used to train AI models."
                else "Charts, the tracker and panchang are calculated on this phone. Your details are stored only on this phone.", style = Astra.type.meta, color = c.textSecondary)
            Text("Credits: Astronomy Engine by Don Cross (MIT) · Place data © GeoNames (CC BY 4.0) · Playfair Display, Inter and Cinzel (SIL Open Font Licence) · Lucide icons (ISC) · Classic tarot: Pamela Colman Smith, 1909 (public domain; scans from Wikimedia Commons) · Card back and oracle decks: ASTRA's own designs.",
                style = Astra.type.meta, color = c.textSecondary)
        }
        SecondaryButton("Delete everything from this phone", { confirmDelete = true }, Modifier.fillMaxWidth().padding(bottom = 24.dp))
    }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete everything?") },
        text = { Text("This removes your birth details, My Story, saved readings and settings from this phone. You can start again any time.") },
        confirmButton = { TextButton({ confirmDelete = false; app.deleteEverything() }) { Text("Delete") } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } }
    )
}

@Composable
private fun OffsetEditor(app: App, p: Profile) {
    val c = Astra.colors
    var text by remember { mutableStateOf(p.utcOffsetOverrideMinutes?.let { "%s%d:%02d".format(if (it < 0) "-" else "+", Math.abs(it) / 60, Math.abs(it) % 60) } ?: "") }
    val parsed = parseOffset(text)
    AstraCard {
        Text("Only change this if your birth record used a different time standard from today's (for example local or city time before 1906, or war time 1942–45).",
            style = Astra.type.meta, color = c.textSecondary)
        AField(text, { text = it.take(6) }, "UTC offset at birth, e.g. +5:30", keyboard = KeyboardType.Text,
            error = if (text.isNotBlank() && parsed == null) "Use a format like +5:30 or +5:21" else null)
        SecondaryButton(if (text.isBlank()) "Use the place's normal time" else "Save offset", {
            if (text.isNotBlank() && parsed == null) return@SecondaryButton
            app.profiles.save(p.copy(utcOffsetOverrideMinutes = if (text.isBlank()) null else parsed))
        }, Modifier.fillMaxWidth())
    }
}

private fun parseOffset(s: String): Int? {
    val m = Regex("^([+-])(\\d{1,2}):(\\d{2})$").find(s.trim()) ?: return null
    val h = m.groupValues[2].toInt(); val min = m.groupValues[3].toInt()
    if (h > 14 || min > 59) return null
    return (if (m.groupValues[1] == "-") -1 else 1) * (h * 60 + min)
}
