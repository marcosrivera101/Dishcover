package app.godishcover.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import app.godishcover.ui.screens.AccountScreen
import app.godishcover.ui.screens.AddMenuScreen
import app.godishcover.ui.screens.DishScreen
import app.godishcover.ui.screens.HomeScreen
import app.godishcover.ui.screens.InterviewScreen
import app.godishcover.ui.screens.MeScreen
import app.godishcover.ui.screens.PlaceScreen
import app.godishcover.ui.screens.RulesScreen
import app.godishcover.ui.screens.SearchScreen
import app.godishcover.ui.screens.SuggestionsScreen
import app.godishcover.ui.screens.WelcomeScreen

object Routes {
    const val HOME = "home"
    const val SEARCH = "search?cat={cat}"
    const val ADD = "add?placeId={placeId}"
    const val ME = "me"
    const val SUGGEST = "suggest/{cat}"
    const val PLACE = "place/{id}"
    const val DISH = "dish/{id}"
    const val RULES = "rules"
    const val INTERVIEW = "interview"

    fun search(cat: String = "") = "search?cat=$cat"
    fun add(placeId: String = "") = "add?placeId=$placeId"
    /** "any" stands for "Surprise me". */
    fun suggest(cat: String?) = "suggest/${cat ?: "any"}"
    fun place(id: String) = "place/$id"
    fun dish(id: String) = "dish/$id"
}

@Composable
fun DishcoverRoot(vm: AppViewModel = viewModel()) {
    val user by vm.user.collectAsState()
    val profile by vm.profile.collectAsState()
    val msg by vm.message.collectAsState()
    val ctx = LocalContext.current

    LaunchedEffect(msg) {
        msg?.let {
            Toast.makeText(ctx, it, Toast.LENGTH_LONG).show()
            vm.message.value = null
        }
    }

    Column(Modifier.fillMaxSize().background(Dc.Paper).systemBarsPadding()) {
        if (vm.repo.isDemo) DemoBanner()
        Box(Modifier.weight(1f)) {
            val p = profile
            when {
                user == null -> WelcomeScreen(vm)
                p == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Dc.Wine)
                }
                !p.agreedToRules -> AccountScreen(vm, p)
                !p.interviewDone -> InterviewScreen(vm, p, onDone = {})
                else -> MainNav(vm)
            }
        }
    }
}

@Composable
private fun MainNav(vm: AppViewModel) {
    val nav = rememberNavController()
    LaunchedEffect(Unit) { vm.refreshLocation() }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            NavHost(nav, startDestination = Routes.HOME) {
                composable(Routes.HOME) { HomeScreen(vm, nav) }
                composable(
                    Routes.SEARCH,
                    arguments = listOf(navArgument("cat") { type = NavType.StringType; defaultValue = "" }),
                ) { SearchScreen(vm, nav, it.arguments?.getString("cat").orEmpty()) }
                composable(
                    Routes.ADD,
                    arguments = listOf(navArgument("placeId") { type = NavType.StringType; defaultValue = "" }),
                ) { AddMenuScreen(vm, nav, it.arguments?.getString("placeId").orEmpty()) }
                composable(Routes.ME) { MeScreen(vm, nav) }
                composable(Routes.SUGGEST) {
                    val c = it.arguments?.getString("cat").orEmpty()
                    SuggestionsScreen(vm, nav, if (c == "any") null else c)
                }
                composable(Routes.PLACE) { PlaceScreen(vm, nav, it.arguments?.getString("id").orEmpty()) }
                composable(Routes.DISH) { DishScreen(vm, nav, it.arguments?.getString("id").orEmpty()) }
                composable(Routes.RULES) { RulesScreen(nav) }
                composable(Routes.INTERVIEW) {
                    val p by vm.profile.collectAsState()
                    InterviewScreen(vm, p ?: app.godishcover.data.Profile(), onDone = { nav.popBackStack() })
                }
            }
        }
        BottomBar(nav)
    }
}

private enum class HomeTabs(val route: String, val label: String, val icon: ImageVector) {
    Today(Routes.HOME, "Today", Icons.Filled.Home),
    Search(Routes.search(), "Search", Icons.Filled.Search),
    Add(Routes.add(), "Add menu", Icons.Filled.Add),
    Me(Routes.ME, "Me", Icons.Filled.Person),
}

@Composable
private fun BottomBar(nav: NavHostController) {
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route.orEmpty()
    NavigationBar(containerColor = Dc.White) {
        HomeTabs.entries.forEach { tab ->
            val selected = current.substringBefore('?') == tab.route.substringBefore('?')
            NavigationBarItem(
                selected = selected,
                onClick = {
                    nav.navigate(tab.route) {
                        popUpTo(Routes.HOME) { saveState = false }
                        launchSingleTop = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Dc.Wine, selectedTextColor = Dc.Wine,
                    unselectedIconColor = Dc.Muted, unselectedTextColor = Dc.Muted,
                    indicatorColor = Dc.WineTint,
                ),
            )
        }
    }
}
