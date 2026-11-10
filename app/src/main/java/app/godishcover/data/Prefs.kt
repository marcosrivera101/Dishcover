package app.godishcover.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Everything the user can customize. Kept on the phone. */
data class Settings(
    /** "system", "light" or "dark". */
    val theme: String = "system",
    /** One of [Accents.ALL]. */
    val accent: String = "wine",
    /** How far "nearby" reaches, in miles (1 to 50). */
    val radiusMi: Int = 10,
    val useKm: Boolean = false,
    /** Feed entry types shown under "All". */
    val feedTypes: Set<String> = FeedType.ALL.toSet(),
    val showNews: Boolean = true,
    /** Tab the app opens on: "today" or "feed". */
    val startTab: String = "today",
    val haptics: Boolean = true,
    val reduceMotion: Boolean = false,
    val largeText: Boolean = false,
    val healthyFirst: Boolean = false,
    val alertsNewPlaces: Boolean = true,
    val alertsNewMenus: Boolean = true,
    val alertsSaved: Boolean = true,
    val autoCheckUpdates: Boolean = true,
    val introSeen: Boolean = false,
    /** Version whose "what's new" sheet was last shown. */
    val seenChangelogVersion: Int = 0,
    /** Newest feed entry the user has looked at (ms), for the "new" badge. */
    val feedSeenAt: Long = 0,
) {
    val radiusKm: Double get() = radiusMi * 1.609344
}

object Accents {
    /** name to (light-theme color, dark-theme color) as ARGB longs. */
    val ALL: Map<String, Pair<Long, Long>> = linkedMapOf(
        "wine" to (0xFF7B1E2E to 0xFFE0707F),
        "ember" to (0xFFB4451F to 0xFFF08A5D),
        "forest" to (0xFF2F6B4F to 0xFF6FC59A),
        "ocean" to (0xFF1F5E8C to 0xFF6DB3E8),
        "plum" to (0xFF5B2A6E to 0xFFC08ADB),
        "gold" to (0xFF8A6508 to 0xFFE2B84A),
    )
    fun label(name: String) = name.replaceFirstChar { it.uppercase() }
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("dishcover", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings

    private fun load(): Settings {
        val d = Settings()
        return Settings(
            theme = sp.getString("theme", d.theme) ?: d.theme,
            accent = sp.getString("accent", d.accent) ?: d.accent,
            radiusMi = sp.getInt("radiusMi", d.radiusMi),
            useKm = sp.getBoolean("useKm", d.useKm),
            feedTypes = sp.getStringSet("feedTypes", d.feedTypes)?.toSet() ?: d.feedTypes,
            showNews = sp.getBoolean("showNews", d.showNews),
            startTab = sp.getString("startTab", d.startTab) ?: d.startTab,
            haptics = sp.getBoolean("haptics", d.haptics),
            reduceMotion = sp.getBoolean("reduceMotion", d.reduceMotion),
            largeText = sp.getBoolean("largeText", d.largeText),
            healthyFirst = sp.getBoolean("healthyFirst", d.healthyFirst),
            alertsNewPlaces = sp.getBoolean("alertsNewPlaces", d.alertsNewPlaces),
            alertsNewMenus = sp.getBoolean("alertsNewMenus", d.alertsNewMenus),
            alertsSaved = sp.getBoolean("alertsSaved", d.alertsSaved),
            autoCheckUpdates = sp.getBoolean("autoCheckUpdates", d.autoCheckUpdates),
            introSeen = sp.getBoolean("introSeen", d.introSeen),
            seenChangelogVersion = sp.getInt("seenChangelogVersion", d.seenChangelogVersion),
            feedSeenAt = sp.getLong("feedSeenAt", d.feedSeenAt),
        ).also { Geo.useKm = it.useKm }
    }

    fun update(change: (Settings) -> Settings) {
        val s = change(_settings.value)
        _settings.value = s
        Geo.useKm = s.useKm
        sp.edit()
            .putString("theme", s.theme)
            .putString("accent", s.accent)
            .putInt("radiusMi", s.radiusMi)
            .putBoolean("useKm", s.useKm)
            .putStringSet("feedTypes", s.feedTypes)
            .putBoolean("showNews", s.showNews)
            .putString("startTab", s.startTab)
            .putBoolean("haptics", s.haptics)
            .putBoolean("reduceMotion", s.reduceMotion)
            .putBoolean("largeText", s.largeText)
            .putBoolean("healthyFirst", s.healthyFirst)
            .putBoolean("alertsNewPlaces", s.alertsNewPlaces)
            .putBoolean("alertsNewMenus", s.alertsNewMenus)
            .putBoolean("alertsSaved", s.alertsSaved)
            .putBoolean("autoCheckUpdates", s.autoCheckUpdates)
            .putBoolean("introSeen", s.introSeen)
            .putInt("seenChangelogVersion", s.seenChangelogVersion)
            .putLong("feedSeenAt", s.feedSeenAt)
            .apply()
    }

    /** Small cached strings (news, last crash). */
    fun getString(key: String): String? = sp.getString("x_$key", null)
    fun putString(key: String, value: String?) = sp.edit().putString("x_$key", value).apply()

    fun resetSettings() {
        val keep = _settings.value
        sp.edit().clear().apply()
        _settings.value = load()
        update { it.copy(introSeen = keep.introSeen, seenChangelogVersion = keep.seenChangelogVersion) }
    }
}
