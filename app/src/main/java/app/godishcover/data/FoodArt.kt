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

/** Websites of big chains, so they show their own logo even when a place has no website saved. */
object ChainSites {
    private val SITES = mapOf(
        "mcdonalds" to "mcdonalds.com", "burgerking" to "bk.com", "wendys" to "wendys.com",
        "tacobell" to "tacobell.com", "chipotle" to "chipotle.com", "chipotlemexicangrill" to "chipotle.com",
        "subway" to "subway.com", "starbucks" to "starbucks.com", "dunkin" to "dunkindonuts.com",
        "dunkindonuts" to "dunkindonuts.com", "kfc" to "kfc.com", "popeyes" to "popeyes.com",
        "popeyeslouisianakitchen" to "popeyes.com", "chickfila" to "chick-fil-a.com", "dominos" to "dominos.com",
        "dominospizza" to "dominos.com", "pizzahut" to "pizzahut.com", "papajohns" to "papajohns.com",
        "littlecaesars" to "littlecaesars.com", "panerabread" to "panerabread.com", "panera" to "panerabread.com",
        "fiveguys" to "fiveguys.com", "shakeshack" to "shakeshack.com", "wingstop" to "wingstop.com",
        "arbys" to "arbys.com", "sonic" to "sonicdrivein.com", "sonicdrivein" to "sonicdrivein.com",
        "jerseymikes" to "jerseymikes.com", "jerseymikessubs" to "jerseymikes.com", "jimmyjohns" to "jimmyjohns.com",
        "pandaexpress" to "pandaexpress.com", "olivegarden" to "olivegarden.com", "applebees" to "applebees.com",
        "applebeesgrillbar" to "applebees.com", "chilis" to "chilis.com", "chilisgrillbar" to "chilis.com",
        "ihop" to "ihop.com", "dennys" to "dennys.com", "texasroadhouse" to "texasroadhouse.com",
        "outback" to "outback.com", "outbacksteakhouse" to "outback.com", "redlobster" to "redlobster.com",
        "buffalowildwings" to "buffalowildwings.com", "qdoba" to "qdoba.com", "qdobamexicaneats" to "qdoba.com",
        "moes" to "moes.com", "moessouthwestgrill" to "moes.com", "dairyqueen" to "dairyqueen.com",
        "baskinrobbins" to "baskinrobbins.com", "cheesecakefactory" to "thecheesecakefactory.com",
        "thecheesecakefactory" to "thecheesecakefactory.com", "crackerbarrel" to "crackerbarrel.com",
        "fridays" to "tgifridays.com", "tgifridays" to "tgifridays.com", "rubytuesday" to "rubytuesday.com",
        "jackinthebox" to "jackinthebox.com", "whitecastle" to "whitecastle.com", "culvers" to "culvers.com",
        "sweetgreen" to "sweetgreen.com", "cava" to "cava.com", "noodlescompany" to "noodles.com",
        "bojangles" to "bojangles.com", "zaxbys" to "zaxbys.com", "raisingcanes" to "raisingcanes.com",
        "firehousesubs" to "firehousesubs.com", "potbelly" to "potbelly.com", "krispykreme" to "krispykreme.com",
        "timhortons" to "timhortons.com", "einsteinbrosbagels" to "einsteinbros.com", "auntieannes" to "auntieannes.com",
        "cinnabon" to "cinnabon.com", "smoothieking" to "smoothieking.com", "tropicalsmoothiecafe" to "tropicalsmoothiecafe.com",
        "kungfutea" to "kungfutea.com", "gongcha" to "gong-cha.com", "pfchangs" to "pfchangs.com",
        "longhornsteakhouse" to "longhornsteakhouse.com", "bjsrestaurantbrewhouse" to "bjsrestaurants.com",
        "carrabbasitaliangrill" to "carrabbas.com", "bertuccis" to "bertuccis.com", "friendlys" to "friendlys.com",
        "99restaurant" to "99restaurants.com", "99restaurantpub" to "99restaurants.com", "wingsover" to "wingsover.com",
    )

    fun site(name: String): String? = SITES[name.lowercase().replace("’", "").replace(Regex("[^a-z0-9]"), "")]
}
