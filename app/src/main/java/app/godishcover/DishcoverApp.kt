package app.godishcover

import android.app.Application
import app.godishcover.data.CrashLog
import app.godishcover.data.DemoRepo
import app.godishcover.data.Prefs
import app.godishcover.data.FirebaseRepo
import app.godishcover.data.PlacesSource
import app.godishcover.data.Repo

class DishcoverApp : Application() {
    lateinit var repo: Repo
        private set
    lateinit var places: PlacesSource
        private set
    lateinit var prefs: Prefs
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        prefs = Prefs(this)
        // Firebase is configured from google-services.json at build time; without it the app runs in demo mode.
        repo = if (BuildConfig.HAS_FIREBASE) FirebaseRepo() else DemoRepo(this)
        places = PlacesSource(this, BuildConfig.MAPS_API_KEY)
        runCatching { app.godishcover.data.AutoUpdate.schedule(this) }
    }
}
