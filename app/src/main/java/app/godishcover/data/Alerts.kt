package app.godishcover.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import app.godishcover.BuildConfig
import app.godishcover.MainActivity
import app.godishcover.R
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Alerts are push messages sent to topics by the server when the feed gets a new entry:
 *   near_place_<gh4> a new restaurant in that area
 *   near_menu_<gh4>  new dishes at a place in that area
 *   place_<id>       new dishes at a saved restaurant
 * The phone follows the topics that match its Settings and area.
 */
object Alerts {
    const val CHANNEL = "whats_new"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "What's new nearby", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    /** Follows the right topics and drops the ones that no longer apply. */
    fun sync(prefs: Prefs, center: LatLng?, savedPlaces: List<String>) {
        if (!BuildConfig.HAS_FIREBASE) return
        val s = prefs.settings.value
        val want = mutableSetOf<String>()
        if (center != null) {
            val cells = Geo.nearbyCells(center)
            if (s.alertsNewPlaces) cells.forEach { want += "near_place_$it" }
            if (s.alertsNewMenus) cells.forEach { want += "near_menu_$it" }
        }
        if (s.alertsSaved) savedPlaces.take(50).forEach { want += "place_$it" }
        val had = prefs.getString("topics")?.split(',')?.filter { it.isNotBlank() }?.toSet().orEmpty()
        runCatching {
            val fm = FirebaseMessaging.getInstance()
            (had - want).forEach { fm.unsubscribeFromTopic(it) }
            (want - had).forEach { fm.subscribeToTopic(it) }
            prefs.putString("topics", want.joinToString(","))
        }
    }
}

class AlertService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"] ?: ""
        Alerts.ensureChannel(this)
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("open", "feed"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(this, Alerts.CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching {
            getSystemService(NotificationManager::class.java).notify((System.currentTimeMillis() % 100000).toInt(), n)
        }
    }

    override fun onNewToken(token: String) {}
}
