package app.godishcover.data

import android.content.Context
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
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

    /** True when Google Play installed this copy, so Play handles its updates. */
    fun installedFromPlay(ctx: Context): Boolean {
        val pm = ctx.packageManager
        val src = runCatching {
            if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(ctx.packageName).installingPackageName
            else @Suppress("DEPRECATION") pm.getInstallerPackageName(ctx.packageName)
        }.getOrNull()
        return src == "com.android.vending"
    }

    /**
     * Installs [file] over this app through PackageInstaller. On Android 12 and newer an app that
     * updates itself can skip the confirmation screen; if Android still wants a tap, InstallReceiver
     * shows it (or a notification when the app is in the background).
     */
    suspend fun installQuietly(ctx: Context, file: File) = withContext(Dispatchers.IO) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("dishcover.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val done = PendingIntent.getBroadcast(ctx, id, Intent(ctx, InstallReceiver::class.java), flags)
            session.commit(done.intentSender)
        }
    }
}
