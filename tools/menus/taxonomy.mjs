// Turns a dish name, description and the restaurant's cuisine into Dishcover's fixed
// categories and diet tags. Keep the category keys in step with Categories.kt.

const DISH_RULES = [
  ["burger", /burger|whopper|baconator|big mac|quarter pounder|mcdouble|slamburger|smash\b|patty melt|dave's (single|double)/],
  ["pizza", /pizza|calzone|stromboli|apizza|^(?!.*(apple|pecan|pumpkin|key lime|cream|\bpot\b|shepherd|spinach|sundae|cherry|sweet potato|chocolate|lemon|blueberry|peach|strawberry|berry|hand pie|turnover|mince|custard|sweet|dessert)).*\bpie\b/],
  ["tacos", /taco|birria/],
  ["burrito", /burrito|quesadilla|chimichanga|enchilada|nachos|crunchwrap|chalupa|gordita|fajita/],
  ["sandwich", /sandwich|\bsub\b|grinder|hoagie|panini|\bwrap\b|cubano|\bclub\b|\bblt\b|b\.l\.t|\bmelt\b|\bhero\b|po' ?boy|croissan'wich|philly|cheesesteak|cheese steak|reuben|banh mi|gyro|torta|stacker|biscuit (sandwich|with (egg|sausage|bacon))/],
  ["wings", /wing/],
  ["chicken", /chicken|tenders|nugget|mcchicken|chick-n|pollo|jerk/],
  ["bbq", /\bbbq\b|barbe?cue|brisket|pulled pork|\bribs?\b|smoked|burnt ends/],
  ["steak", /steak|ribeye|rib eye|sirloin|filet mignon|\bfilet\b(?!-o)|ny strip|new york strip|picanha|churrasco|porterhouse|t-bone|tenderloin|prime rib/],
  ["seafood", /shrimp|salmon|\bfish|lobster|crab|scallop|clam|oyster|mussel|calamari|\btuna|\bcod\b|tilapia|seafood|octopus|mahi|halibut|swordfish|branzino|snapper|saltfish|bacalao|ceviche|haddock|catfish|crawfish/],
  ["sushi", /sushi|maki|nigiri|sashimi|hand roll|california roll|dragon roll|rainbow roll|spicy tuna|spider roll|eel roll|philadelphia roll|tempura roll|omakase/],
  ["ramen", /ramen/],
  ["noodles", /noodle|lo mein|chow mein|pad thai|udon|soba|pad see ew|drunken|chow fun|mei fun|japchae|yakisoba|dan dan|laksa/],
  ["pho", /\bpho\b/],
  ["dumplings", /(?<!fr(y|ied) )dumpling|gyoza|potsticker|pot sticker|\bbao\b|dim sum|wonton|\bmomo|xiao long|shumai|siu mai|har gow/],
  ["rice bowl", /\bbowl\b|fried rice|rice plate|bibimbap|\bpoke\b|donburi|biryani|rice (and|&) (beans|peas)|arroz|jollof|bento/],
  ["salad", /salad|caesar(?! wrap)/],
  ["soup", /soup|chowder|bisque|\bstew\b|pozole|menudo|sancocho|gumbo|chili\b|minestrone|pasta e fagioli|tom yum|tom kha|miso/],
  ["pasta", /pasta|spaghetti|penne|linguin|fettuc|ravioli|lasagn|gnocchi|rigatoni|alfredo|carbonara|tortellin|\bziti|mac (and|&|n) cheese|macaroni|bolognese|cavatelli|orecchiette|pappardelle|bucatini|manicotti|vodka sauce/],
  ["hot dogs", /hot ?dog|cheese dog|\bfrank\b|kielbasa|bratwurst|corn dog/],
  ["breakfast", /pancake|hotcake|waffle|omelet|omelette|french toast|\beggs?\b|benedict|breakfast|hash ?brown|home fries|egg (and|&) cheese|crepe|grand slam|\bslam\b|mcmuffin|scramble|frittata|oatmeal|grits|chilaquiles|huevos|wake-up wrap|avocado toast/],
  ["bagels", /bagel/],
  ["fries and sides", /\bfries\b|\btots\b|onion rings|mozzarella sticks|coleslaw|cole slaw|mashed potato|\bplantain|tostones|maduros|garlic knots|garlic bread|breadsticks|bread bites|side salad|mac (and|&) cheese side|cornbread|hush ?pupp|baked potato|corn on the cob/],
  ["dessert", /cake|cheesecake|brownie|cookie|^(?=.*(apple|sundae|pecan|pumpkin|key lime|cream|cherry|sweet potato|chocolate|lemon|blueberry|peach|strawberry|berry|hand pie|mince|custard|sweet|dessert)).*\bpies?\b|turnover|tiramisu|cannol|flan|sundae|churro|pudding|dessert|tres leches|mousse|cobbler|baklava|creme brulee|crème brûlée|beignet|mochi|mcflurry|frosty|lava|cinnamon (twists|sticks|delights|bread)|zeppole/],
  ["bakery", /croissant|muffin|scone|danish|pastr|\bloaf\b|banana bread|coco bread|\bpatty\b(?!.*melt)|patties|empanada|pastelillo|turnover|bun\b|roll(s)? \(|biscotti|bread pudding/],
  ["donuts", /donut|doughnut|munchkin|cruller|fritter/],
  ["ice cream", /ice cream|gelato|sorbet|frozen yogurt|froyo|milkshake|\bshake\b|\bcone\b|sundae|frosty|mcflurry|coolatta|freeze\b/],
  ["coffee", /coffee|latte|espresso|cappuccino|americano|macchiato|cold brew|mocha|cortado|frappuccino|frappé|frappe|flat white|pike place|roast\b/],
  ["bubble tea", /bubble tea|\bboba\b|milk tea|taro|thai tea/],
  ["smoothies and juice", /smoothie|juice|acai|açaí|lemonade|refresher|agua fresca|batido|sea moss/],
];

const CUISINE_RULES = [
  ["american", /american|diner|burger|steak_house|steak house|bar and grill|grill|pub|tavern|sports|new_american|wings/],
  ["italian", /italian|pizza|trattoria|ristorante|apizza/],
  ["mexican", /mexican|tex-mex|taqueria|tacos?\b|burrito/],
  ["chinese", /chinese|szechuan|sichuan|cantonese|hot ?pot|dim sum/],
  ["japanese", /japanese|sushi|ramen|hibachi|izakaya/],
  ["korean", /korean/],
  ["thai", /thai/],
  ["vietnamese", /vietnamese|\bpho\b/],
  ["indian", /indian|south_indian|pakistani|nepal|himalayan|bangladeshi/],
  ["mediterranean", /mediterranean|lebanese|turkish|falafel|kebab|greek/],
  ["middle eastern", /middle_eastern|middle eastern|lebanese|turkish|afghan|persian|syrian|shawarma|kebab|halal/],
  ["greek", /greek|gyro/],
  ["caribbean", /caribbean|jamaican|west_indian|trinidad|haitian|puerto_rican|dominican|cuban/],
  ["jamaican", /jamaican|jerk/],
  ["puerto rican", /puerto_rican|puerto rican|mofongo/],
  ["latin", /latin|spanish|dominican|colombian|salvadoran|honduran|guatemalan|cuban|venezuelan|ecuadorian|puerto_rican|peruvian|brazilian/],
  ["soul food", /soul|southern/],
  ["cajun", /cajun|creole|louisiana/],
  ["african", /african|ethiopian|nigerian|ghanaian|senegalese|somali|eritrean/],
  ["brazilian", /brazil/],
  ["peruvian", /peruvian|peru/],
  ["french", /french|bistro|brasserie|crepe/],
  ["halal", /halal/],
  ["vegan", /vegan|plant[- ]based/],
  ["bbq", /barbecue|bbq|smokehouse/],
  ["pizza", /pizza/],
  ["chicken", /chicken/],
  ["coffee", /coffee_shop|coffee|cafe|café|espresso/],
  ["donuts", /donut/],
  ["breakfast", /breakfast|brunch|pancake/],
  ["sandwich", /sandwich|deli|sub/],
  ["ice cream", /ice_cream|ice cream|frozen_yogurt/],
  ["bakery", /bakery|pastry/],
  ["seafood", /seafood|fish/],
];

const DISH_CUISINE = [
  ["jamaican", /jerk|oxtail|curry goat|ackee|escovitch|festival|callaloo|rice (and|&) peas|beef patty|coco bread/],
  ["caribbean", /jerk|oxtail|curry goat|ackee|roti|doubles|callaloo|plantain|rice (and|&) peas|coco bread/],
  ["puerto rican", /mofongo|pernil|alcapurria|pastelillo|tostones|arroz con gandules|pasteles|tripleta|jibarito|bacalaito/],
  ["latin", /mofongo|pernil|empanada|arepa|pupusa|tostones|maduros|sancocho|churrasco|lomo saltado|ceviche|chicharr|tres leches|flan|cubano|ropa vieja/],
  ["mexican", /taco|burrito|quesadilla|enchilada|tamale|elote|pozole|birria|carnitas|al pastor|chilaquiles|mole|nachos|guacamole|horchata|tostada|torta|huarache|sope/],
  ["vietnamese", /\bpho\b|banh mi|bun bo|vermicelli bowl|spring roll/],
  ["thai", /pad thai|pad see ew|thai|drunken noodle|tom yum|tom kha|green curry|red curry|massaman|panang|larb/],
  ["indian", /tikka|masala|biryani|naan|samosa|vindaloo|korma|paneer|tandoori|\bdal\b|saag|chana|pakora|dosa|idli|\blassi\b/],
  ["korean", /bibimbap|bulgogi|kimchi|galbi|tteok|japchae|korean/],
  ["japanese", /sushi|sashimi|nigiri|maki|ramen|udon|tempura|teriyaki|katsu|gyoza|edamame|miso|hibachi|donburi|takoyaki/],
  ["chinese", /lo mein|chow mein|general tso|kung pao|sesame chicken|orange chicken|egg roll|fried rice|wonton|dumpling|mapo|szechuan|moo shu|egg foo|chow fun|mei fun|crab rangoon|sweet and sour/],
  ["greek", /gyro|souvlaki|spanakopita|moussaka|tzatziki|greek/],
  ["middle eastern", /shawarma|falafel|hummus|kebab|kabob|tabbouleh|baba ganoush|kofta|baklava|manakish/],
  ["mediterranean", /shawarma|falafel|hummus|gyro|kebab|kabob|tabbouleh|tzatziki|greek salad/],
  ["italian", /pizza|pasta|spaghetti|lasagn|ravioli|risotto|\bparm\b|parmigiana|calzone|bruschetta|tiramisu|cannol|gnocchi|carbonara|alfredo|arancini|caprese|piccata|marsala/],
  ["cajun", /cajun|jambalaya|gumbo|etouffee|étouffée|po' ?boy|crawfish|beignet/],
  ["soul food", /fried chicken|collard|mac (and|&) cheese|candied yam|cornbread|smothered|chicken (and|&) waffles|catfish|grits|black[- ]eyed peas/],
  ["african", /jollof|injera|fufu|suya|egusi|waakye/],
  ["brazilian", /picanha|feijoada|coxinha|pão de queijo|pao de queijo|brigadeiro/],
  ["peruvian", /lomo saltado|ceviche|aji de gallina|pollo a la brasa|causa|anticucho/],
];

const MEAT = /chicken|beef|pork|bacon|ham\b|steak|sausage|pepperoni|turkey|lamb|goat|oxtail|brisket|shrimp|fish|salmon|tuna|crab|lobster|clam|meat|chorizo|prosciutto|salami|pastrami|anchov|carnitas|pernil|veal|duck|wings?\b|jerk|scallop|calamari|octopus|mussel|oyster|cod|tilapia|eel|burger(?!.*(veggie|impossible|beyond|black bean))/;
const FRIED = /fried|crispy|tempura|battered|breaded|fries|nugget|tender|bacon|alfredo|cream|loaded|buffalo|cheesesteak|philly|\bparm\b|parmigiana|cake|cookie|donut|shake|sundae|brownie|pie\b|carbonara|mac (and|&) cheese|double|triple|deluxe|supreme|stuffed/;
const ALCOHOL = /\bbeer|\blight\b.*\b(lager|ale)\b|bud light|miller|coors|michelob|corona|modelo|heineken|stella|guinness|\bipa\b|lager|\bale\b|stout|pilsner|cider|seltzer|wine|merlot|cabernet|pinot|chardonnay|sauvignon|prosecco|champagne|sangria|cocktail|margarita|martini|mojito|vodka|tequila|whiskey|bourbon|\brum\b|\bgin\b|mimosa|spritz/;
const HEALTHY = /salad|grilled|steamed|\bbowl\b|poke|acai|açaí|smoothie|veggie|vegetable|quinoa|kale|broiled|lean|light|protein|avocado toast|oatmeal|fruit|sashimi|nigiri|lettuce wrap|egg white|baked salmon|roasted vegetables|power|greens|hummus|lentil|chickpea|tofu|edamame|ceviche|pho\b|miso soup|fit slam/;

export function categorize(name, description = "", cuisine = "", section = "") {
  const text = `${name} ${description}`.toLowerCase();
  const sec = section.toLowerCase();
  const cats = new Set();
  for (const [cat, re] of DISH_RULES) if (re.test(text)) cats.add(cat);
  if (cats.size === 0) for (const [cat, re] of DISH_RULES) if (sec && re.test(sec)) cats.add(cat);
  for (const [cat, re] of DISH_CUISINE) if (re.test(text)) cats.add(cat);
  const c = cuisine.toLowerCase();
  for (const [cat, re] of CUISINE_RULES) {
    if (!re.test(c)) continue;
    // Place-level hints that are dish types only apply when the dish says nothing else.
    const dishType = ["bbq", "pizza", "chicken", "coffee", "donuts", "breakfast", "sandwich", "ice cream", "bakery", "seafood"].includes(cat);
    if (!dishType || cats.size === 0) cats.add(cat);
  }
  // A drink at a coffee shop is coffee, not "american".
  if (cats.has("coffee") || cats.has("bubble tea") || cats.has("smoothies and juice")) cats.delete("american");
  if (cats.size === 0) cats.add("other");
  return [...cats].slice(0, 5);
}

export function tagsFor(name, description = "", cuisine = "") {
  const text = `${name} ${description}`.toLowerCase();
  const tags = new Set();
  const meat = MEAT.test(text);
  if (/vegan|plant[- ]based|beyond meat/.test(text) || (/vegan/.test(cuisine) && !meat)) tags.add("vegan");
  if (tags.has("vegan") || (/vegetarian|veggie|\(v\)|meatless|impossible|tofu|falafel|caprese|margherita|cheese pizza|eggplant|paneer|spinach|black bean|bean burrito|grilled cheese/.test(text) && !meat)) tags.add("vegetarian");
  if (/gluten[- ]free|\bgf\b/.test(text)) tags.add("gluten-free");
  if (/spicy|\bhot\b|jalape|buffalo|sriracha|diablo|nashville|habanero|ghost pepper|jerk|vindaloo|szechuan|sichuan|atomic|fra diavolo|chipotle|kimchi|mapo|curry goat/.test(text)) tags.add("spicy");
  if (HEALTHY.test(text) && !FRIED.test(text) && !ALCOHOL.test(text)) tags.add("healthy");
  return [...tags];
}

const ALLERGEN_RULES = [
  ["Peanuts", /peanut/], ["Tree nuts", /almond|walnut|pecan|cashew|pistachio|hazelnut|macadamia|pine nut/],
  ["Shellfish", /shrimp|lobster|crab|crawfish|prawn/], ["Fish", /\bfish|salmon|tuna|cod\b|tilapia|anchov|halibut|snapper|saltfish|bacalao|mahi|swordfish/],
  ["Milk", /cheese|cream|butter|milk|yogurt|mozzarella|parmesan|ricotta|alfredo|queso|latte|cappuccino|shake|ice cream|gelato/],
  ["Eggs", /\begg|omelet|mayo|aioli|carbonara|benedict|french toast/], ["Wheat / gluten", /bread|bun\b|pasta|noodle|flour|tortilla|wrap|pizza|bagel|croissant|biscuit|breaded|tempura|crust|pancake|waffle|dumpling|naan/],
  ["Soy", /\bsoy|tofu|edamame|miso|teriyaki/], ["Sesame", /sesame|tahini|hummus/],
  ["Mollusks", /clam|mussel|oyster|scallop|squid|calamari|octopus/], ["Coconut", /coconut|coco bread/],
  ["Mushrooms", /mushroom/], ["Pork", /pork|bacon|ham\b|sausage|pepperoni|prosciutto|chorizo|carnitas|pernil|salami/],
  ["Red meat", /beef|steak|burger|lamb|goat|oxtail|brisket|veal|picanha/], ["Avocado", /avocado|guacamole/],
];

/** Allergens the dish's own name or description makes obvious. Menus are incomplete; the app says so. */
export function allergensFor(name, description = "") {
  const text = `${name} ${description}`.toLowerCase();
  return ALLERGEN_RULES.filter(([, re]) => re.test(text)).map(([a]) => a);
}
