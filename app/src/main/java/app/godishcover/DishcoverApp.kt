package app.godishcover

import android.app.Application
import app.godishcover.data.DemoRepo
import app.godishcover.data.FirebaseRepo
import app.godishcover.data.PlacesSource
import app.godishcover.data.Repo

class DishcoverApp : Application() {
    lateinit var repo: Repo
        private set
    lateinit var places: PlacesSource
        private set

    override fun onCreate() {
        super.onCreate()
        // Firebase is configured from google-services.json at build time; without it the app runs in demo mode.
        repo = if (BuildConfig.HAS_FIREBASE) FirebaseRepo() else DemoRepo()
        places = PlacesSource(this, BuildConfig.MAPS_API_KEY)
    }
}
