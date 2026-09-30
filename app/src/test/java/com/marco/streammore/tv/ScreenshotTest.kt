package com.marco.streammore.tv

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsActions

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick

import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import coil.test.FakeImageLoaderEngine
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/**
 * Renders the real Compose screens to PNG on the JVM via Robolectric's native
 * (Skia) graphics. No emulator and no /dev/kvm required.
 *
 * Renders at 960x540dp / xhdpi, i.e. a 1080p Android TV panel.
 * Output: one PNG per screen under app/build/screenshots/
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi-notouch")
class ScreenshotTest {

    /** A 24 hour wall clock, as the corner of the screen renders it. */
    private val clockPattern = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

    /**
     * Matches any node whose text is a wall clock, so the assertion does not depend
     * on what the minute happens to be while the suite runs.
     */
    private val clockMatcher = SemanticsMatcher("shows a HH:mm clock") { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.any { clockPattern.matches(it.text) } == true
    }

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun installFakeImages() {
        // Deterministic posters: no network, no async races.
        val engine = FakeImageLoaderEngine.Builder()
            .intercept({ it is String && it.contains("backdrop") }, ColorDrawable(0xFF3E4C6B.toInt()))
            .intercept({ it is String && it.contains("poster") }, ColorDrawable(0xFF3C3C3C.toInt()))
            .default(ColorDrawable(0xFF2A2A2A.toInt()))
            .build()
        val loader = ImageLoader.Builder(ApplicationProvider.getApplicationContext())
            .components { add(engine) }
            .build()
        Coil.setImageLoader(loader)
    }

    /** Mirrors the app root theme so screenshots reflect real colours. */
    @Composable
    private fun AppFrame(content: @Composable () -> Unit) {
        MaterialTheme(colorScheme = StreammoreScheme) {
            Surface(Modifier.fillMaxSize(), color = Bg) { content() }
        }
    }

    /**
     * captureToImage() needs a real window redraw (PixelCopy) that Robolectric
     * cannot provide, so draw the view hierarchy into a software Bitmap instead.
     */
    private fun capture(): Bitmap {
        val decor = compose.activity.window.decorView
        val metrics = compose.activity.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        if (decor.width != width || decor.height != height) {
            decor.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            decor.layout(0, 0, width, height)
        }
        compose.waitForIdle()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        decor.draw(Canvas(bitmap))
        return bitmap
    }

    private fun shot(name: String, onReady: (() -> Unit)? = null, content: @Composable () -> Unit) {
        compose.setContent { AppFrame(content) }
        compose.waitForIdle()
        onReady?.let { hook -> compose.runOnIdle { hook() } }
        compose.waitForIdle()

        writePng(name, capture())
    }

    private fun writePng(name: String, bitmap: Bitmap) {
        val dir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
        val out = dir.resolve("$name.png")
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SHOT $name -> $out (${bitmap.width}x${bitmap.height})")
    }

    /** Counts pixels matching the Streammore purple primary (#9B6DFF). */
    private fun purplePixels(bitmap: Bitmap): Int {
        var count = 0
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.red(pixel) > 120 && Color.blue(pixel) > 180 && Color.green(pixel) < 150) count++
            }
        }
        return count
    }

    // ---------------------------------------------------------------- screens

    @Test
    fun login() = shot("01-login") {
        LoginScreen(loading = false, error = null) { _, _ -> }
    }

    @Test
    fun loginError() = shot("02-login-error") {
        LoginScreen(loading = false, error = "Cannot reach Streammore backend at http://192.168.3.221:3896: Connection refused") { _, _ -> }
    }

    @Test
    fun profiles() = shot("03-profiles") {
        ProfileScreen(Fake.profiles, null) { }
    }

    @Test
    fun home() = shot("04-home") {
        HomeScreen(rows = Fake.rows, onCard = { })
    }

    /** The hero, without a trailer (a WebView cannot render under Robolectric). */
    @Test
    fun homeHero() = shot("23-home-hero") {
        HomeScreen(rows = Fake.rows, billboard = Fake.billboard, autoplayPreviews = false, onCard = { })
    }

    @Test
    fun shellAndGrid() = shot("05-shell-grid") {
        AppShell(TvScreen.Browse("movie"), { }, Fake.profiles[0]) {
            GridScreen("Movies", Fake.cards(18), onCard = { })
        }
    }

    @Test
    fun currentTopLevelSectionHasAnActiveMarker() {
        compose.setContent {
            AppFrame {
                AppShell(TvScreen.Browse("movie"), { }, Fake.profiles[0]) { }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("active-tab-indicator-movies", useUnmergedTree = true).fetchSemanticsNode()
        check(compose.onAllNodesWithTag("active-tab-indicator-search").fetchSemanticsNodes().isEmpty()) {
            "the Search tab must not be marked active on the Movies screen"
        }
    }

    @Test
    fun activeSidebarMarkerUsesThePurpleBrandAccent() {
        compose.setContent {
            AppFrame {
                AppShell(TvScreen.Home, { }, Fake.profiles[0]) {
                    Text("Home body", color = TextPrimary)
                }
            }
        }
        compose.waitForIdle()
        val purple = purplePixels(capture())
        check(purple > 40) { "the active Home marker should use the purple brand accent; found $purple purple pixels" }
    }

    @Test
    fun switchProfileIsInsideTheFocusableProfileMenu() {
        val destinations = mutableListOf<TvScreen>()
        compose.setContent {
            AppFrame {
                AppShell(TvScreen.Browse("movie"), { destinations += it }, Fake.profiles[0]) { }
            }
        }
        compose.waitForIdle()

        check(compose.onAllNodesWithText("Switch", substring = false).fetchSemanticsNodes().isEmpty()) {
            "Switch should be available from the profile menu, not displayed as a separate nav item"
        }
        compose.onNodeWithContentDescription("Profile menu").performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        val switchProfile = compose.onNodeWithText("Switch profile", substring = false)
        switchProfile.fetchSemanticsNode()
        switchProfile.assertIsFocused()
        writePng("32-profile-menu", capture())
        switchProfile.performClick()

        check(destinations == listOf(TvScreen.Profiles)) { "expected Switch profile to navigate to profile selection: $destinations" }
    }

    @Test
    fun searchIsAtTheTopOfTheSidebarAndOpensSearch() {
        val destinations = mutableListOf<TvScreen>()
        compose.setContent {
            AppFrame {
                AppShell(TvScreen.Search, { destinations += it }, Fake.profiles[0]) {
                    Text("Page content", color = TextPrimary, modifier = Modifier.testTag("page-content"))
                }
            }
        }
        compose.waitForIdle()

        check(compose.onAllNodesWithText("Search", substring = false).fetchSemanticsNodes().isEmpty()) {
            "Search should be an icon, not a text label"
        }
        val search = compose.onNodeWithContentDescription("Search")
        val searchBounds = search.fetchSemanticsNode().boundsInRoot
        val homeBounds = compose.onNodeWithContentDescription("Home").fetchSemanticsNode().boundsInRoot
        val contentBounds = compose.onNodeWithTag("page-content").fetchSemanticsNode().boundsInRoot
        check(searchBounds.top < homeBounds.top && searchBounds.right < contentBounds.left) {
            "expected Search at the top of the left rail beside page content: Search=$searchBounds Home=$homeBounds Content=$contentBounds"
        }
        compose.onNodeWithTag("active-tab-indicator-search", useUnmergedTree = true).fetchSemanticsNode()
        writePng("33-search-sidebar", capture())
        search.performClick()
        check(destinations == listOf(TvScreen.Search)) { "expected icon click to open Search: $destinations" }
    }

    @Test
    fun homeShowsAFeaturedHeroAndTrendingShelfInTheSidebarLayout() {
        compose.setContent {
            AppFrame {
                AppShell(TvScreen.Home, { }, Fake.profiles[0]) {
                    HomeScreen(
                        rows = listOf(Fake.rows[1]),
                        billboard = Fake.billboard,
                        autoplayPreviews = false,
                        onCard = { },
                    )
                }
            }
        }
        compose.waitForIdle()

        val kicker = compose.onNodeWithText("SERIES", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithText("Reacher", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val play = compose.onNodeWithText("▶ Play", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val moreInfo = compose.onNodeWithText("More info", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val trending = compose.onNodeWithText("Trending Now", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        check(kicker.top < title.top && title.bottom < play.top && play.bottom < trending.top) {
            "hero hierarchy should lead into the trending shelf: kicker=$kicker title=$title play=$play trending=$trending"
        }
        check(play.left < moreInfo.left && moreInfo.top == play.top) {
            "Play and More info should form a single horizontal action row: Play=$play MoreInfo=$moreInfo"
        }
        writePng("34-home-sidebar-featured", capture())
    }

    @Test
    fun search() = shot("06-search") {
        AppShell(TvScreen.Search, { }, Fake.profiles[0]) {
            SearchScreen(onCard = { }) { Fake.cards(6) }
        }
    }

    @Test
    fun searchUpdatesSuggestionsAsYouTypeWithoutStealingFocus() {
        val requestedQueries = mutableListOf<String>()
        val matches = listOf(
            MediaCard(mediaType = "movie", tmdbId = 1, title = "Dune"),
            MediaCard(mediaType = "tv", tmdbId = 2, title = "The Bear"),
        )
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AppFrame {
                SearchScreen(onCard = { }, onSearch = { query ->
                    requestedQueries += query
                    matches
                })
            }
        }
        val input = compose.onNode(hasSetTextAction())
        input.performClick()
        input.performTextInput("Du")
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()

        check(requestedQueries == listOf("Du")) { "expected a live search for the current text; got $requestedQueries" }
        input.assertIsFocused()
        val moviesHeading = compose.onNodeWithText("Movies", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val movieTitle = compose.onNodeWithText("Dune", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val showsHeading = compose.onNodeWithText("TV Shows", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val showTitle = compose.onNodeWithText("The Bear", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val screenHeight = compose.activity.resources.displayMetrics.heightPixels.toFloat()
        check(
            moviesHeading.top < showsHeading.top && movieTitle.top < showsHeading.top &&
                showsHeading.bottom < showTitle.top && showTitle.bottom <= screenHeight,
        ) {
            "both result categories must have a visible, separate row: Movies=$moviesHeading Dune=$movieTitle TV Shows=$showsHeading The Bear=$showTitle screenHeight=$screenHeight"
        }
        input.assertIsFocused()
        writePng("06-search-live", capture())
    }

    @Test
    fun liveTv() = shot("07-livetv") {
        AppShell(TvScreen.Live, { }, Fake.profiles[0]) {
            LiveScreen(Fake.channels, null, { }, { })
        }
    }

    @Test
    fun liveTvEmpty() = shot("08-livetv-empty") {
        AppShell(TvScreen.Live, { }, Fake.profiles[0]) {
            LiveScreen(emptyList(), null, { }, { })
        }
    }

    @Test
    fun viewingActivityIsRemovedFromTheSidebar() {
        compose.setContent {
            AppFrame {
                AppShell(TvScreen.Home, { }, Fake.profiles[0]) {
                    Text("Home content", color = TextPrimary)
                }
            }
        }
        compose.waitForIdle()
        check(compose.onAllNodesWithContentDescription("Activity").fetchSemanticsNodes().isEmpty()) {
            "Viewing Activity should no longer be a sidebar destination"
        }
        check(compose.onAllNodesWithText("Viewing activity", substring = true).fetchSemanticsNodes().isEmpty()) {
            "Viewing Activity should no longer be shown as a page"
        }
    }

    private fun verifyShelfFocusIsNotClipped(wide: Boolean) {
        val card = Fake.cards(1).first().copy(title = "Edge fixture", ribbon = null)
        compose.setContent {
            AppFrame {
                AppShell(TvScreen.Home, { }, Fake.profiles[0]) {
                    RowSection("Edge shelf", listOf(card), onCard = { }, wide = wide)
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(card.title)
            .performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
        val bitmap = capture()
        writePng(if (wide) "edge-wide-focus" else "edge-poster-focus", bitmap)
        val caption = compose.onNodeWithText(card.title).fetchSemanticsNode().boundsInRoot
        val image = compose.onNodeWithContentDescription(card.title).fetchSemanticsNode().boundsInRoot
        // The focus ring must be visible to the LEFT of the unscaled caption;
        // a row starting at the card itself clips this strip entirely.
        val density = compose.activity.resources.displayMetrics.density
        val cardWidth = (if (wide) 250f else PosterWidth.value) * density
        val x = (caption.left - cardWidth * 0.045f + 2).toInt()
        val purple = (image.top.toInt() + 20 until image.bottom.toInt() - 20).count { y ->
            val pixel = bitmap.getPixel(x, y)
            Color.red(pixel) > 120 && Color.blue(pixel) > 180 && Color.green(pixel) < 150
        }
        check(purple > 20) { "focused ${if (wide) "wide" else "poster"} card's left border is clipped ($purple pixels)" }
        val content = compose.onNodeWithTag("main-app-content").fetchSemanticsNode().boundsInRoot
        check(caption.left - content.left <= 40f) { "menu-to-card gap is too large: ${caption.left - content.left}px" }
        val viewport = compose.onNodeWithTag("shelf-viewport-Edge shelf").fetchSemanticsNode().boundsInRoot
        check(abs(viewport.right - content.right) < 1f) { "a fixed right gutter still clips the shelf" }
    }

    @Test
    fun posterShelfFocusHasUnclippedLeftBorder() = verifyShelfFocusIsNotClipped(false)

    @Test
    fun wideShelfFocusHasUnclippedLeftBorder() = verifyShelfFocusIsNotClipped(true)

    @Test
    fun newHotTopRowUpDoesNotRequestAnUnattachedHero() {
        var moveFocus: (FocusDirection) -> Boolean = { false }
        val card = Fake.cards(1).first().copy(mediaType = "tv", title = "Top trending fixture")
        compose.setContent {
            AppFrame {
                val manager = LocalFocusManager.current
                SideEffect { moveFocus = manager::moveFocus }
                AppShell(TvScreen.NewHot, { }, Fake.profiles[0]) {
                    NewHotScreen(listOf(HomeRow("trending", "Trending Now", listOf(card))), onCard = { })
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(card.title)
            .performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
        compose.onNodeWithContentDescription(card.title).assertIsFocused()
        repeat(5) {
            compose.runOnIdle {
                moveFocus(FocusDirection.Up)
                val view = compose.activity.window.decorView
                view.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_UP))
                view.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_UP))
            }
            compose.waitForIdle()
        }
        compose.onNodeWithTag("main-app-content").fetchSemanticsNode()
    }

    @Test
    fun sidebarFocusMovesDoNotRecomposeHomeContent() {
        var contentCompositions = 0
        compose.setContent {
            AppFrame {
                AppShell(TvScreen.Home, { }, Fake.profiles[0]) {
                    SideEffect { contentCompositions++ }
                    MediaCardView(Fake.cards(1).first(), onClick = { })
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Home")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
        val baseline = contentCompositions
        listOf("New & Hot", "TV Shows", "Movies", "TV Shows", "New & Hot", "Home").forEach { label ->
            compose.onNodeWithContentDescription(label)
                .performSemanticsAction(SemanticsActions.RequestFocus)
            compose.waitForIdle()
            compose.onNodeWithContentDescription(label).assertIsFocused()
            check(contentCompositions == baseline) {
                "sidebar focus on $label recomposed Home content: $baseline -> $contentCompositions"
            }
        }
    }

    @Test
    fun focusedSidebarItemShowsItsNameOnlyWhileFocused() {
        var moveFocus: (FocusDirection) -> Boolean = { false }
        compose.setContent {
            AppFrame {
                val focusManager = LocalFocusManager.current
                SideEffect { moveFocus = focusManager::moveFocus }
                AppShell(TvScreen.Home, { }, Fake.profiles[0]) {
                    MediaCardView(
                        Fake.cards(1).first().copy(ribbon = null),
                        onClick = { },
                        modifier = Modifier.testTag("right-side-card"),
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Movies")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Movies").assertIsFocused()
        compose.onNodeWithTag("focused-nav-label", useUnmergedTree = true).fetchSemanticsNode()
        check(compose.runOnIdle { moveFocus(FocusDirection.Right) }) {
            "focus should move from the selected menu item into the cards"
        }
        compose.waitForIdle()
        compose.onNodeWithTag("right-side-card").assertIsFocused()
        val cardBounds = compose.onNodeWithTag("right-side-card").fetchSemanticsNode().boundsInRoot
        val fullScreen = capture()
        val cardImage = Bitmap.createBitmap(
            fullScreen,
            cardBounds.left.toInt(),
            cardBounds.top.toInt(),
            cardBounds.width.toInt().coerceAtLeast(1),
            cardBounds.height.toInt().coerceAtLeast(1),
        )
        val cardRingPixels = purplePixels(cardImage)
        check(cardRingPixels > 500) {
            "the currently focused card must visibly draw the purple focus ring; found $cardRingPixels purple pixels"
        }
        check(compose.onAllNodesWithTag("focused-nav-label", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
            "the menu label should disappear as soon as focus leaves the sidebar"
        }
        check(compose.runOnIdle { moveFocus(FocusDirection.Left) }) {
            "focus should return from the first card to the last selected menu item"
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Movies").assertIsFocused()
        compose.onNodeWithTag("focused-nav-label", useUnmergedTree = true).fetchSemanticsNode()
        compose.onNodeWithText("Movies", useUnmergedTree = true).fetchSemanticsNode()
    }

    @Test
    fun detailMovie() = shot("10-detail-movie") {
        DetailScreen(Fake.movieDetail, emptyList(), null, { }, { }, { }, { _, _ -> }, { }, { }, { })
    }

    @Test
    fun detailTv() = shot("11-detail-tv") {
        DetailScreen(Fake.tvDetail, Fake.episodes, null, { }, { }, { }, { _, _ -> }, { }, { }, { })
    }

    @Test
    fun detailTvProgress() = shot("24-detail-tv-progress") {
        val watchedEpisodes = listOf(
            Fake.episodes[0].copy(progress = 0.5, positionMs = 300_000L),
            Fake.episodes[1].copy(progress = 1.0, watched = true, positionMs = 600_000L),
        )
        DetailScreen(Fake.tvDetail, watchedEpisodes, null, { }, { }, { }, { _, _ -> }, { }, { }, { })
    }

    /**
     * The detail page has to answer "what time is this over" for the title Play
     * would start: a resumed movie from its saved position, and a series from the
     * episode Resume opens. Asserted on the composed nodes, so the wording is
     * checked rather than only the pixels.
     */
    @Test
    fun detailMovieFinishTime() {
        val resumed = Fake.movieDetail.copy(resumePositionMs = 70 * 60_000L)
        compose.setContent {
            AppFrame {
                DetailScreen(resumed, emptyList(), null, { }, { }, { }, { _, _ -> }, { }, { }, { })
            }
        }
        compose.waitForIdle()
        writePng("28-detail-movie-ends-at", capture())
        assertTextPresent("Ends at", substring = true)
        // 167 minutes of film with 70 minutes watched leaves 97, whenever this runs.
        assertTextPresent("1h 37m left", substring = true)
        assertClockPresent()
    }

    @Test
    fun detailTvFinishTime() {
        compose.setContent {
            AppFrame {
                DetailScreen(Fake.tvDetail, Fake.episodes, null, { }, { }, { }, { _, _ -> }, { }, { }, { })
            }
        }
        compose.waitForIdle()
        writePng("29-detail-tv-ends-at", capture())
        assertTextPresent("Ends at", substring = true)
        // Season 1's first episode carries a 41 minute runtime.
        assertTextPresent("41m left", substring = true)
    }

    @Test
    fun debugBuildUsesTheConfiguredLanBackend() {
        check(BuildConfig.STREAMMORE_BASE_URL == "http://192.168.3.221:3896") {
            "TV debug builds should target the configured LAN Streammore service, got ${BuildConfig.STREAMMORE_BASE_URL}"
        }
    }

    @Test
    fun shellUsesSidebarWithoutGlobalTopClock() {
        compose.setContent {
            AppFrame {
                AppShell(TvScreen.Home, { }, Fake.profiles[0]) {
                    Text("Home content", color = TextPrimary, modifier = Modifier.testTag("home-content"))
                }
            }
        }
        compose.waitForIdle()
        writePng("30-shell-sidebar", capture())

        compose.onNodeWithTag("side-navigation-rail").fetchSemanticsNode()
        check(compose.onAllNodes(clockMatcher, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
            "the reference layout has no global clock in a crowded top header"
        }
    }

    /**
     * Where the new labels land, in pixels, on a 1920x1080 (960x540dp) panel. A
     * label that is composed but painted off the screen or under another row would
     * pass a text assertion, so the geometry is checked too.
     */
    @Test
    fun theClockAndFinishTimeSitInsideTheSafeArea() {
        val resumed = Fake.movieDetail.copy(resumePositionMs = 70 * 60_000L)
        compose.setContent {
            AppFrame {
                DetailScreen(resumed, emptyList(), null, { }, { }, { }, { _, _ -> }, { }, { }, { })
            }
        }
        compose.waitForIdle()

        val caption = compose.onAllNodesWithText("Ends at", substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes().single().boundsInRoot
        val clock = compose.onAllNodes(clockMatcher, useUnmergedTree = true)
            .fetchSemanticsNodes().single().boundsInRoot
        val playButton = compose.onAllNodesWithText("Play", substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes().first().boundsInRoot
        println("GEOMETRY caption=$caption clock=$clock playButton=$playButton")

        check(caption.left >= 0f && caption.top >= 0f && caption.right <= 1920f && caption.bottom <= 1080f) {
            "the finish-time caption must be fully on screen: $caption"
        }
        check(caption.top >= playButton.bottom) {
            "the finish-time caption must sit under the action row: caption=$caption play=$playButton"
        }
        check(clock.top <= 120f && clock.right >= 1920f - 240f) {
            "the clock belongs in the top-right corner: $clock"
        }
        check(clock.bottom <= caption.top) {
            "the clock must not reach down into the caption: clock=$clock caption=$caption"
        }
    }

    /** The playback clock must be hidden together with the controls until playback UI is shown. */
    @Test
    fun thePlayerHidesTheClockWhenPlaybackControlsAreHidden() {
        compose.setContent {
            AppFrame {
                PlayerScreen(
                    // A closed port: playback fails immediately, which is fine — this
                    // is about what the screen draws before any media arrives.
                    source = "http://127.0.0.1:1/never-plays.m3u8",
                    title = "Dune: Part Two",
                    subtitles = emptyList(),
                    cookie = null,
                    onBack = { },
                )
            }
        }
        compose.waitForIdle()
        writePng("31-player-clock", capture())
        val clocks = compose.onAllNodes(clockMatcher, useUnmergedTree = true).fetchSemanticsNodes()
        check(clocks.isEmpty()) { "the playback clock should be hidden with the playback controls" }
        // The seek bar and its finish time belong to that same hidden control row.
        val captions = compose.onAllNodesWithText("Ends at", substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes()
        check(captions.isEmpty()) { "the control row should start hidden, and the caption with it" }
    }

    private fun assertTextPresent(text: String, substring: Boolean = false) {
        val nodes = compose.onAllNodesWithText(text, substring = substring, useUnmergedTree = true)
            .fetchSemanticsNodes()
        check(nodes.isNotEmpty()) { "expected the composed screen to show \"$text\"" }
    }

    private fun assertClockPresent() {
        val nodes = compose.onAllNodes(clockMatcher, useUnmergedTree = true).fetchSemanticsNodes()
        check(nodes.isNotEmpty()) { "expected an HH:mm clock on the screen" }
    }

    @Test
    fun newHot() = shot("12-newhot") {
        AppShell(TvScreen.NewHot, { }, Fake.profiles[0]) {
            NewHotScreen(Fake.rows, onCard = { })
        }
    }

    @Test
    fun trendingScreenSeparatesShowsAndMoviesThenShowsReleasesAndComingSoon() {
        val trendingShow = Fake.cards(3).first().copy(mediaType = "tv", title = "Trending show fixture")
        val trendingMovie = Fake.cards(1).first().copy(mediaType = "movie", title = "Trending movie fixture")
        val weeklyEpisode = Fake.cards(6).first().copy(mediaType = "tv", title = "Weekly episode fixture")
        val release = Fake.cards(1).first().copy(mediaType = "movie", title = "New release fixture")
        val upcoming = Fake.cards(2).first().copy(mediaType = "movie", title = "Coming soon fixture")
        val newHotApiRows = listOf(
            HomeRow("new-this-week", "New This Week", listOf(release)),
            HomeRow("weekly-episodes", "New Episodes Every Week", listOf(weeklyEpisode)),
            HomeRow("coming-soon", "Coming Soon", listOf(upcoming)),
        )
        val homeApiRows = listOf(
            HomeRow("popular-movies", "Popular Movies", listOf(release)),
            HomeRow("trending", "Trending Now", listOf(trendingShow, trendingMovie)),
        )
        val rows = newHotRowsWithTrending(newHotApiRows, homeApiRows)
        compose.setContent {
            AppFrame { NewHotScreen(rows, onCard = { }) }
        }
        compose.waitForIdle()

        val sections = organizeNewHotRows(rows)
        val expectedTitles = listOf("Trending Shows", "Trending Movies", "New Releases", "Coming Soon")
        check(sections.map { it.title } == expectedTitles) {
            "trending page sections must be shows, movies, releases, then coming soon: ${sections.map { it.title }}"
        }
        check(sections[0].items.map { it.title } == listOf("Trending show fixture"))
        check(sections[1].items.map { it.title } == listOf("Trending movie fixture"))
        check(sections[2].items.map { it.title } == listOf("New release fixture", "Weekly episode fixture"))
        check(sections[3].items.map { it.title } == listOf("Coming soon fixture"))
        expectedTitles.forEach { compose.onNodeWithText(it, useUnmergedTree = true).fetchSemanticsNode() }
    }

    @Test
    fun myList() = shot("13-mylist") {
        AppShell(TvScreen.MyList, { }, Fake.profiles[0]) {
            GridScreen("My List", Fake.cards(4), onCard = { })
        }
    }

    @Test
    fun errorBanner() = shot("16-error-banner") {
        AppShell(TvScreen.Home, { }, Fake.profiles[0]) {
            Box(Modifier.fillMaxSize()) {
                HomeScreen(rows = Fake.rows, onCard = { })
                ErrorBanner(
                    "Cannot reach Streammore backend at http://192.168.3.221:3896: Connection refused",
                    Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
                )
            }
        }
    }

    /**
     * The focused appearance, rendered from state. Robolectric cannot grant real
     * focus (its DecorView chain is focusable=false, so View.requestFocus() is
     * refused), so both states are rendered side by side instead.
     */
    @Test
    fun focusStates() = shot("14-focus-states") {
        Column(Modifier.padding(60.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("Unfocused row", color = Muted, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(3) { FocusTile(focused = false) }
            }
            Text("Focused tile is the middle one", color = Muted, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                FocusTile(focused = false)
                FocusTile(focused = true)
                FocusTile(focused = false)
            }
        }
    }

    @Composable
    private fun FocusTile(focused: Boolean) {
        TvCardSurface(focused = focused, onClick = {}, modifier = Modifier.width(PosterWidth)) {
            Box(Modifier.size(PosterWidth, PosterHeight))
        }
    }

    /**
     * Hard assertion that the focus treatment renders: the focused tile must draw
     * the brand ring, which its unfocused siblings do not. Rendered as two rows in
     * one composition (a Compose rule only allows one setContent), compared by
     * counting brand-coloured pixels in each half of the capture.
     */
    @Test
    fun focusedRingRenders() {
        compose.setContent {
            AppFrame {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        FocusTile(focused = false)
                        FocusTile(focused = false)
                        FocusTile(focused = false)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        FocusTile(focused = false)
                        FocusTile(focused = true)
                        FocusTile(focused = false)
                    }
                }
            }
        }
        compose.waitForIdle()

        val bitmap = capture()
        writePng("25-focus-ring", bitmap)
        val half = bitmap.height / 2
        val unfocusedRow = purplePixels(Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, half))
        val focusedRow = purplePixels(Bitmap.createBitmap(bitmap, 0, half, bitmap.width, bitmap.height - half))
        println("FOCUS RING PIXELS unfocused=$unfocusedRow focused=$focusedRow")
        check(focusedRow > unfocusedRow + 300) {
            "the focused tile should draw the brand ring: unfocused=$unfocusedRow focused=$focusedRow"
        }
    }

    /**
     * Hard assertion on the avatar: the glyph must be centred in its circle and
     * fill it. A bare `Text` inside a fixed-size circle lands in the top half
     * instead, because an emoji's line box is far taller than the glyph and the
     * card's column wraps that height rather than centring it - the regression
     * this test exists to catch.
     */
    @Test
    fun profileAvatarIsCentredAndFillsItsCircle() {
        compose.setContent {
            AppFrame { ProfileScreen(listOf(Profile("p1", "Marco", "🦊", false)), null) { } }
        }
        compose.waitForIdle()

        val bitmap = capture()
        writePng("27-avatar", bitmap)

        // The single avatar is centred, so its equator is the widest run of
        // non-background pixels in the image.
        val background = bitmap.getPixel(4, 4)
        fun isBackground(x: Int, y: Int): Boolean {
            val p = bitmap.getPixel(x, y)
            return p == background
        }
        var equator = 0
        var left = 0
        var right = 0
        for (y in 0 until bitmap.height) {
            var runStart = -1
            for (x in 0 until bitmap.width) {
                val ink = !isBackground(x, y)
                if (ink && runStart < 0) runStart = x
                if ((!ink || x == bitmap.width - 1) && runStart >= 0) {
                    val runEnd = if (ink) x else x - 1
                    if (runEnd - runStart > right - left) {
                        left = runStart; right = runEnd; equator = y
                    }
                    runStart = -1
                }
            }
        }
        val cx = (left + right) / 2
        var top = equator
        while (top > 0 && !isBackground(cx, top - 1)) top--
        var bottom = equator
        while (bottom < bitmap.height - 1 && !isBackground(cx, bottom + 1)) bottom++
        val diameter = minOf(right - left + 1, bottom - top + 1)
        val circleCx = (left + right) / 2f
        val circleCy = (top + bottom) / 2f
        val radius = diameter / 2f

        // Ink inside the disc that is neither the card fill nor its ring.
        val fills = listOf(Panel.toArgb(), BorderIdle.toArgb())
        var minX = Int.MAX_VALUE
        var maxX = -1
        var minY = Int.MAX_VALUE
        var maxY = -1
        for (y in top..bottom) {
            for (x in left..right) {
                val dx = x - circleCx
                val dy = y - circleCy
                if (dx * dx + dy * dy > (radius - 6f) * (radius - 6f)) continue
                val p = bitmap.getPixel(x, y)
                if (fills.any { c ->
                        abs(Color.red(p) - Color.red(c)) <= 12 &&
                            abs(Color.green(p) - Color.green(c)) <= 12 &&
                            abs(Color.blue(p) - Color.blue(c)) <= 12
                    }) continue
                if (isBackground(x, y)) continue
                minX = minOf(minX, x); maxX = maxOf(maxX, x)
                minY = minOf(minY, y); maxY = maxOf(maxY, y)
            }
        }
        check(maxX >= minX && maxY >= minY) { "the avatar drew no glyph at all" }
        val glyphCx = (minX + maxX) / 2f
        val glyphCy = (minY + maxY) / 2f
        val fill = (maxY - minY + 1) / diameter.toFloat()
        println(
            "AVATAR circle=${diameter}px ink=${maxX - minX + 1}x${maxY - minY + 1} " +
                "offset=(${glyphCx - circleCx}, ${glyphCy - circleCy}) fill=$fill",
        )
        check(abs(glyphCx - circleCx) <= 6f) { "the avatar glyph is ${glyphCx - circleCx}px off centre horizontally" }
        check(abs(glyphCy - circleCy) <= 6f) { "the avatar glyph is ${glyphCy - circleCy}px off centre vertically (it used to sit in the top half)" }
        check(fill >= 0.5f) { "the avatar glyph only fills $fill of its circle" }
    }

    /** Confirms the dark scheme's red primary actually reaches Material3 components. */
    @Test
    fun themePrimary() {
        compose.setContent {
            AppFrame {
                Column(Modifier.padding(40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button({}) { Text("Sign in") }
                    OutlinedButton({}) { Text("+ My List") }
                    Button({}, enabled = false) { Text("Sign in (disabled)") }
                }
            }
        }
        compose.waitForIdle()
        val bitmap = capture()
        writePng("17-theme-primary", bitmap)
        val purple = purplePixels(bitmap)
        println("THEME PURPLE PIXELS (enabled Button) = $purple")
        check(purple > 1000) { "enabled Button should paint the purple primary; found $purple purple pixels" }
    }

    /** Long titles must ellipsise rather than clip mid-word. */
    @Test
    fun longTitles() = shot("15-long-titles") {
        Column(Modifier.padding(60.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    "A Very Long Title That Should Definitely Be Truncated Somewhere",
                    "Short",
                    "The Extraordinarily Improbable Journey Of A Gentleman",
                ).forEachIndexed { index, title ->
                    MediaCardView(Fake.cards(3)[index].copy(title = title, rating = 8.4, year = "2024"), {})
                }
            }
        }
    }
}

// ------------------------------------------------------------------ fixtures

internal object Fake {
    val profiles = listOf(
        Profile("p1", "Marco", "🦊", false),
        Profile("p2", "Kids", "K", true),
        Profile("p3", "Guest", "🦁", false),
    )

    fun cards(count: Int): List<MediaCard> = (1..count).map { i ->
        MediaCard(
            mediaType = if (i % 3 == 0) "tv" else "movie",
            tmdbId = 1000 + i,
            title = when (i % 4) {
                0 -> "A Very Long Title That Should Probably Be Truncated Somewhere"
                1 -> "Dune"
                2 -> "The Bear"
                else -> "Blade Runner 2049"
            },
            poster = "https://image.tmdb.org/t/p/w500/poster$i.jpg",
            year = "${2000 + i}",
            rating = 6.0 + (i % 40) / 10.0,
            percent = if (i % 5 == 0) 0.42 else 0.0,
            ribbon = if (i == 1) "NEW" else null,
        )
    }

    val billboard = Billboard(
        mediaType = "tv",
        tmdbId = 136315,
        title = "Reacher",
        overview = "Jack Reacher, a veteran military police investigator, has just recently entered civilian life. Reacher is a drifter, carrying no phone and the barest of essentials as he travels the country and explores the nation he once served.",
        backdrop = "https://image.tmdb.org/t/p/w1280/backdrop-reacher.jpg",
        poster = "https://image.tmdb.org/t/p/w500/poster-reacher.jpg",
        year = "2022",
        rating = 8.1,
        trailerKey = "dQw4w9WgXcQ",
    )

    /** Mirrors the backend's resume row: a still, an episode number and a position. */
    val resumeItems: List<MediaCard> = (1..6).map { i ->
        MediaCard(
            mediaType = "tv",
            tmdbId = 2000 + i,
            title = listOf("The Bear", "Reacher", "Monster: The Lizzie Borden Story")[i % 3],
            poster = "https://image.tmdb.org/t/p/w500/poster$i.jpg",
            backdrop = "https://image.tmdb.org/t/p/w780/backdrop$i.jpg",
            year = "2025",
            rating = 7.5 + (i % 10) / 10.0,
            season = 2,
            episode = i,
            episodeTitle = "Episode $i",
            positionSeconds = 300.0 * i,
            durationSeconds = 1800.0,
            percent = (300.0 * i) / 1800.0,
        )
    }

    val rows = listOf(
        HomeRow("continue", "Continue Watching", resumeItems),
        HomeRow("r2", "Trending Now", cards(8)),
        HomeRow("r3", "New Releases", cards(8)),
    )

    fun people(count: Int): List<Person> = (1..count).map {
        Person(id = 500 + it, name = "Actor Name $it", character = "Character $it", profile = "https://image.tmdb.org/t/p/w185/poster-profile$it.jpg")
    }

    val channels = (1..12).map { i ->
        // A guide window around "now", so the tile's programme progress bar renders.
        val now = System.currentTimeMillis()
        LiveChannel(
            id = "ch$i",
            channelId = "$i",
            name = "Channel $i",
            genre = if (i % 2 == 0) "Sports" else "Movies",
            country = "ZA",
            nowPlaying = LiveProgram(
                id = "now$i",
                title = "Live Match $i",
                startMs = now - 30 * 60_000L,
                endMs = now + 30 * 60_000L,
                isLive = true,
            ),
            nextPlaying = LiveProgram(
                id = "next$i",
                title = "Highlights $i",
                startMs = now + 30 * 60_000L,
                endMs = now + 90 * 60_000L,
            ),
        )
    }

    val movieDetail = TitleDetail(
        mediaType = "movie",
        tmdbId = 693134,
        title = "Dune: Part Two",
        overview = "Paul Atreides unites with Chani and the Fremen while seeking revenge against the conspirators who destroyed his family. Facing a choice between the love of his life and the fate of the known universe, he endeavors to prevent a terrible future only he can foresee.",
        backdrop = "https://image.tmdb.org/t/p/w1280/backdrop-dune.jpg",
        poster = "https://image.tmdb.org/t/p/w500/poster-dune.jpg",
        year = "2024",
        rating = 8.2,
        runtime = 167,
        tagline = "Long live the fighters.",
        inMyList = true,
        myRating = "up",
        progress = 0.42,
        cast = people(6),
    )

    val tvDetail = TitleDetail(
        mediaType = "tv",
        tmdbId = 136315,
        title = "The Bear",
        overview = "A young chef from the fine dining world returns to Chicago to run his family's sandwich shop.",
        backdrop = "https://image.tmdb.org/t/p/w1280/backdrop-bear.jpg",
        poster = "https://image.tmdb.org/t/p/w500/poster-bear.jpg",
        year = "2022",
        rating = 8.6,
        runtime = 30,
        tagline = "Every second counts.",
        seasons = listOf(Season(1, "Season 1", 8), Season(2, "Season 2", 10), Season(3, "Season 3", 10)),
        cast = people(4),
    )

    val episodes = (1..6).map {
        Episode(
            it,
            "Episode $it: A Reasonably Long Episode Title For Testing",
            null,
            null,
            // The season route has always sent a per-episode runtime; the finish
            // time on the detail page is derived from it.
            runtime = 40 + it,
        )
    }
}
