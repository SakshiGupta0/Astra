// Account in Me: back up with Google (links the anonymous account so data survives a new phone).
package app.astra.ui.me

import android.app.Activity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import app.astra.App
import app.astra.ui.components.AstraCard
import app.astra.ui.components.Notice
import app.astra.ui.components.SecondaryButton
import app.astra.ui.components.SectionTitle
import app.astra.ui.theme.Astra
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

@Composable
fun AccountSection(app: App) {
    val c = Astra.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    SectionTitle("Backup")
    AstraCard {
        if (!app.cloud.firebaseReady) {
            Text("Sign-in and backup are being set up. For now everything stays on this phone.", style = Astra.type.label, color = c.textSecondary)
            return@AstraCard
        }
        if (app.cloud.backedUpWithGoogle) {
            Text("Backed up with Google${app.cloud.accountLabel?.let { " ($it)" } ?: ""}.", style = Astra.type.bodyStrong, color = c.text)
            Text("Sign in with the same account on a new phone to keep your history.", style = Astra.type.meta, color = c.textSecondary)
        } else {
            Text("Not backed up yet", style = Astra.type.bodyStrong, color = c.text)
            Text("Back up with Google so your story and saved readings can move to a new phone.", style = Astra.type.meta, color = c.textSecondary)
            SecondaryButton(if (busy) "Opening Google…" else "Back up with Google", {
                if (busy) return@SecondaryButton
                val idRes = ctx.resources.getIdentifier("default_web_client_id", "string", ctx.packageName)
                if (idRes == 0) { message = "Google sign-in isn't set up in this build yet."; return@SecondaryButton }
                busy = true
                scope.launch {
                    try {
                        val option = GetSignInWithGoogleOption.Builder(ctx.getString(idRes)).build()
                        val cred = CredentialManager.create(ctx).getCredential(ctx as Activity, GetCredentialRequest.Builder().addCredentialOption(option).build()).credential
                        if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                            val g = GoogleIdTokenCredential.createFrom(cred.data)
                            val (who, switched) = app.cloud.linkGoogle(GoogleAuthProvider.getCredential(g.idToken, null))
                            val restored = switched && app.restoreIfEmpty()
                            app.syncStory()
                            message = if (restored) "Signed in as $who and brought back your story and saved readings." else "Backed up with $who."
                        } else message = "Google sent an unexpected response. Please try again."
                    } catch (e: GetCredentialCancellationException) {
                    } catch (e: NoCredentialException) {
                        message = "No Google account on this phone. Add one in Android Settings → Accounts, then try again."
                    } catch (e: Exception) {
                        message = "Backup didn't work: ${e.message ?: "unknown error"}"
                    } finally { busy = false }
                }
            }, Modifier.fillMaxWidth())
        }
        (message ?: app.syncNote)?.let { Notice(it) }
    }
}
