package app.astra.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.astra.core.Reading
import app.astra.core.ReadingCodec
import java.io.File

/** Saved readings (library), newest first, on the phone. */
class ReadingRepository(dir: File, private val onChange: () -> Unit = {}) {
    private val file = File(dir, "readings.json")
    var readings: List<Reading> by mutableStateOf(runCatching { if (file.exists()) ReadingCodec.decode(file.readText()) else emptyList() }.getOrDefault(emptyList()))
        private set

    fun save(r: Reading): Boolean = write(listOf(r) + readings.filterNot { it.id == r.id })
    fun delete(id: String): Boolean = write(readings.filterNot { it.id == id })
    fun clearFile() { file.delete(); readings = emptyList() }

    private fun write(next: List<Reading>): Boolean = runCatching { file.writeAtomically(ReadingCodec.encode(next)); readings = next }.isSuccess.also { if (it) onChange() }
}
