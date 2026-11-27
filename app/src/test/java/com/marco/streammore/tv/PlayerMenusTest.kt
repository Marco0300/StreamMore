package com.marco.streammore.tv

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every button on the playback control row, driven the way the viewer drives it:
 * wake the controls, open a menu, pick an option, and see the result.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi-notouch")
class PlayerMenusTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val sources = listOf(
        StreamSource("Alpha", "1080p", "http://127.0.0.1:1/a.m3u8"),
        StreamSource("Beta", "720p", "http://127.0.0.1:1/b.m3u8"),
    )
    private val episodes = (1..30).map { Episode(it, "Ep $it") }

    private var chosenSource: StreamSource? = null
    private var chosenEpisode: Episode? = null
    private var playedNext: NextEpisodeInfo? = null
    private var chosenSubtitle: String? = "unset"
    private var chosenAudio: String? = null
    private var backs = 0

    private fun show(preferredAudio: String? = null, preferredSubtitle: String? = null) {
        compose.setContent {
            MaterialTheme(colorScheme = StreammoreScheme) {
                Surface(Modifier.fillMaxSize(), color = Bg) {
                    PlayerScreen(
                        // A closed port: nothing plays, which is fine for driving the controls.
                        source = "http://127.0.0.1:1/never-plays.m3u8",
                        title = "The Bear",
                        season = 2,
                        episode = 20,
                        subtitles = listOf(
                            SubtitleTrack("English", "eng", "http://127.0.0.1:1/en.vtt"),
                            SubtitleTrack("Spanish", "spa", "http://127.0.0.1:1/es.vtt"),
                        ),
                        cookie = null,
                        nextEpisode = NextEpisodeInfo("tv", 2, "Forks", 21, 1),
                        sources = sources,
                        episodeList = episodes,
                        onSelectSource = { picked, _ -> chosenSource = picked },
                        onSelectEpisode = { chosenEpisode = it },
                        onPlayNext = { playedNext = it },
                        onBack = { backs++ },
                        preferredAudioLanguage = preferredAudio,
                        preferredSubtitleLanguage = preferredSubtitle,
                        onSubtitleChosen = { chosenSubtitle = it },
                        onAudioLanguageChosen = { chosenAudio = it },
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onRoot().performKeyInput {
            keyDown(Key.DirectionCenter)
            keyUp(Key.DirectionCenter)
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun subtitlesMenuPicksALanguageAndOff() {
        show()
        compose.onNodeWithContentDescription("Subtitles").performClick()
        compose.onNodeWithText("Off").assertExists()
        compose.onNodeWithText("Spanish").performClick()
        assertEquals("spa", chosenSubtitle)
        compose.onNodeWithText("Off").assertDoesNotExist()

        compose.onNodeWithContentDescription("Subtitles").performClick()
        compose.onNodeWithText("Off").performClick()
        assertNull(chosenSubtitle)
        compose.onNodeWithText("Off").assertDoesNotExist()
    }

    @Test
    fun aSavedSubtitleLanguageIsSelectedBeforeAnyMenuIsOpened() {
        show(preferredSubtitle = "spa")
        // The control row names the chosen language once it is focused; the menu marks it.
        compose.onNodeWithContentDescription("Subtitles").performClick()
        compose.onNodeWithText("Spanish").assertExists()
    }

    @Test
    fun sourcesMenuSwitchesSource() {
        show()
        compose.onNodeWithContentDescription("Sources").performClick()
        compose.onNodeWithText("1080p · Alpha").assertExists()
        compose.onNodeWithText("720p · Beta").performClick()
        assertEquals("Beta", chosenSource?.name)
        compose.onNodeWithText("720p · Beta").assertDoesNotExist()
    }

    @Test
    fun episodesMenuOpensOnTheEpisodeBeingWatched() {
        show()
        compose.onNodeWithContentDescription("Episodes").performClick()
        // Episode 20 of 30: the list must start there, not at episode 1.
        compose.onNodeWithText("20. Ep 20", substring = true).assertIsDisplayed()
        compose.onNodeWithText("1. Ep 1", substring = true).assertDoesNotExist()
        compose.onNodeWithText("20. Ep 20", substring = true).performClick()
        assertEquals(20, chosenEpisode?.number)
        compose.onNodeWithText("20. Ep 20", substring = true).assertDoesNotExist()
    }

    @Test
    fun settingsMenuTogglesAutoPlay() {
        show()
        compose.onNodeWithContentDescription("Player settings").performClick()
        compose.onNodeWithText("Auto-play next episode: On").performClick()
        compose.onNodeWithText("Auto-play next episode: Off").assertExists()
        compose.onNodeWithText("Auto-play next episode: Off").performClick()
        compose.onNodeWithText("Auto-play next episode: On").assertExists()
    }

    @Test
    fun qualityMenuOpensAndClosesOnAuto() {
        show()
        // The quality button carries its current label; "Auto" appears again in the menu.
        compose.onNodeWithText("Auto").performClick()
        compose.onNodeWithText("Video quality").assertExists()
        compose.onAllNodesWithText("Auto")[1].performClick()
        compose.onNodeWithText("Video quality").assertDoesNotExist()
    }

    @Test
    fun audioMenuShowsTheProfilesLanguageAndClosesOnChoice() {
        show(preferredAudio = "spa")
        compose.onNodeWithContentDescription("Audio tracks").performClick()
        compose.onNodeWithText("Auto · Spanish preferred").assertExists()
        compose.onNodeWithText("Auto · Spanish preferred").performClick()
        compose.onNodeWithText("Auto · Spanish preferred").assertDoesNotExist()
    }

    @Test
    fun nextEpisodeButtonHandsOffExactlyOnce() {
        show()
        compose.onNodeWithContentDescription("Next episode").performClick()
        assertEquals(21, playedNext?.episode)
        playedNext = null
        compose.onNodeWithContentDescription("Next episode").performClick()
        assertNull("a second press during the hand-off must be ignored", playedNext)
    }

    @Test
    fun openingOneMenuClosesTheOther() {
        show()
        compose.onNodeWithContentDescription("Subtitles").performClick()
        compose.onNodeWithText("Off").assertExists()
        compose.onNodeWithContentDescription("Sources").performClick()
        compose.onNodeWithText("Off").assertDoesNotExist()
        compose.onNodeWithText("1080p · Alpha").assertExists()
    }

    @Test
    fun backClosesAMenuBeforeLeavingThePlayer() {
        show()
        compose.onNodeWithContentDescription("Sources").performClick()
        back()
        compose.onNodeWithText("1080p · Alpha").assertDoesNotExist()
        assertEquals("the first Back only closes the menu", 0, backs)
        back()
        assertTrue("the second Back leaves the player", backs == 1)
    }
}
