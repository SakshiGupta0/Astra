// ASTRA core — the user's profile and settings, with a plain key=value codec (stored on device in M2).
package app.astra.core

import java.io.StringReader
import java.io.StringWriter
import java.time.LocalDate
import java.time.LocalTime
import java.util.Properties

enum class ReplyLanguage(val label: String) { EN("English"), HINGLISH("Hinglish"), HI("हिन्दी") }
enum class ThemeChoice(val label: String) { SYSTEM("Match phone"), DARK("Dark"), LIGHT("Light") }

data class SavedPlace(val label: String, val latitude: Double, val longitude: Double, val zoneId: String) {
    companion object { fun of(p: Place) = SavedPlace(p.label, p.latitude, p.longitude, p.zoneId) }
}

data class Profile(
    val name: String,
    val birthDate: LocalDate,
    val birthTime: LocalTime?,
    val timeStatus: BirthTimeStatus,
    val windowMinutes: Int,
    val birthPlace: SavedPlace,
    val utcOffsetOverrideMinutes: Int?,
    val currentPlace: SavedPlace,
    val language: ReplyLanguage = ReplyLanguage.EN,
    val theme: ThemeChoice = ThemeChoice.DARK
) {
    fun birthInput() = BirthInput(
        date = birthDate, time = birthTime, timeStatus = timeStatus,
        windowMinutes = if (timeStatus == BirthTimeStatus.APPROXIMATE) windowMinutes else 0,
        latitude = birthPlace.latitude, longitude = birthPlace.longitude, zoneId = birthPlace.zoneId,
        utcOffsetOverrideMinutes = utcOffsetOverrideMinutes, placeName = birthPlace.label
    )
}

object ProfileCodec {
    const val VERSION = "1"

    fun encode(p: Profile): String {
        val props = Properties()
        fun put(k: String, v: Any?) { if (v != null) props.setProperty(k, v.toString()) }
        put("v", VERSION); put("name", p.name); put("birthDate", p.birthDate); put("birthTime", p.birthTime)
        put("timeStatus", p.timeStatus.name); put("windowMinutes", p.windowMinutes)
        put("birth.label", p.birthPlace.label); put("birth.lat", p.birthPlace.latitude); put("birth.lon", p.birthPlace.longitude); put("birth.zone", p.birthPlace.zoneId)
        put("utcOffsetOverrideMinutes", p.utcOffsetOverrideMinutes)
        put("now.label", p.currentPlace.label); put("now.lat", p.currentPlace.latitude); put("now.lon", p.currentPlace.longitude); put("now.zone", p.currentPlace.zoneId)
        put("language", p.language.name); put("theme", p.theme.name)
        val w = StringWriter(); props.store(w, "ASTRA profile"); return w.toString()
    }

    /** Returns null when the text is missing required fields (the app then shows onboarding). */
    fun decode(text: String): Profile? = try {
        val p = Properties().apply { load(StringReader(text)) }
        fun s(k: String) = p.getProperty(k)
        fun place(prefix: String) = SavedPlace(s("$prefix.label")!!, s("$prefix.lat")!!.toDouble(), s("$prefix.lon")!!.toDouble(), s("$prefix.zone")!!)
        val status = BirthTimeStatus.valueOf(s("timeStatus")!!)
        Profile(
            name = s("name") ?: "",
            birthDate = LocalDate.parse(s("birthDate")!!),
            birthTime = s("birthTime")?.let { LocalTime.parse(it) },
            timeStatus = status,
            windowMinutes = s("windowMinutes")?.toInt() ?: 0,
            birthPlace = place("birth"),
            utcOffsetOverrideMinutes = s("utcOffsetOverrideMinutes")?.toInt(),
            currentPlace = place("now"),
            language = s("language")?.let { runCatching { ReplyLanguage.valueOf(it) }.getOrNull() } ?: ReplyLanguage.EN,
            theme = s("theme")?.let { runCatching { ThemeChoice.valueOf(it) }.getOrNull() } ?: ThemeChoice.DARK
        ).also { it.birthInput() } // validates
    } catch (e: Exception) { null }
}
