package app.godishcover.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.godishcover.R
import app.godishcover.ui.Dc
import app.godishcover.ui.PrimaryButton
import app.godishcover.ui.Wordmark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Startup animation: the plate spins in on wine, then the name rises under it. */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val plateScale = remember { Animatable(0.4f) }
    val plateTurn = remember { Animatable(-140f) }
    val plateAlpha = remember { Animatable(0f) }
    val nameAlpha = remember { Animatable(0f) }
    val nameRise = remember { Animatable(18f) }
    LaunchedEffect(Unit) {
        if (Dc.reduceMotion) {
            plateScale.snapTo(1f); plateTurn.snapTo(0f); plateAlpha.snapTo(1f); nameAlpha.snapTo(1f); nameRise.snapTo(0f)
            delay(500)
        } else {
            launch { plateAlpha.animateTo(1f, tween(250)) }
            launch { plateTurn.animateTo(0f, tween(900, easing = FastOutSlowInEasing)) }
            plateScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
            launch { nameRise.animateTo(0f, tween(450, easing = FastOutSlowInEasing)) }
            nameAlpha.animateTo(1f, tween(450))
            delay(450)
        }
        onDone()
    }
    Box(Modifier.fillMaxSize().background(Dc.Brand), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painterResource(R.drawable.plate), contentDescription = null,
                modifier = Modifier.size(150.dp).scale(plateScale.value).rotate(plateTurn.value).alpha(plateAlpha.value),
            )
            Spacer(Modifier.height(28.dp))
            Text(
                "Dishcover", style = MaterialTheme.typography.displaySmall, color = Color.White,
                modifier = Modifier.alpha(nameAlpha.value).padding(top = nameRise.value.dp),
            )
            Text(
                "Dishcover your next meal", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.alpha(nameAlpha.value),
            )
        }
    }
}

private data class IntroPage(val icon: ImageVector, val title: String, val body: String)

private val PAGES = listOf(
    IntroPage(Icons.Filled.Star, "Rate dishes, not just restaurants", "Remember exactly what was good. Your stars shape everything Dishcover suggests."),
    IntroPage(Icons.Filled.Search, "Know what to eat tonight", "Tell us your mood and we'll line up the best places near you, ranked by your ratings and everyone else's."),
    IntroPage(Icons.Filled.Notifications, "See what's new around you", "A feed of new menus, new places, reviews and what people are eating. Add a menu and help your city eat better."),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IntroScreen(onDone: () -> Unit) {
    val pager = rememberPagerState(pageCount = { PAGES.size })
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Wordmark(30.dp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDone) { Text("Skip", color = Dc.Wine) }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { i ->
            val page = PAGES[i]
            Column(
                Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(120.dp).clip(CircleShape).background(Dc.WineTint), contentAlignment = Alignment.Center) {
                    Icon(page.icon, contentDescription = null, tint = Dc.Wine, modifier = Modifier.size(56.dp))
                }
                Spacer(Modifier.height(32.dp))
                Text(page.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(page.body, style = MaterialTheme.typography.bodyLarge, color = Dc.Muted, textAlign = TextAlign.Center)
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.Center) {
            PAGES.indices.forEach { i ->
                val on = pager.currentPage == i
                Box(
                    Modifier.padding(horizontal = 4.dp).height(8.dp).width(if (on) 24.dp else 8.dp)
                        .clip(CircleShape).background(if (on) Dc.Wine else Dc.ChipLine),
                )
            }
        }
        val last = pager.currentPage == PAGES.lastIndex
        PrimaryButton(if (last) "Get started" else "Next", onClick = {
            if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
        })
    }
}
