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
