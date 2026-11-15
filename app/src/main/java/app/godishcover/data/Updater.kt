package app.godishcover.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Downloads a new build inside the app and hands it to Android's installer. */
object Updater {
    private fun apkFile(ctx: Context) = File(ctx.cacheDir, "updates/dishcover.apk")

    /** Downloads [url], reporting progress from 0 to 1. Returns the saved file. */
    suspend fun download(ctx: Context, url: String, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val out = apkFile(ctx).apply { parentFile?.mkdirs(); delete() }
        var link = url
        var conn: HttpURLConnection
        var hops = 0
        while (true) {
            conn = (URL(link).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            val code = conn.responseCode
            if (code in 300..399 && hops++ < 5) {
                link = URL(URL(link), conn.getHeaderField("Location")).toString()
                conn.disconnect()
                continue
            }
            if (code != 200) error("The download failed (error $code).")
            break
        }
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            out.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    done += n
                    if (total > 0) progress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        conn.disconnect()
        out
    }

    /** True when the phone still needs the user to allow installs from Dishcover. */
    fun needsInstallPermission(ctx: Context) =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun install(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
