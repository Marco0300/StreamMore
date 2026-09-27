package com.marco.streammore.mobile

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
 * The profile avatars are emoji (the server only accepts the glyphs in
 * `PROFILE_AVATARS`). A bare `Text` in a fixed-size circle does not work: an emoji's
 * line box is much taller than its artwork and, if the line box is clamped with a
 * `lineHeight`, the artwork is clipped. These tests pin the two decisions that make
 * the glyph fit *and* centre inside the disc:
 *
 *  - the font size is derived from the glyph's measured ink, not from a fixed ratio;
 *  - the ink is centred by offsetting against the line box, not by trusting the
 *    font's ascent/descent.
 */
class AvatarGlyphTest {
    private val diameter = 1000f

    @Test
    fun emojiAvatarIsUsedAsTheGlyphWhenPresent() {
        assertEquals("🦊", avatarGlyphText(Profile("p1", "Marco", "🦊")))
    }

    @Test
    fun initialsAreUsedWhenTheProfileHasNoAvatar() {
        assertEquals("M", avatarGlyphText(Profile("p1", "Marco", null)))
        assertEquals("K", avatarGlyphText(Profile("p2", "kids", null)))
        assertEquals("?", avatarGlyphText(Profile("p3", "   ", "  ")))
    }

    @Test
    fun glyphIsScaledSoItsLargestInkDimensionFitsInsideTheDisc() {
        // Square ink: the fitted size makes the artwork occupy the target fraction.
        val square = fittedAvatarGlyphFontSizePx(
            inkWidth = 80f,
            inkHeight = 80f,
            probeFontSizePx = 100f,
            diameterPx = diameter,
        )
        assertEquals(diameter * AvatarInkFraction, square / 100f * 80f, 0.01f)

        // Wide ink is limited by its width, tall ink by its height.
        val wide = fittedAvatarGlyphFontSizePx(200f, 50f, 100f, diameter)
        val tall = fittedAvatarGlyphFontSizePx(50f, 200f, 100f, diameter)
        assertEquals(diameter * AvatarInkFraction, wide / 100f * 200f, 0.01f)
        assertEquals(diameter * AvatarInkFraction, tall / 100f * 200f, 0.01f)
    }

    @Test
    fun fittedGlyphNeverExceedsTheDisc() {
        val cases = listOf(80f to 80f, 200f to 50f, 50f to 200f, 1f to 1f, 0.5f to 300f)
        for ((width, height) in cases) {
            val fontSize = fittedAvatarGlyphFontSizePx(width, height, 100f, diameter)
            val scale = fontSize / 100f
            assertTrue(
                "ink ${width}x$height must stay inside the disc",
                width * scale <= diameter && height * scale <= diameter,
            )
        }
    }

    @Test
    fun unusableInkFallsBackToASafeFontSizeInsteadOfBlowingUp() {
        val fallback = diameter * AvatarFallbackFontRatio
        assertEquals(fallback, fittedAvatarGlyphFontSizePx(0f, 0f, 100f, diameter), 0.01f)
        assertEquals(fallback, fittedAvatarGlyphFontSizePx(Float.NaN, Float.NaN, 100f, diameter), 0.01f)
        assertEquals(fallback, fittedAvatarGlyphFontSizePx(10f, 10f, 0f, diameter), 0.01f)
        assertEquals(0f, fittedAvatarGlyphFontSizePx(10f, 10f, 100f, 0f), 0.01f)
    }

    @Test
    fun inkIsOffsetFromTheLineBoxSoItCentresVertically() {
        // Ink sitting above the line box's middle is pushed down, and vice versa.
        assertEquals(10f, avatarGlyphInkOffsetY(lineHeightPx = 200f, inkTopPx = 40f, inkBottomPx = 140f), 0.01f)
        assertEquals(-10f, avatarGlyphInkOffsetY(lineHeightPx = 200f, inkTopPx = 60f, inkBottomPx = 160f), 0.01f)
        assertEquals(0f, avatarGlyphInkOffsetY(lineHeightPx = 200f, inkTopPx = 50f, inkBottomPx = 150f), 0.01f)
        assertEquals(0f, avatarGlyphInkOffsetY(Float.NaN, 0f, 0f), 0.01f)
    }
}

/**
 * The picker must render each avatar through [AvatarGlyph]; the previous version put a
 * fixed 42sp `Text` in a 142dp circle, which is what clipped the emoji.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-xhdpi")
class ProfilePickerUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun everyProfileIsDrawnThroughTheFittedAvatarGlyph() {
        val profiles = listOf(
            Profile("p1", "Marco", "🦊", false),
            Profile("p2", "Kids", "🐼", true),
            Profile("p3", "Guest", null, false),
        )
        compose.setContent {
            MaterialTheme(colorScheme = StreammoreScheme) {
                Surface(Modifier.fillMaxSize(), color = Bg) {
                    ProfileScreen(profiles, error = null, onSelect = {})
                }
            }
        }
        compose.waitForIdle()

        for (profile in profiles) {
            compose.onNodeWithTag(mobileAvatarGlyphTag(profile.id), useUnmergedTree = true)
                .assertExists("profile ${profile.name} must render a fitted avatar glyph")
        }
    }
}
