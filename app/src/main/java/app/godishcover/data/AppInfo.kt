package app.godishcover.data

import android.content.Context
import android.os.Build
import app.godishcover.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** Where ideas and error reports go. */
const val FEEDBACK_EMAIL = "agentjarvis@mail.grokbot.com"

data class ChangeEntry(val versionCode: Int, val version: String, val date: String, val items: List<String>)

/** What changed in each build. The newest entry is shown once after an update. */
object Changelog {
    val ALL = listOf(
        ChangeEntry(
            4, "0.4", "November 2026",
            listOf(
                "Dishcover is live: sign in with Google and your ratings, reviews and posts are saved.",
                "Post photos to the feed, and add a restaurant's menu from a photo or a link.",
                "Alerts when something new is posted near you.",
                "Updates install from inside the app: tap Update, and Dishcover downloads the new version and opens the installer.",
                "Search has a Restaurants side: every place near you, filtered by food type, sorted by distance, rating or menu size, with a Favorites filter.",
                "Suggestions show each chain once, at its closest location.",
            ),
        ),
        ChangeEntry(
            3, "0.3.0", "November 2026",
            listOf(
                "Real Hartford restaurants and their menus, read from each restaurant's own website.",
                "A daily menu check: when a restaurant adds dishes, they show up under New on menus in the feed.",
                "54 kinds of food to pick from, grouped into dishes, cuisines, and sweets and drinks.",
                "Many more allergies and want-to-try dishes, and you can type your own on any list.",
                "More moods to choose from, with a More button for every kind of food.",
            ),
        ),
        ChangeEntry(
            2, "0.2.0", "November 2026",
            listOf(
                "New Feed tab: what's new on menus near you, new places, shared reviews, posts, trending dishes and restaurant news.",
                "Share where you went and what you ate, with a photo, and share your reviews to the feed.",
                "Review a whole restaurant, not just single dishes.",
                "Healthy picks, plus vegetarian, vegan, gluten-free and spicy tags on dishes.",
                "Save restaurants and get alerts when they add dishes.",
                "Alerts for new places and new menus near you.",
                "Choose how far \"nearby\" reaches, from 1 to 50 miles.",
                "Light and dark themes, six accent colors, larger text, miles or kilometers.",
                "Startup animation, a short intro, and smoother motion everywhere.",
                "Update checks, this change log, troubleshooting, and one-tap idea and error reports.",
            ),
        ),
        ChangeEntry(
            1, "0.1.0", "November 2026",
            listOf(
                "First build: sign in, taste interview, what-to-eat suggestions, search, restaurant and dish pages, ratings, and adding menus by photo, link or typing.",
            ),
        ),
    )
}

/** Keeps the last crash on the phone so the user can choose to email it. Nothing is sent automatically. */
object CrashLog {
    private fun file(ctx: Context) = File(ctx.filesDir, "last-crash.txt")

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                file(app).writeText(
                    "When: ${java.util.Date()}\n${deviceInfo()}\n\n" + sw.toString().take(6000),
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(ctx: Context): String? = file(ctx).takeIf { it.exists() }?.readText()
    fun clear(ctx: Context) { file(ctx).delete() }

    fun deviceInfo(): String =
        "Dishcover ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})" +
            (if (BuildConfig.HAS_FIREBASE) "" else " demo") +
            "\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
}
