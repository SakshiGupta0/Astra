// Today's card: one draw per calendar day, kept on the phone (PRD F-05).
package app.astra.data

import app.astra.core.DrawCodec
import app.astra.core.Tarot
import app.astra.core.TarotDraw
import java.io.File
import java.time.LocalDate

class DrawRepository(dir: File) {
    private val file = File(dir, "daily_card.json")
    private val revealedFile = File(dir, "daily_card_seen.txt")

    // Plain field (not Compose state): read and written while a screen is being drawn.
    var daily: TarotDraw? = runCatching { if (file.exists()) DrawCodec.decode(file.readText()) else null }.getOrNull()
        private set

    /** The card for [day], drawing and saving it the first time it's asked for. */
    fun dailyFor(day: LocalDate): TarotDraw {
        val d = Tarot.daily(daily, day)
        if (d !== daily) { runCatching { file.writeAtomically(DrawCodec.encode(d)) }; daily = d }
        return d
    }

    /** Whether the user has turned today's card over yet. */
    fun revealed(day: LocalDate): Boolean = runCatching { revealedFile.readText() == day.toString() }.getOrDefault(false)
    fun markRevealed(day: LocalDate) { runCatching { revealedFile.writeText(day.toString()) } }

    fun clearFile() { file.delete(); revealedFile.delete(); daily = null }
}
