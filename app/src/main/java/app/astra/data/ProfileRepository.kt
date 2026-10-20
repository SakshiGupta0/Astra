package app.astra.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.astra.core.Profile
import app.astra.core.ProfileCodec
import java.io.File

/**
 * The profile lives in one small file on the device (M2; cloud sync arrives with sign-in in M3).
 * Writes go to a temp file first and are then renamed, so a crash never leaves a half-written profile.
 */
class ProfileRepository(dir: File) {
    private val file = File(dir, "profile.properties")
    private val tmp = File(dir, "profile.properties.tmp")

    var profile: Profile? by mutableStateOf(load())
        private set

    var lastError: String? by mutableStateOf(null)
        private set

    private fun load(): Profile? = runCatching { if (file.exists()) ProfileCodec.decode(file.readText()) else null }.getOrNull()

    fun save(p: Profile): Boolean = try {
        tmp.writeText(ProfileCodec.encode(p))
        if (!tmp.renameTo(file)) { file.delete(); check(tmp.renameTo(file)) { "rename failed" } }
        profile = p; lastError = null; true
    } catch (e: Exception) {
        lastError = "Couldn't save your details on this phone (${e.message}). Please try again."; false
    }

    fun clear() {
        file.delete(); tmp.delete(); profile = null
    }
}
