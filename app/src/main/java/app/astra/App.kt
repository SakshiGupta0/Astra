// ASTRA v0.2.0
package app.astra

import android.app.Application
import app.astra.data.PlaceRepository
import app.astra.data.ProfileRepository
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch

@OptIn(DelicateCoroutinesApi::class)
class App : Application() {
    lateinit var profiles: ProfileRepository; private set
    lateinit var places: PlaceRepository; private set

    override fun onCreate() {
        super.onCreate()
        profiles = ProfileRepository(filesDir)
        places = PlaceRepository(this)
        // Warm the offline place list in the background so search is instant when onboarding reaches it.
        if (profiles.profile == null) kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Default) { runCatching { places.index() } }
    }
}
