package app.godishcover.data

object Categories {
    /** Fixed category tags (step 1 plan). Stored lowercase, shown capitalized. Grouped for pickers. */
    val DISHES = listOf(
        "burger", "pizza", "tacos", "burrito", "sandwich", "wings", "chicken", "bbq", "steak", "seafood",
        "sushi", "ramen", "noodles", "pho", "dumplings", "rice bowl", "salad", "soup", "pasta", "hot dogs",
        "breakfast", "bagels", "fries and sides",
    )
    val CUISINES = listOf(
        "american", "italian", "mexican", "chinese", "japanese", "korean", "thai", "vietnamese", "indian",
        "mediterranean", "middle eastern", "greek", "caribbean", "jamaican", "puerto rican", "latin",
        "soul food", "cajun", "african", "brazilian", "peruvian", "french", "halal", "vegan",
    )
    val SWEETS_DRINKS = listOf(
        "dessert", "bakery", "donuts", "ice cream", "coffee", "bubble tea", "smoothies and juice",
    )
    val ALL = DISHES + CUISINES + SWEETS_DRINKS + "other"
    val INTERVIEW = ALL - "other"
    val GROUPS = listOf("Dishes" to DISHES, "Cuisines" to CUISINES, "Sweets and drinks" to SWEETS_DRINKS)

    val ALLERGIES = listOf(
        "Peanuts", "Tree nuts", "Shellfish", "Fish", "Milk", "Eggs", "Wheat / gluten", "Soy", "Sesame",
        "Mustard", "Corn", "Coconut", "Celery", "Sulfites", "Lupin", "Mollusks", "Red meat", "Pork",
        "Nightshades", "Garlic", "Onion", "Strawberries", "Avocado", "Kiwi", "Mushrooms", "Chocolate",
    )
    val WANT_TO_TRY = listOf(
        "Ramen", "Birria tacos", "Pho", "Shawarma", "Poke", "Bao", "Empanadas", "Pierogi", "Dim sum",
        "Mofongo", "Omakase", "Bibimbap", "Jerk chicken", "Oxtail", "Curry goat", "Pad thai", "Tikka masala",
        "Biryani", "Korean fried chicken", "Hot pot", "Banh mi", "Falafel", "Gyro", "Arepas", "Pupusas",
        "Ceviche", "Lomo saltado", "Picanha", "Brisket", "Crawfish", "Lobster roll", "Chicken and waffles",
        "Mac and cheese", "Xiao long bao", "Takoyaki", "Tonkatsu", "Udon", "Katsu curry", "Jollof rice",
        "Injera", "Cannoli", "Tiramisu", "Churros", "Mochi", "Bubble tea", "Acai bowl", "Crepes",
        "Pastelillos", "Cubano", "Philly cheesesteak", "Nashville hot chicken", "Smash burger",
        "Neapolitan pizza", "New Haven apizza", "Steamed cheeseburger",
    )

    private val SPECIAL = mapOf("bbq" to "BBQ", "pho" to "Pho")
    fun label(cat: String): String = SPECIAL[cat] ?: cat.replaceFirstChar { it.uppercase() }

    /** Diet tags a dish can carry. "healthy" powers Healthy picks. */
    val TAGS = listOf("healthy", "vegetarian", "vegan", "gluten-free", "spicy")
    const val HEALTHY = "healthy"

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

    /** Mood chips for the home screen, before "More". Empty string means "Surprise me". */
    fun moodsFor(meal: Meal): List<String> {
        val list = when (meal) {
            Meal.Breakfast -> listOf("breakfast", "coffee", "bagels", "donuts", "bakery", "sandwich", "smoothies and juice", "burrito")
            Meal.Lunch -> listOf("sandwich", "salad", "burger", "pizza", "tacos", "rice bowl", "pho", "soup", "wings", "sushi")
            Meal.Dinner -> listOf("burger", "pizza", "tacos", "sushi", "pasta", "steak", "bbq", "ramen", "wings", "seafood", "chinese", "caribbean")
        }
        return list + ""
    }

    fun defaultMood(meal: Meal): String = when (meal) {
        Meal.Breakfast -> "breakfast"
        Meal.Lunch -> "sandwich"
        Meal.Dinner -> "burger"
    }
}
