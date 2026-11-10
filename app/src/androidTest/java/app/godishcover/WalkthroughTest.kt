package app.godishcover

import android.graphics.Bitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Walks through the app in demo mode the way a new user would and saves a screenshot of each
 * screen, so every build is checked for crashes and can be looked over without a phone.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class WalkthroughTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private var n = 0

    private fun shot(name: String) {
        rule.waitForIdle()
        Thread.sleep(700)
        val inst = InstrumentationRegistry.getInstrumentation()
        val dir = File(inst.targetContext.filesDir, "shots").apply { mkdirs() }
        var bmp = inst.uiAutomation.takeScreenshot()
        var tries = 0
        while (bmp == null && tries++ < 5) { Thread.sleep(500); bmp = inst.uiAutomation.takeScreenshot() }
        if (bmp == null) return
        n++
        File(dir, "%02d-%s.png".format(n, name)).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun waitFor(text: String, timeout: Long = 15_000) =
        rule.waitUntilAtLeastOneExists(hasText(text, substring = true), timeout)

    private fun tap(text: String, scroll: Boolean = false) {
        waitFor(text)
        val node = rule.onAllNodesWithText(text, substring = true).onFirst()
        if (scroll) node.performScrollTo()
        node.performClick()
        rule.waitForIdle()
    }

    private fun tapExact(text: String, scroll: Boolean = false) {
        rule.waitUntilAtLeastOneExists(hasText(text), 15_000)
        val node = rule.onAllNodesWithText(text).onFirst()
        if (scroll) node.performScrollTo()
        node.performClick()
        rule.waitForIdle()
    }

    private fun tapDesc(desc: String) {
        rule.waitUntilAtLeastOneExists(hasContentDescription(desc), 15_000)
        rule.onAllNodesWithContentDescription(desc).onFirst().performClick()
        rule.waitForIdle()
    }

    private fun back() {
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    @Test
    fun walkThroughTheApp() {
        waitFor("Skip")
        shot("intro")
        tap("Skip")

        waitFor("Continue with Google")
        shot("welcome")
        tap("Continue with Google")

        waitFor("Create your account")
        tap("I agree to the Terms")
        shot("account")
        tap("Continue", scroll = true)

        waitFor("What do you love to eat?")
        tap("Burger"); tap("Tacos"); tap("Sushi")
        shot("interview")
        tap("Next"); tap("Next"); tap("Next")
        tap("Ramen")
        tap("Finish")

        waitFor("What's new in Dishcover")
        shot("whats-new-sheet")
        tap("Nice", scroll = true)

        waitFor("What are you in the mood for?")
        shot("today")
        tapExact("Burger")
        tap("Show me places for")
        waitFor("best first")
        shot("suggestions")
        tap("(sample)")
        waitFor("Rate this place")
        shot("restaurant")
        tap("Smash Double")
        waitFor("Your rating")
        shot("dish")
        tapDesc("4 stars")
        tap("Save rating", scroll = true)
        waitFor("Saved")
        back(); back(); back()

        tapExact("Feed")
        waitFor("What's new")
        shot("feed")
        tapExact("New on menus")
        shot("feed-new-on-menus")
        tap("Share a bite")
        waitFor("Where did you go?")
        shot("post")
        back()

        tapExact("Search")
        waitFor("Near me")
        shot("search")
        tapExact("Healthy")
        shot("search-healthy")

        tapExact("Add")
        waitFor("Which place?")
        shot("add-menu")

        tapExact("Me")
        waitFor("Your taste")
        shot("me")
        tapExact("Settings", scroll = true)
        waitFor("Accent color")
        shot("settings")
        tapExact("Dark")
        shot("settings-dark")
        back()
        tap("Help, ideas", scroll = true)
        waitFor("Got an idea?")
        shot("help-dark")
        back()
        tap("About, updates", scroll = true)
        waitFor("Change log")
        shot("about-dark")
        back()
        tapExact("Today")
        waitFor("What are you in the mood for?")
        shot("today-dark")
        tapExact("Feed")
        waitFor("What's new")
        shot("feed-dark")
    }
}
