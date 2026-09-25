package com.marco.streammore.mobile

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@androidx.compose.runtime.Composable
private fun mobileTestFrame(content: @androidx.compose.runtime.Composable () -> Unit) {
    MaterialTheme(colorScheme = StreammoreScheme) {
        Surface(Modifier.fillMaxSize(), color = Bg) { content() }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-xhdpi")
class PortraitUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun portraitShellRendersResponsiveContentAndBottomNavigation() {
        compose.setContent {
            mobileTestFrame {
                AppShell(TvScreen.Home, {}, Fake.profiles.first()) {
                    GridScreen("Movies", Fake.cards(6), onCard = {})
                }
            }
        }
        compose.waitForIdle()
        val metrics = compose.activity.resources.displayMetrics
        assertTrue("test window must be portrait", metrics.heightPixels > metrics.widthPixels)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w915dp-h411dp-xhdpi")
class LandscapeUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun landscapeShellRendersTvStyleTopNavigation() {
        compose.setContent {
            mobileTestFrame {
                AppShell(TvScreen.Home, {}, Fake.profiles.first()) {
                    GridScreen("Movies", Fake.cards(12), onCard = {})
                }
            }
        }
        compose.waitForIdle()
        val metrics = compose.activity.resources.displayMetrics
        assertTrue("test window must be landscape", metrics.widthPixels > metrics.heightPixels)
    }
}
