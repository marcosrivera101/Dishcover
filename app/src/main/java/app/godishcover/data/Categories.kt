package app.godishcover.data

object Categories {
    /** Fixed category tags (step 1 plan). Stored lowercase, shown capitalized. */
    val ALL = listOf(
        "burger", "pizza", "tacos", "sushi", "salad", "sandwich", "pasta",
        "chicken", "seafood", "breakfast", "dessert", "coffee", "other",
    )
    val INTERVIEW = ALL - "other"
    val ALLERGIES = listOf(
        "Peanuts", "Tree nuts", "Shellfish", "Fish", "Milk", "Eggs", "Wheat / gluten", "Soy", "Sesame",
    )
    val WANT_TO_TRY = listOf(
        "Ramen", "Birria tacos", "Pho", "Shawarma", "Poke", "Bao", "Empanadas",
        "Pierogi", "Dim sum", "Mofongo", "Omakase", "Bibimbap",
    )

    /** Diet tags a dish can carry. "healthy" powers Healthy picks. */
    val TAGS = listOf("healthy", "vegetarian", "vegan", "gluten-free", "spicy")
    const val HEALTHY = "healthy"

    fun label(cat: String): String = cat.replaceFirstChar { it.uppercase() }

    enum class Meal(val label: String, val greeting: String, val hint: String) {
        Breakfast("Breakfast", "Good morning", "breakfast and coffee"),
        Lunch("Lunch", "Good afternoon", "quick lunch picks"),
        Dinner("Dinner", "Good evening", "what people order for dinner"),
    }

    /** Breakfast before 11:00, lunch until 15:00, dinner after. */
    fun mealFor(hour: Int): Meal = when {
        hour < 11 -> Meal.Breakfast
        hour < 15 -> Meal.Lunch
        else -> Meal.Dinner
    }

    /** Mood chips for the home screen. Empty string means "Surprise me". */
    fun moodsFor(meal: Meal): List<String> {
        val extra = when (meal) {
            Meal.Breakfast -> listOf("breakfast", "coffee")
            Meal.Lunch -> listOf("sandwich", "salad")
            Meal.Dinner -> listOf("sushi", "pasta")
        }
        return listOf("burger", "tacos", "pizza") + extra + ""
    }

    fun defaultMood(meal: Meal): String = when (meal) {
        Meal.Breakfast -> "breakfast"
        Meal.Lunch -> "sandwich"
        Meal.Dinner -> "burger"
    }
}
