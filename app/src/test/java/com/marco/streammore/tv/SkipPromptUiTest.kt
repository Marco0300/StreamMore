package com.marco.streammore.tv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi-notouch")
class SkipPromptUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun skipPromptShowsOneActionInTheBottomCornerFocusesItAndInvokesItsSkipCallback() {
        var skipped = false
        var focused = false
        val focusRequester = FocusRequester()
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    SkipSegmentPrompt(
                        target = SkipTarget("Skip intro", 246_143L),
                        focusRequester = focusRequester,
                        onSkip = { skipped = true },
                        onFocusGained = { focused = true },
                        modifier = Modifier.align(Alignment.BottomEnd)
                            .padding(end = 34.dp, bottom = 42.dp)
                            .widthIn(max = 360.dp),
                    )
                }
            }
        }

        val skipButton = compose.onNodeWithText("Skip intro").assertExists()
        val bounds = skipButton.fetchSemanticsNode().boundsInRoot
        val metrics = compose.activity.resources.displayMetrics
        assertTrue("skip action should be in the right half of the TV frame", bounds.left > metrics.widthPixels / 2f)
        assertTrue("skip action should be in the lower half of the TV frame", bounds.top > metrics.heightPixels / 2f)
        assertTrue("skip action must not extend beyond the TV frame", bounds.right <= metrics.widthPixels && bounds.bottom <= metrics.heightPixels)

        skipButton.performSemanticsAction(SemanticsActions.RequestFocus)
        skipButton.assertIsFocused()
        compose.runOnIdle { assertTrue("focus callback should confirm focus", focused) }

        skipButton.performClick()
        assertTrue(skipped)
    }
}
