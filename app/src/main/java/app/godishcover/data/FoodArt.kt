package app.godishcover.data

/** A picture for each kind of food, shown wherever a dish or place has no photo. */
object FoodArt {
    private val BY_CATEGORY = mapOf(
        "burger" to "🍔", "pizza" to "🍕", "tacos" to "🌮", "burrito" to "🌯", "sandwich" to "🥪",
        "wings" to "🍗", "chicken" to "🍗", "bbq" to "🍖", "steak" to "🥩", "seafood" to "🦐",
        "sushi" to "🍣", "ramen" to "🍜", "noodles" to "🍝", "pho" to "🍜", "dumplings" to "🥟",
        "rice bowl" to "🍚", "salad" to "🥗", "soup" to "🍲", "pasta" to "🍝", "hot dogs" to "🌭",
        "breakfast" to "🍳", "bagels" to "🥯", "fries and sides" to "🍟",
        "american" to "🍔", "italian" to "🍝", "mexican" to "🌮", "chinese" to "🥡", "japanese" to "🍱",
        "korean" to "🍲", "thai" to "🍛", "vietnamese" to "🍜", "indian" to "🍛", "mediterranean" to "🧆",
        "middle eastern" to "🧆", "greek" to "🥙", "caribbean" to "🍛", "jamaican" to "🍛",
        "puerto rican" to "🍛", "latin" to "🫓", "soul food" to "🍗", "cajun" to "🦞", "african" to "🍛",
        "brazilian" to "🥩", "peruvian" to "🍋", "french" to "🥐", "halal" to "🥙", "vegan" to "🥦",
        "dessert" to "🍰", "bakery" to "🥐", "donuts" to "🍩", "ice cream" to "🍨", "coffee" to "☕",
        "bubble tea" to "🧋", "smoothies and juice" to "🥤",
    )

    /** Words in a dish name that pick a better picture than its category. */
    private val BY_WORD = listOf(
        Regex("burger") to "🍔", Regex("pizza|calzone|stromboli") to "🍕", Regex("taco") to "🌮",
        Regex("burrito|wrap") to "🌯", Regex("sushi|roll\\b|nigiri|sashimi") to "🍣", Regex("ramen|pho\\b|noodle soup") to "🍜",
        Regex("wing") to "🍗", Regex("fries|tots") to "🍟", Regex("salad") to "🥗", Regex("soup|chowder|stew|bisque") to "🍲",
        Regex("shrimp|prawn") to "🍤", Regex("lobster|crab") to "🦞", Regex("oyster|clam|mussel") to "🦪", Regex("salmon|fish|tuna|cod\\b") to "🐟",
        Regex("steak|ribeye|sirloin|filet") to "🥩", Regex("rib|brisket|pork") to "🍖", Regex("egg|omelet") to "🍳",
        Regex("pancake|waffle|french toast") to "🥞", Regex("bagel") to "🥯", Regex("croissant|pastry") to "🥐",
        Regex("cake|cheesecake|tiramisu") to "🍰", Regex("cookie") to "🍪", Regex("donut|doughnut") to "🍩",
        Regex("ice cream|gelato|sundae") to "🍨", Regex("pie\\b") to "🥧", Regex("coffee|latte|espresso|cappuccino|mocha") to "☕",
        Regex("tea\\b") to "🍵", Regex("boba|bubble") to "🧋", Regex("smoothie|shake|juice") to "🥤",
        Regex("beer|ale\\b|lager|ipa\\b") to "🍺", Regex("wine|sangria") to "🍷", Regex("margarita|cocktail|martini|mojito") to "🍹",
        Regex("dumpling|gyoza|potsticker") to "🥟", Regex("curry") to "🍛", Regex("rice") to "🍚", Regex("pasta|spaghetti|linguine|penne|lasagna|ravioli|fettuccine") to "🍝",
        Regex("sandwich|sub\\b|hoagie|panini|melt|club") to "🥪", Regex("hot dog") to "🌭", Regex("quesadilla|nachos") to "🧀",
        Regex("falafel|gyro|shawarma|kebab") to "🥙", Regex("chicken|tender") to "🍗", Regex("veggie|vegetable") to "🥦",
    )

    fun forDish(name: String, categories: List<String>): String {
        val n = name.lowercase()
        BY_WORD.firstOrNull { it.first.containsMatchIn(n) }?.let { return it.second }
        return categories.firstNotNullOfOrNull { BY_CATEGORY[it] } ?: "🍽️"
    }

    fun forPlace(cuisine: String, dishCategories: List<String>): String {
        val cs = cuisine.lowercase().split(';', ',').map { it.trim().replace('_', ' ') }
        return cs.firstNotNullOfOrNull { BY_CATEGORY[it] ?: BY_WORD.firstOrNull { w -> w.first.containsMatchIn(it) }?.second }
            ?: dishCategories.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
                .firstNotNullOfOrNull { BY_CATEGORY[it.key] }
            ?: "🍽️"
    }
}
