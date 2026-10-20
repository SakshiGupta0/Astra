package app.astra.data

import android.content.Context
import app.astra.core.PlaceCodec
import app.astra.core.PlaceIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Offline place list (GeoNames, CC BY 4.0) bundled as an asset; loaded once, off the main thread. */
class PlaceRepository(private val context: Context) {
    private val lock = Mutex()
    @Volatile private var index: PlaceIndex? = null

    suspend fun index(): PlaceIndex = index ?: lock.withLock {
        index ?: withContext(Dispatchers.IO) {
            context.assets.open("places.tsv").bufferedReader().useLines { PlaceIndex(PlaceCodec.parse(it)) }
        }.also { index = it }
    }
}
