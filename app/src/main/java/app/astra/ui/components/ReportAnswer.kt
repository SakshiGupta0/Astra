// "Report" on every AI-written answer (Google Play AI-generated content policy): the user can flag an answer as
// harmful, frightening or wrong. The report sends the answer text and the reason to ASTRA, nothing else.
package app.astra.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.astra.App
import app.astra.data.CloudError
import app.astra.ui.theme.Astra
import kotlinx.coroutines.launch

val REPORT_REASONS = listOf(
    "harmful" to "Harmful, offensive or inappropriate",
    "frightening" to "Frightening or fatalistic",
    "wrong" to "Wrong or misleading",
    "other" to "Something else",
)

/** A small "Report" link; [key] identifies the answer so the "Reported" state survives recomposition. */
@Composable
fun ReportLink(app: App, mode: String, answer: String, key: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    var done by rememberSaveable(key) { mutableStateOf(false) }
    if (done) { Text("Reported", style = Astra.type.label, color = Astra.colors.textSecondary, modifier = modifier.padding(horizontal = 8.dp)); return }
    TextLink("Report", { open = true }, modifier.testTag("reportAnswer"))
    if (open) ReportDialog(app, mode, answer) { sent -> open = false; if (sent) done = true }
}

@Composable
private fun ReportDialog(app: App, mode: String, answer: String, onDone: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { onDone(false) },
        title = { Text("Report this answer") },
        text = {
            Column {
                Text("This sends the answer and your reason to the ASTRA team so we can fix it. Your chart and story aren't included.",
                    style = Astra.type.label)
                REPORT_REASONS.forEach { (id, label) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).selectable(reason == id, role = Role.RadioButton) { reason = id }
                        .testTag("reason_$id"), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(reason == id, null)
                        Text(label, style = Astra.type.body, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                OutlinedTextField(note, { note = it.take(500) }, Modifier.fillMaxWidth().padding(top = 8.dp).testTag("reportNote"),
                    placeholder = { Text("Anything to add? (optional)") })
                error?.let { Text(it, style = Astra.type.meta, color = Astra.colors.error) }
            }
        },
        confirmButton = { TextButton({
            val r = reason ?: run { error = "Please choose a reason."; return@TextButton }
            if (busy) return@TextButton
            busy = true
            scope.launch {
                try { app.cloud.reportAnswer(mode, r, note.trim(), answer); app.analytics.log("answer_reported", "mode" to mode, "reason" to r); onDone(true) }
                catch (e: CloudError) { error = e.message }
                catch (e: Exception) { error = "Couldn't send just now. Please try again." }
                finally { busy = false }
            }
        }, modifier = Modifier.testTag("sendReport")) { Text(if (busy) "Sending…" else "Send report") } },
        dismissButton = { TextButton({ onDone(false) }) { Text("Cancel") } }
    )
}
