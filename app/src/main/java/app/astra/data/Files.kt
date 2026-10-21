package app.astra.data

import java.io.File

/** Write to a temp file, then rename, so a crash never leaves a half-written file. */
internal fun File.writeAtomically(text: String) {
    val tmp = File(parentFile, "$name.tmp")
    tmp.writeText(text)
    if (!tmp.renameTo(this)) { delete(); check(tmp.renameTo(this)) { "Couldn't save $name" } }
}
