// ASTRA core — offline place search over the bundled GeoNames list (CC BY 4.0).
package app.astra.core

import java.text.Normalizer
import java.util.Locale

data class Place(
    val name: String,
    val admin: String,
    val countryCode: String,
    val latitude: Double,
    val longitude: Double,
    val zoneId: String,
    val population: Int,
    val alternateNames: List<String> = emptyList()
) {
    val country: String get() = Locale("", countryCode).getDisplayCountry(Locale.ENGLISH).ifBlank { countryCode }
    /** "Bengaluru, Karnataka, India" */
    val label: String get() = listOf(name, admin, country).filter { it.isNotBlank() }.distinct().joinToString(", ")
}

object PlaceCodec {
    /** Parses the bundled TSV (see tools/places/build_places.py). Bad lines are skipped. */
    fun parse(lines: Sequence<String>): List<Place> = lines.mapNotNull { line ->
        val p = line.split('\t')
        if (p.size < 7) return@mapNotNull null
        val lat = p[3].toDoubleOrNull() ?: return@mapNotNull null
        val lon = p[4].toDoubleOrNull() ?: return@mapNotNull null
        Place(p[0], p[1], p[2], lat, lon, p[5], p[6].toIntOrNull() ?: 0,
            if (p.size > 7 && p[7].isNotBlank()) p[7].split('|') else emptyList())
    }.toList()
}

class PlaceIndex(private val places: List<Place>, private val homeCountry: String = "IN") {
    private class Entry(val place: Place, val keys: List<String>, val adminKey: String, val countryKey: String)
    private val entries = places.map { pl ->
        Entry(pl, (listOf(pl.name) + pl.alternateNames).map(::norm).distinct(), norm(pl.admin), norm(pl.country) + " " + norm(pl.countryCode))
    }

    val size: Int get() = places.size

    /**
     * Ranked search. "pune", "bangalore", "new york", "Pune, Maharashtra", "springfield, us".
     * Ranking: exact name > prefix > word prefix > contains; then home-country boost; then population.
     */
    fun search(query: String, limit: Int = 8): List<Place> {
        val parts = query.split(',').map(::norm).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return emptyList()
        val q = parts.first()
        val qualifiers = parts.drop(1)
        if (q.length < 2) return emptyList()
        val scored = ArrayList<Pair<Entry, Double>>()
        for (e in entries) {
            var best = 0
            for (k in e.keys) {
                val s = when {
                    k == q -> 4
                    k.startsWith(q) -> 3
                    k.split(' ').any { it.startsWith(q) } -> 2
                    q.length >= 4 && k.contains(q) -> 1
                    else -> 0
                }
                if (s > best) best = s
            }
            if (best == 0) continue
            if (qualifiers.isNotEmpty() && !qualifiers.all { e.adminKey.contains(it) || e.countryKey.contains(it) }) continue
            val home = if (e.place.countryCode == homeCountry) 1.0 else 0.0
            val score = best * 100.0 + home * 15.0 + Math.log10(e.place.population.coerceAtLeast(1).toDouble()) * 6.0
            scored += e to score
        }
        return scored.sortedByDescending { it.second }.take(limit).map { it.first.place }
    }

    /** Nearest bundled place (for "use my coordinates" and for labelling a manual entry). */
    fun nearest(latitude: Double, longitude: Double): Place? = places.minByOrNull {
        val dLat = it.latitude - latitude
        val dLon = (it.longitude - longitude) * Math.cos(Math.toRadians(latitude))
        dLat * dLat + dLon * dLon
    }

    companion object {
        fun norm(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
