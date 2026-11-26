package app.godishcover.data

/**
 * Shared AI-made photos for the most common dishes, hosted with the website at /dishes/<key>.jpg.
 * Each one carries a small "AI" mark. Generated from tools/photos/dishes.tsv; the longest match wins,
 * and earlier entries win ties.
 */
object FoodPhotos {
    private const val BASE = "https://godishcover.web.app/dishes/"

    private val ALL: List<Pair<String, Regex>> = listOf(
        "iced-coffee" to Regex("""iced coffee|iced americano"""),
        "cold-brew" to Regex("""cold brew"""),
        "iced-latte" to Regex("""iced .*(latte|macchiato|mocha)|shaken espresso"""),
        "frozen-coffee" to Regex("""frapp|frozen coffee|coolatta|blended coffee"""),
        "matcha-latte" to Regex("""matcha"""),
        "latte" to Regex("""latte|cappuccino|flat white|macchiato|mocha|chai|cortado"""),
        "hot-coffee" to Regex("""coffee|americano|espresso|pike place|roast\b"""),
        "hot-chocolate" to Regex("""hot chocolate|hot cocoa"""),
        "iced-tea" to Regex("""iced tea|sweet tea|black tea|green tea|\btea\b"""),
        "refresher" to Regex("""refresher|pink drink|dragon ?fruit|lemonade|agua fresca"""),
        "smoothie" to Regex("""smoothie"""),
        "milkshake" to Regex("""shake|frosty|malt\b"""),
        "hash-browns" to Regex("""hash ?brown"""),
        "bacon-egg-cheese" to Regex("""bacon,? egg|sausage,? egg|ham,? egg|egg (and|&) cheese|breakfast sandwich|mcmuffin|egg sandwich|mcgriddle"""),
        "biscuit-sandwich" to Regex("""biscuit"""),
        "croissant-sandwich" to Regex("""croissan'?wich|croissant sandwich"""),
        "breakfast-burrito" to Regex("""breakfast burrito|breakfast crunchwrap|breakfast quesadilla"""),
        "breakfast-wrap" to Regex("""wake up wrap|egg white wrap|breakfast wrap"""),
        "avocado-toast" to Regex("""avocado toast"""),
        "bagel" to Regex("""bagel"""),
        "croissant" to Regex("""croissant"""),
        "blueberry-muffin" to Regex("""muffin"""),
        "glazed-donut" to Regex("""glazed donut|old fashioned|cake donut|\bdonut\b|doughnut"""),
        "frosted-donut" to Regex("""frosted|sprinkle"""),
        "filled-donut" to Regex("""kreme|jelly|cream filled|filled donut|boston cream"""),
        "pancakes" to Regex("""pancake|hotcake|flapjack"""),
        "waffle" to Regex("""waffle"""),
        "french-toast" to Regex("""french toast"""),
        "omelet" to Regex("""omelet|omelette|scrambled|eggs any|two eggs|egg bites"""),
        "oatmeal" to Regex("""oatmeal|oats|porridge|granola"""),
        "cheeseburger" to Regex("""cheeseburger|hamburger|burger|whopper|quarter pounder|big mac|mcdouble|dave's single|big bacon classic"""),
        "double-cheeseburger" to Regex("""double|triple|dave's (double|triple)|baconator|stack"""),
        "bacon-cheeseburger" to Regex("""bacon (cheese)?burger|bacon king|rodeo"""),
        "crispy-chicken-sandwich" to Regex("""chicken sandwich|mccrispy|mcchicken|chicken burger|crispy chicken"""),
        "grilled-chicken-sandwich" to Regex("""grilled chicken sandwich|chicken club"""),
        "fish-sandwich" to Regex("""fish sandwich|filet.?o.?fish|fish fillet"""),
        "italian-sub" to Regex("""italian|b ?m ?t|cold cut|(\w+ )?(grinder|sub|hoagie|hero)\b|supreme meats|ham\b"""),
        "turkey-sub" to Regex("""turkey"""),
        "meatball-sub" to Regex("""meatball (sub|grinder|marinara|parm)"""),
        "cheesesteak" to Regex("""cheesesteak|steak (and|&) cheese|philly"""),
        "blt" to Regex("""\bb ?l ?t\b"""),
        "club-sandwich" to Regex("""club"""),
        "tuna-sandwich" to Regex("""tuna"""),
        "grilled-cheese" to Regex("""grilled cheese|panini|melt\b"""),
        "chicken-wrap" to Regex("""(\w+ )?wrap"""),
        "hot-dog" to Regex("""hot dog|frank|coney"""),
        "wings" to Regex("""wing"""),
        "boneless-wings" to Regex("""boneless"""),
        "chicken-tenders" to Regex("""tender|finger|strips|chicken fries"""),
        "chicken-nuggets" to Regex("""nugget|mcnugget|popcorn chicken"""),
        "fried-chicken" to Regex("""fried chicken|bucket|drumstick|thigh|chicken leg|piece chicken"""),
        "french-fries" to Regex("""fries|wedges"""),
        "sweet-potato-fries" to Regex("""sweet potato"""),
        "loaded-fries" to Regex("""cheese fries|chili fries|loaded fries|nacho fries|poutine"""),
        "onion-rings" to Regex("""onion ring"""),
        "mozzarella-sticks" to Regex("""mozzarella stick|fried mozzarella|cheese stick"""),
        "mac-and-cheese" to Regex("""mac (and|&|n) cheese|macaroni"""),
        "garlic-knots" to Regex("""garlic knot|garlic bread|breadstick"""),
        "caesar-salad" to Regex("""caesar"""),
        "greek-salad" to Regex("""greek"""),
        "cobb-salad" to Regex("""cobb|chef salad|antipasto"""),
        "grilled-chicken-salad" to Regex("""chicken salad|buffalo chicken salad"""),
        "taco-salad" to Regex("""taco salad"""),
        "garden-salad" to Regex("""salad"""),
        "chicken-noodle-soup" to Regex("""chicken noodle|chicken soup|soup the day|soup of the day"""),
        "wonton-soup" to Regex("""wonton soup|egg drop|hot (and|&) sour"""),
        "french-onion-soup" to Regex("""french onion"""),
        "chili" to Regex("""\bchili\b|chowder|bisque|soup"""),
        "cheese-pizza" to Regex("""cheese pizza|pizza|\bpie\b|slice"""),
        "pepperoni-pizza" to Regex("""pepperoni"""),
        "margherita-pizza" to Regex("""margherita|margarita pizza"""),
        "buffalo-chicken-pizza" to Regex("""buffalo chicken pizza|buffalo pizza"""),
        "bbq-chicken-pizza" to Regex("""bbq chicken pizza|barbecue chicken pizza"""),
        "meat-lovers-pizza" to Regex("""meat lover|supreme pizza|works pizza|deluxe pizza"""),
        "veggie-pizza" to Regex("""veggie pizza|vegetable pizza|garden pizza"""),
        "calzone" to Regex("""calzone|stromboli"""),
        "chicken-parm" to Regex("""chicken parm|chicken marsala|chicken francese|chicken piccata"""),
        "eggplant-parm" to Regex("""eggplant"""),
        "lasagna" to Regex("""lasagna|lasagne"""),
        "penne-vodka" to Regex("""vodka|penne|rigatoni|ziti|ravioli|gnocchi|tortellini"""),
        "spaghetti-meatballs" to Regex("""spaghetti|meatball|linguine|bolognese"""),
        "chicken-alfredo" to Regex("""alfredo|fettuccine|carbonara|pasta"""),
        "shrimp-scampi" to Regex("""scampi"""),
        "crunchy-taco" to Regex("""crunchy taco|hard taco|doritos locos|tacos?\b|birria"""),
        "soft-taco" to Regex("""soft taco|street taco|al pastor|carnitas taco|chalupa|gordita"""),
        "burrito" to Regex("""burrito|quesarito|crunchwrap|chimichanga"""),
        "quesadilla" to Regex("""quesadilla"""),
        "nachos" to Regex("""nacho"""),
        "burrito-bowl" to Regex("""bowl"""),
        "chips-and-guac" to Regex("""guac|salsa|chips (and|&)"""),
        "enchiladas" to Regex("""enchilada|tamale|fajita"""),
        "fried-rice" to Regex("""fried rice|rice"""),
        "lo-mein" to Regex("""lo mein|chow mein|noodle|udon"""),
        "general-tso" to Regex("""general tso|orange chicken|sesame chicken|sweet (and|&) sour|teriyaki|kung pao"""),
        "crab-rangoon" to Regex("""rangoon"""),
        "spring-roll" to Regex("""spring roll|egg roll|eggroll"""),
        "dumplings" to Regex("""dumpling|gyoza|potsticker|wonton|bao|momo"""),
        "sushi-roll" to Regex("""sushi|roll\b|maki|nigiri|sashimi|poke"""),
        "ramen" to Regex("""ramen"""),
        "pho" to Regex("""\bpho\b"""),
        "pad-thai" to Regex("""pad thai|pad see ew|drunken noodle"""),
        "tikka-masala" to Regex("""tikka|masala|curry|korma|vindaloo|biryani|naan|samosa"""),
        "fried-calamari" to Regex("""calamari|squid"""),
        "shrimp-cocktail" to Regex("""shrimp cocktail|fried shrimp|coconut shrimp|shrimp"""),
        "fish-and-chips" to Regex("""fish (and|&|n) chips|fried fish|fish fry|fish"""),
        "salmon" to Regex("""salmon"""),
        "steak" to Regex("""steak|ribeye|sirloin|filet mignon|strip\b|porterhouse|prime rib"""),
        "ribs" to Regex("""\bribs?\b|brisket|pulled pork|bbq"""),
        "gyro" to Regex("""gyro|shawarma|kebab|souvlaki|falafel"""),
        "jerk-chicken" to Regex("""jerk|oxtail|curry goat|rice (and|&) peas"""),
        "rice-and-beans" to Regex("""beans|plantain|mofongo|empanada|pernil"""),
        "chocolate-chip-cookie" to Regex("""cookie"""),
        "brownie" to Regex("""brownie|blondie"""),
        "cheesecake" to Regex("""cheesecake"""),
        "tiramisu" to Regex("""tiramisu"""),
        "cannoli" to Regex("""cannoli"""),
        "chocolate-cake" to Regex("""cake|cupcake"""),
        "sundae" to Regex("""sundae|mcflurry|blizzard|banana split"""),
        "ice-cream-cone" to Regex("""cone|ice cream|gelato|soft serve"""),
    )

    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** The photo URL for a dish name, or null when no shared photo fits. */
    fun forDish(name: String): String? {
        val n = name.lowercase().trim()
        if (n.isEmpty()) return null
        val hit = cache.getOrPut(n) {
            var best = ""; var bestLen = 0
            for ((key, re) in ALL) {
                val len = re.findAll(n).maxOfOrNull { it.value.length } ?: 0
                if (len > bestLen) { best = key; bestLen = len }
            }
            best
        }
        return hit.takeIf { it.isNotEmpty() }?.let { "$BASE$it.jpg" }
    }
}
