// Privacy & data in Me: usage stats on/off, the privacy notice, feedback, and deleting the account (PRD F-06, DPDP).
package app.astra.ui.me

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.astra.App
import app.astra.BuildConfig
import app.astra.R
import app.astra.data.CloudError
import app.astra.ui.components.AIcon
import app.astra.ui.components.AstraCard
import app.astra.ui.components.Notice
import app.astra.ui.components.SecondaryButton
import app.astra.ui.components.SectionTitle
import app.astra.ui.components.TextLink
import app.astra.ui.theme.Astra
import kotlinx.coroutines.launch

const val CONTACT_FALLBACK = "us using Send feedback in the app (Me → Send feedback)"

@Composable
fun PrivacySection(app: App, onOpenNotice: () -> Unit) {
    val c = Astra.colors
    val scope = rememberCoroutineScope()
    var feedback by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    SectionTitle("Privacy & data")
    AstraCard(Modifier.testTag("privacyCard")) {
        ToggleRow("Share anonymous usage stats", "Which features are used, never your questions, story or birth details. Helps decide what to improve.",
            app.analytics.enabled, { app.analytics.setEnabled(it) }, tag = "analyticsSwitch")
        TextLink("Read the privacy notice", onOpenNotice, Modifier.testTag("openPrivacy"))
        TextLink("Send feedback", { feedback = true }, Modifier.testTag("sendFeedback"))
        TextLink(if (deleting) "Deleting…" else "Delete my account", { if (!deleting) confirmDelete = true }, Modifier.testTag("deleteAccount"))
        message?.let { Notice(it) }
    }
    if (feedback) FeedbackDialog(app, onDone = { sent -> feedback = false; if (sent) message = "Thanks — your feedback was sent." })
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete your account?") },
        text = { Text("This permanently deletes your story, chart copy, backups, usage stats and feedback from ASTRA's servers, signs you out, and clears the app on this phone. It can't be undone.") },
        confirmButton = { TextButton({
            confirmDelete = false; deleting = true
            scope.launch {
                try { app.deleteAccount() }
                catch (e: CloudError) { message = e.message + " Your data on this phone was not changed." }
                catch (e: Exception) { message = "Couldn't delete your account just now (${e.message ?: "unknown error"}). Please try again." }
                finally { deleting = false }
            }
        }, modifier = Modifier.testTag("confirmDeleteAccount")) { Text("Delete") } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } }
    )
}

@Composable
private fun FeedbackDialog(app: App, onDone: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { onDone(false) },
        title = { Text("Send feedback") },
        text = {
            Column {
                Text("What's working, what isn't, what you'd like. Please don't include birth details or anything private.", style = Astra.type.label)
                OutlinedTextField(text, { text = it.take(1000) }, Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(top = 8.dp).testTag("feedbackText"))
                error?.let { Text(it, style = Astra.type.meta, color = Astra.colors.error) }
            }
        },
        confirmButton = { TextButton({
            if (busy || text.isBlank()) return@TextButton
            busy = true
            scope.launch {
                try { app.cloud.sendFeedback(text.trim()); app.analytics.log("feedback_sent"); onDone(true) }
                catch (e: CloudError) { error = e.message }
                catch (e: Exception) { error = "Couldn't send just now. Please try again." }
                finally { busy = false }
            }
        }) { Text(if (busy) "Sending…" else "Send") } },
        dismissButton = { TextButton({ onDone(false) }) { Text("Cancel") } }
    )
}

/** The privacy notice, from the same text the server publishes at /privacy (docs/privacy/PRIVACY.md). */
@Composable
fun PrivacyNoticeScreen(onBack: () -> Unit) {
    val c = Astra.colors
    val ctx = LocalContext.current
    androidx.activity.compose.BackHandler(onBack = onBack)
    val md = remember {
        runCatching { ctx.assets.open("PRIVACY.md").bufferedReader().use { it.readText() } }.getOrDefault("The privacy notice couldn't be loaded.")
            .replace("{{CONTACT}}", BuildConfig.CONTACT_EMAIL.ifBlank { CONTACT_FALLBACK })
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().testTag("privacyNotice")) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) { AIcon(R.drawable.ic_chevron_left, "Back") }
            Text("Privacy notice", style = Astra.type.title, color = c.text)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            md.lines().forEach { raw ->
                val line = raw.trim()
                when {
                    line.isEmpty() -> Box(Modifier.size(8.dp))
                    line.startsWith("# ") -> {}
                    line.startsWith("## ") -> Text(line.removePrefix("## "), style = Astra.type.heading, color = c.accent, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    line.startsWith("- ") -> Row(Modifier.padding(vertical = 3.dp)) {
                        Text("•  ", style = Astra.type.body, color = c.textSecondary)
                        Text(bold(line.removePrefix("- ")), style = Astra.type.body, color = c.text)
                    }
                    else -> Text(bold(line), style = Astra.type.body, color = c.text)
                }
            }
        }
    }
}

private fun bold(s: String) = buildAnnotatedString {
    var rest = s
    while (true) {
        val a = rest.indexOf("**"); if (a < 0) break
        val b = rest.indexOf("**", a + 2); if (b < 0) break
        append(rest.substring(0, a)); withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(rest.substring(a + 2, b)) }
        rest = rest.substring(b + 2)
    }
    append(rest)
}
