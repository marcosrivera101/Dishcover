package app.godishcover.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.godishcover.BuildConfig
import app.godishcover.DishcoverApp
import app.godishcover.R
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Keeps Dishcover up to date on its own, like apps that update themselves in the background.
 * A worker checks for a newer build every few hours and downloads it. Once you leave the app,
 * it hands the file to Android's installer with "no confirmation needed" (Android 12 and newer let
 * an app update itself this way once "Install unknown apps" is allowed for it). Older phones, or a
 * phone that insists on asking, get a quiet "Tap to finish updating" notification instead.
 * Builds installed from Google Play are left to Play, which updates them itself.
 */
object AutoUpdate {
    /** True while a Dishcover screen is on show, so an install never closes the app under you. */
    @Volatile var visible = false

    private const val CHANNEL = "updates"
    private const val NOTE_ID = 4101

    fun allowed(ctx: Context): Boolean {
        val app = ctx.applicationContext as DishcoverApp
        return BuildConfig.HAS_FIREBASE && app.prefs.settings.value.autoCheckUpdates && !Updater.installedFromPlay(ctx)
    }

    /** True when updates can install with no taps at all. */
    fun silent(ctx: Context) = allowed(ctx) && !Updater.needsInstallPermission(ctx)

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedule(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        if (!allowed(ctx)) { wm.cancelUniqueWork("auto-update"); return }
        wm.enqueueUniquePeriodicWork(
            "auto-update", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS).setConstraints(network).build(),
        )
    }

    /** Checks now; if a downloaded update is waiting and the app is in the background, installs it. */
    fun runNow(ctx: Context) {
        if (!allowed(ctx)) return
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            "auto-update-now", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<UpdateWorker>().setConstraints(network).build(),
        )
    }

    fun notifyTapToFinish(ctx: Context, confirm: Intent) {
        if (Build.VERSION.SDK_INT >= 26) {
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val tap = PendingIntent.getActivity(ctx, 0, confirm, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val note = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("A new Dishcover is ready")
            .setContentText("Tap to finish updating.")
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(NOTE_ID, note) }
    }
}

class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!AutoUpdate.allowed(ctx)) return Result.success()
        val app = ctx as DishcoverApp
        return try {
            val r = app.repo.latestRelease()
            val dir = File(ctx.cacheDir, "updates")
            if (r == null || r.versionCode <= BuildConfig.VERSION_CODE || !r.apkUrl.startsWith("https://")) {
                dir.listFiles { f -> f.name.startsWith("ready-") }?.forEach { it.delete() }
                return Result.success()
            }
            val ready = File(dir, "ready-${r.versionCode}.apk")
            if (!ready.exists()) {
                val f = Updater.download(ctx, r.apkUrl) { }
                if (f.length() < 1_000_000) { f.delete(); return Result.retry() }
                dir.listFiles { x -> x.name.startsWith("ready-") }?.forEach { it.delete() }
                f.renameTo(ready)
            }
            if (!AutoUpdate.visible && !Updater.needsInstallPermission(ctx)) Updater.installQuietly(ctx, ready)
            Result.success()
        } catch (e: Exception) {
            Log.w("AutoUpdate", "update check failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }
}

/** Hears back from Android's installer. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (AutoUpdate.visible) runCatching { ctx.startActivity(confirm) } else AutoUpdate.notifyTapToFinish(ctx, confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> Log.w("AutoUpdate", "install failed ($status): ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
        }
    }
}
