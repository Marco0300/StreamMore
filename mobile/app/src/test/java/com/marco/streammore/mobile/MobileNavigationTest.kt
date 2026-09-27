package com.marco.streammore.mobile

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The signed-in bottom bar must show a real glyph per destination. It previously
 * rendered a text bullet for every item (`●` when selected, `○` otherwise), which
 * is what made every button look like a circle.
 */
class MobileNavigationTest {
    @Test
    fun everyDestinationHasItsOwnIconAndTarget() {
        val destinations = mobileNavigationDestinations()

        assertEquals(
            listOf("Home", "Shows", "Movies", "Search", "New", "Live", "My List"),
            destinations.map { it.label },
        )
        assertEquals(
            listOf<TvScreen>(
                TvScreen.Home,
                TvScreen.Browse("tv"),
                TvScreen.Browse("movie"),
                TvScreen.Search,
                TvScreen.NewHot,
                TvScreen.Live,
                TvScreen.MyList,
            ),
            destinations.map { it.target },
        )
        assertEquals(
            "each destination needs a distinct icon",
            destinations.size,
            destinations.map { it.iconRes }.distinct().size,
        )
        assertTrue("icons must resolve to real resources", destinations.all { it.iconRes != 0 })
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-xhdpi")
class MobileBottomBarUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun bottomBarDrawsIconsInsteadOfTextBullets() {
        compose.setContent {
            MaterialTheme(colorScheme = StreammoreScheme) {
                Surface(Modifier.fillMaxSize(), color = Bg) {
                    AppShell(TvScreen.Home, {}, Fake.profiles.first()) {
                        GridScreen("Movies", Fake.cards(6), onCard = {})
                    }
                }
            }
        }
        compose.waitForIdle()

        for (destination in mobileNavigationDestinations()) {
            compose.onNodeWithTag(mobileNavIconTag(destination.label), useUnmergedTree = true)
                .assertExists("missing icon for ${destination.label}")
        }
        for (bullet in listOf("●", "○")) {
            val found = compose.onAllNodesWithText(bullet, useUnmergedTree = true)
                .fetchSemanticsNodes()
            assertTrue("the bottom bar must not draw a '$bullet' placeholder", found.isEmpty())
        }
    }
}
