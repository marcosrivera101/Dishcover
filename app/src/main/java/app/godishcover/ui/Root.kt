package app.godishcover.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.godishcover.BuildConfig
import app.godishcover.data.Changelog
import app.godishcover.data.Profile
import app.godishcover.ui.screens.AboutScreen
import app.godishcover.ui.screens.AccountScreen
import app.godishcover.ui.screens.AddMenuScreen
import app.godishcover.ui.screens.ChangelogSheet
import app.godishcover.ui.screens.RestaurantsScreen
import app.godishcover.ui.screens.DishScreen
import app.godishcover.ui.screens.FeedScreen
import app.godishcover.ui.screens.HelpScreen
import app.godishcover.ui.screens.HomeScreen
import app.godishcover.ui.screens.InterviewScreen
import app.godishcover.ui.screens.IntroScreen
import app.godishcover.ui.screens.MeScreen
import app.godishcover.ui.screens.PlaceScreen
import app.godishcover.ui.screens.PostScreen
import app.godishcover.ui.screens.RulesScreen
import app.godishcover.ui.screens.SearchScreen
import app.godishcover.ui.screens.SettingsScreen
import app.godishcover.ui.screens.SplashScreen
import app.godishcover.ui.screens.SuggestionsScreen
import app.godishcover.ui.screens.UpdateBanner
import app.godishcover.ui.screens.WelcomeScreen

object Routes {
    const val HOME = "home"
    const val FEED = "feed"
    const val PLACES = "places"
    const val SEARCH = "search?cat={cat}&healthy={healthy}"
    const val ADD = "add?placeId={placeId}"
    const val ME = "me"
    const val SUGGEST = "suggest/{cat}"
    const val PLACE = "place/{id}"
    const val DISH = "dish/{id}"
    const val RULES = "rules"
    const val INTERVIEW = "interview"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val HELP = "help"
    const val POST = "post?placeId={placeId}"

    fun search(cat: String = "", healthy: Boolean = false) = "search?cat=$cat&healthy=$healthy"
    fun add(placeId: String = "") = "add?placeId=$placeId"
    /** "any" stands for "Surprise me". */
    fun suggest(cat: String?) = "suggest/${cat ?: "any"}"
    fun place(id: String) = "place/$id"
    fun dish(id: String) = "dish/$id"
    fun post(placeId: String = "") = "post?placeId=$placeId"
}

private enum class Stage { Splash, Intro, Welcome, Loading, Account, Interview, Main }

@Composable
fun DishcoverRoot(openFeed: Boolean, onOpenedFeed: () -> Unit, vm: AppViewModel = viewModel()) {
    val user by vm.user.collectAsState()
    val profile by vm.profile.collectAsState()
    val settings by vm.settings.collectAsState()
    val msg by vm.message.collectAsState()
    val ctx = LocalContext.current
    var splashDone by remember { mutableStateOf(false) }

    LaunchedEffect(msg) {
        msg?.let {
            Toast.makeText(ctx, it, Toast.LENGTH_LONG).show()
            vm.message.value = null
        }
    }

    val p = profile
    val stage = when {
        !splashDone -> Stage.Splash
        user == null && !settings.introSeen -> Stage.Intro
        user == null -> Stage.Welcome
        p == null -> Stage.Loading
        !p.agreedToRules -> Stage.Account
        !p.interviewDone -> Stage.Interview
        else -> Stage.Main
    }

    Box(Modifier.fillMaxSize().background(Dc.Paper)) {
        AnimatedContent(
            targetState = stage,
            transitionSpec = {
                if (Dc.reduceMotion) EnterTransition.None togetherWith ExitTransition.None
                else fadeIn(tween(350)) togetherWith fadeOut(tween(250))
            },
            label = "stage",
        ) { s ->
            if (s == Stage.Splash) {
                SplashScreen(onDone = { splashDone = true })
            } else {
                Column(Modifier.fillMaxSize().systemBarsPadding()) {
                    if (vm.repo.isDemo && s != Stage.Intro) DemoBanner()
                    Box(Modifier.weight(1f)) {
                        when (s) {
                            Stage.Intro -> IntroScreen(onDone = { vm.setSettings { it.copy(introSeen = true) } })
                            Stage.Welcome -> WelcomeScreen(vm)
                            Stage.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Dc.Wine)
                            }
                            Stage.Account -> AccountScreen(vm, profile ?: Profile())
                            Stage.Interview -> InterviewScreen(vm, profile ?: Profile(), onDone = {})
                            else -> MainNav(vm, openFeed, onOpenedFeed)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainNav(vm: AppViewModel, openFeed: Boolean, onOpenedFeed: () -> Unit) {
    val nav = rememberNavController()
    val settings by vm.settings.collectAsState()
    val start = remember { if (settings.startTab == "feed") Routes.FEED else Routes.HOME }
    var showChangelog by remember { mutableStateOf(settings.seenChangelogVersion < Changelog.ALL.first().versionCode) }

    LaunchedEffect(Unit) {
        vm.refreshLocation()
        vm.loadNews()
    }
    // Look for a newer build every time the app comes back to the front.
    LifecycleResumeEffect(settings.autoCheckUpdates) {
        if (settings.autoCheckUpdates) vm.checkForUpdate(manual = false)
        onPauseOrDispose { }
    }
    LaunchedEffect(openFeed) {
        if (openFeed) {
            nav.navigate(Routes.FEED) { launchSingleTop = true }
            onOpenedFeed()
        }
    }

    val slide = 40
    Column(Modifier.fillMaxSize()) {
        UpdateBanner(vm)
        Box(Modifier.weight(1f)) {
            NavHost(
                nav, startDestination = start,
                enterTransition = {
                    if (Dc.reduceMotion) EnterTransition.None
                    else fadeIn(tween(220)) + slideInHorizontally(tween(260)) { slide }
                },
                exitTransition = { if (Dc.reduceMotion) ExitTransition.None else fadeOut(tween(160)) },
                popEnterTransition = {
                    if (Dc.reduceMotion) EnterTransition.None
                    else fadeIn(tween(220)) + slideInHorizontally(tween(260)) { -slide }
                },
                popExitTransition = {
                    if (Dc.reduceMotion) ExitTransition.None
                    else fadeOut(tween(160)) + slideOutHorizontally(tween(220)) { slide }
                },
            ) {
                composable(Routes.HOME) { HomeScreen(vm, nav) }
                composable(Routes.FEED) { FeedScreen(vm, nav) }
                composable(Routes.PLACES) { RestaurantsScreen(vm, nav) }
                composable(
                    Routes.SEARCH,
                    arguments = listOf(
                        navArgument("cat") { type = NavType.StringType; defaultValue = "" },
                        navArgument("healthy") { type = NavType.BoolType; defaultValue = false },
                    ),
                ) {
                    SearchScreen(vm, nav, it.arguments?.getString("cat").orEmpty(), it.arguments?.getBoolean("healthy") ?: false)
                }
                composable(
                    Routes.ADD,
                    arguments = listOf(navArgument("placeId") { type = NavType.StringType; defaultValue = "" }),
                ) { AddMenuScreen(vm, nav, it.arguments?.getString("placeId").orEmpty()) }
                composable(
                    Routes.POST,
                    arguments = listOf(navArgument("placeId") { type = NavType.StringType; defaultValue = "" }),
                ) { PostScreen(vm, nav, it.arguments?.getString("placeId").orEmpty()) }
                composable(Routes.ME) { MeScreen(vm, nav) }
                composable(Routes.SUGGEST) {
                    val c = it.arguments?.getString("cat").orEmpty()
                    SuggestionsScreen(vm, nav, if (c == "any") null else c)
                }
                composable(Routes.PLACE) { PlaceScreen(vm, nav, it.arguments?.getString("id").orEmpty()) }
                composable(Routes.DISH) { DishScreen(vm, nav, it.arguments?.getString("id").orEmpty()) }
                composable(Routes.RULES) { RulesScreen(nav) }
                composable(Routes.SETTINGS) { SettingsScreen(vm, nav) }
                composable(Routes.ABOUT) { AboutScreen(vm, nav) }
                composable(Routes.HELP) { HelpScreen(vm, nav) }
                composable(Routes.INTERVIEW) {
                    val p by vm.profile.collectAsState()
                    InterviewScreen(vm, p ?: Profile(), onDone = { nav.popBackStack() })
                }
            }
        }
        BottomBar(nav, vm)
    }

    if (showChangelog) {
        ChangelogSheet(onDismiss = {
            showChangelog = false
            vm.setSettings { it.copy(seenChangelogVersion = Changelog.ALL.first().versionCode) }
        })
    }
}

private enum class Tabs(val route: String, val label: String, val icon: ImageVector) {
    Today(Routes.HOME, "Today", Icons.Filled.Home),
    Feed(Routes.FEED, "Feed", Icons.Filled.Notifications),
    Places(Routes.PLACES, "Restaurants", Icons.Filled.Place),
    Search(Routes.search(), "Search", Icons.Filled.Search),
    Me(Routes.ME, "Me", Icons.Filled.Person),
}

@Composable
private fun BottomBar(nav: NavHostController, vm: AppViewModel) {
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route.orEmpty()
    val feed by vm.feed.collectAsState()
    val settings by vm.settings.collectAsState()
    val unseen = remember(feed, settings.feedSeenAt, settings.radiusMi) { vm.unseenFeedCount() }
    NavigationBar(containerColor = Dc.White, windowInsets = WindowInsets(0, 0, 0, 0)) {
        Tabs.entries.forEach { tab ->
            val selected = current.substringBefore('?') == tab.route.substringBefore('?')
            NavigationBarItem(
                selected = selected,
                onClick = {
                    nav.navigate(tab.route) {
                        popUpTo(nav.graph.startDestinationId) { saveState = false }
                        launchSingleTop = true
                    }
                },
                icon = {
                    if (tab == Tabs.Feed && unseen > 0 && !selected) {
                        BadgedBox(badge = { Badge(containerColor = Dc.Wine, contentColor = Dc.OnAccent) { Text(if (unseen > 9) "9+" else "$unseen") } }) {
                            Icon(tab.icon, contentDescription = null)
                        }
                    } else Icon(tab.icon, contentDescription = null)
                },
                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.Unspecified) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Dc.Wine, selectedTextColor = Dc.Wine,
                    unselectedIconColor = Dc.Muted, unselectedTextColor = Dc.Muted,
                    indicatorColor = Dc.WineTint,
                ),
            )
        }
    }
}

@Suppress("unused")
@Composable
private fun KeepImports() { AnimatedVisibility(visible = false) {} }
