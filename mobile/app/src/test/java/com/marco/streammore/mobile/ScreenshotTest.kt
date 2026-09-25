package com.marco.streammore.mobile

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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

    /** Counts pixels bright enough to be the focused tile's white border. */
    private fun whitePixels(bitmap: Bitmap): Int {
        var count = 0
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.red(pixel) > 220 && Color.green(pixel) > 220 && Color.blue(pixel) > 220) count++
            }
        }
        return count
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
        LoginScreen(loading = false, error = "Cannot reach Streammore backend at http://192.168.3.91:3896: Connection refused") { _, _ -> }
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
    fun search() = shot("06-search") {
        AppShell(TvScreen.Search, { }, Fake.profiles[0]) {
            SearchScreen(Fake.cards(6), { }) { }
        }
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
    fun activity() = shot("09-activity") {
        AppShell(TvScreen.Activity, { }, Fake.profiles[0]) {
            ActivityScreen(Fake.activity)
        }
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

    @Test
    fun newHot() = shot("12-newhot") {
        AppShell(TvScreen.NewHot, { }, Fake.profiles[0]) {
            NewHotScreen(Fake.rows, onCard = { })
        }
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
                    "Cannot reach Streammore backend at http://192.168.3.91:3896: Connection refused",
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
     * a bright border that the unfocused tiles do not.
     */
    @Test
    fun focusedBorderRenders() {
        compose.setContent {
            AppFrame {
                Column(Modifier.padding(40.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        FocusTile(focused = false)
                        FocusTile(focused = true)
                        FocusTile(focused = false)
                    }
                }
            }
        }
        compose.waitForIdle()

        val focusedCount = whitePixels(capture())
        println("FOCUS WHITE PIXELS (1 focused of 3) = $focusedCount")
        check(focusedCount > 300) {
            "focused tile should draw a bright border; found $focusedCount near-white pixels"
        }
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
        Profile("p1", "Marco", null, false),
        Profile("p2", "Kids", "K", true),
        Profile("p3", "Guest", null, false),
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

    val rows = listOf(
        HomeRow("r1", "Continue Watching", cards(8)),
        HomeRow("r2", "Trending Now", cards(8)),
        HomeRow("r3", "New Releases", cards(8)),
    )

    fun people(count: Int): List<Person> = (1..count).map {
        Person(id = 500 + it, name = "Actor Name $it", character = "Character $it", profile = "https://image.tmdb.org/t/p/w185/poster-profile$it.jpg")
    }

    val channels = (1..12).map {
        LiveChannel("ch$it", "$it", "Channel $it", if (it % 2 == 0) "Sports" else "Movies", "ZA")
    }

    val activity = listOf(
        ActivityEntry("movie", 693134, "Dune: Part Two", null, 0.42, null, null, null),
        ActivityEntry("tv", 136315, "The Bear", null, 0.87, 3, 4, null),
    )

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
        Episode(it, "Episode $it: A Reasonably Long Episode Title For Testing", null, null)
    }
}
