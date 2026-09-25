package com.marco.streammore.mobile

import androidx.media3.ui.AspectRatioFrameLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class MobileLayoutTest {
    @Test
    fun choosesPortraitModeForNarrowPhoneWindows() {
        assertEquals(MobileLayoutMode.Portrait, mobileLayoutMode(widthDp = 411, heightDp = 915))
    }

    @Test
    fun choosesLandscapeModeForWidePhoneWindows() {
        assertEquals(MobileLayoutMode.Landscape, mobileLayoutMode(widthDp = 915, heightDp = 411))
    }

    @Test
    fun mobileNavigationStaysAtTheBottomInBothOrientations() {
        assertEquals(MobileNavigationPlacement.Bottom, mobileNavigationPlacement(widthDp = 411, heightDp = 915))
        assertEquals(MobileNavigationPlacement.Bottom, mobileNavigationPlacement(widthDp = 915, heightDp = 411))
    }

    @Test
    fun gridCardsStayUsableInBothOrientations() {
        assertEquals(118, mobilePosterMinSizeDp(widthDp = 411, heightDp = 915))
        assertEquals(132, mobilePosterMinSizeDp(widthDp = 915, heightDp = 411))
    }

    @Test
    fun mobilePlaybackStartsWithControlsAvailable() {
        assertEquals(true, mobilePlayerInitialChromeVisible())
    }

    @Test
    fun fullscreenControlHasAnActionableToggleLabel() {
        assertEquals("Fullscreen", mobileFullscreenLabel(false))
        assertEquals("Exit fullscreen", mobileFullscreenLabel(true))
    }
    @Test
    fun fullscreenUsesZoomToFillThePhoneDisplay() {
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, mobilePlayerResizeMode(true))
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, mobilePlayerResizeMode(false))
    }
    @Test
    fun systemBarsAreSafeForScreensAndImmersiveForPlayback() {
        assertEquals(MobileSystemBarsMode.Safe, mobileSystemBarsMode(TvScreen.Home))
        assertEquals(
            MobileSystemBarsMode.ImmersivePlayback,
            mobileSystemBarsMode(TvScreen.Player(source = "https://example.invalid/live.m3u8", title = "Test")),
        )
    }

}

