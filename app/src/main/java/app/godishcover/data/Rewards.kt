package app.godishcover.data

/** Points, level and badges, counted by the server (stats/{uid}). */
data class Stats(
    var points: Long = 0,
    var level: Int = 1,
    var counts: Map<String, Long> = emptyMap(),
    var badges: List<String> = emptyList(),
)

/** Must match functions/src/rewards.ts. */
object Rewards {
    val LEVELS = listOf(
        0 to "Nibbler", 50 to "Taster", 150 to "Regular", 400 to "Foodie",
        800 to "Connoisseur", 1500 to "Critic", 3000 to "Tastemaker", 6000 to "Legend",
    )

    fun levelName(level: Int) = LEVELS.getOrNull(level - 1)?.second ?: LEVELS.first().second
    fun levelFor(points: Long): Int = LEVELS.indexOfLast { points >= it.first } + 1

    /** Points needed for the next level, or null at the top. */
    fun nextAt(level: Int): Int? = LEVELS.getOrNull(level)?.first

    data class Badge(val id: String, val name: String, val how: String, val emoji: String)

    val BADGES = listOf(
        Badge("first_bite", "First Bite", "Rate your first dish", "🍴"),
        Badge("regular_rater", "Regular Rater", "Rate 25 dishes", "⭐"),
        Badge("explorer", "Explorer", "Rate food at 10 different places", "🧭"),
        Badge("globetrotter", "Globetrotter", "Rate food at 50 different places", "🌎"),
        Badge("spice_seeker", "Spice Seeker", "Rate 10 spicy dishes", "🌶️"),
        Badge("photographer", "Photographer", "Post 10 food photos", "📸"),
        Badge("early_bird", "Early Bird", "Be first to rate 5 dishes or places", "🐦"),
        Badge("night_owl", "Night Owl", "Rate 5 dishes between 10 PM and 4 AM", "🦉"),
        Badge("menu_maker", "Menu Maker", "Add a full menu (5+ dishes) for a place", "📋"),
        Badge("trailblazer", "Trailblazer", "Add 3 new places", "📍"),
        Badge("wordsmith", "Wordsmith", "Write 10 real reviews", "✍️"),
        Badge("crowd_pleaser", "Crowd Pleaser", "Get 50 likes", "❤️"),
    )

    val HOW = listOf(
        "Rate a dish or place" to "10",
        "Write a real review (a sentence or two)" to "+5",
        "Be the first to rate it" to "+10",
        "Post a food photo" to "10",
        "Add a dish to a menu" to "5",
        "Add a full menu (5+ dishes)" to "+25",
        "Add a new place" to "25",
        "Someone likes your post" to "2",
    )

    val PERKS = listOf(
        3 to "Extra app colors",
        6 to "Early access to new features",
        8 to "No ads, once ads arrive",
    )
}
