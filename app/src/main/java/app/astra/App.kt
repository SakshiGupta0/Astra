// ASTRA v0.2.0
package app.astra

import android.app.Application
import app.astra.data.PlaceRepository
import app.astra.data.ProfileRepository

class App : Application() {
    lateinit var profiles: ProfileRepository; private set
    lateinit var places: PlaceRepository; private set

    override fun onCreate() {
        super.onCreate()
        profiles = ProfileRepository(filesDir)
        places = PlaceRepository(this)
    }
}
