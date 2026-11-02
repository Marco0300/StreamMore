package com.marco.streammore.tv

import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import coil.test.FakeImageLoaderEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Rendering, reachability and selection tests for the four new surfaces. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi-notouch")
class NewScreensTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun installFakeImages() {
        val engine = FakeImageLoaderEngine.Builder()
            .intercept({ it is String && it.contains("backdrop") }, ColorDrawable(0xFF3E4C6B.toInt()))
            .intercept({ it is String && it.contains("poster") }, ColorDrawable(0xFF3C3C3C.toInt()))
            .default(ColorDrawable(0xFF2A2A2A.toInt()))
            .build()
        Coil.setImageLoader(
            ImageLoader.Builder(ApplicationProvider.getApplicationContext()).components { add(engine) }.build(),
        )
    }

    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        MaterialTheme(colorScheme = StreammoreScheme) {
            Surface(Modifier.fillMaxSize(), color = Bg) { content() }
        }
    }

    private fun entry(id: Int, title: String, dayIndex: Int) = CalendarEntry(
        tmdbId = id,
        title = title,
        poster = "https://image.tmdb.org/t/p/w500/poster$id.jpg",
        year = "2020",
        rating = 8.0,
        status = "Returning Series",
        episode = CalendarEpisode(
            season = 2,
            episode = 3,
            airDate = "2026-11-04",
            name = "Episode Three",
            dayIndex = dayIndex,
            aired = dayIndex < 0,
        ),
    )

    private val calendar = CalendarData(
        days = listOf(
            CalendarDay("2026-11-04", 0, listOf(entry(1396, "Breaking Bad", 0))),
            CalendarDay("2026-11-05", 1, listOf(entry(2316, "The Office", 1))),
        ),
        recent = listOf(entry(94997, "House of the Dragon", -2)),
        counts = CalendarCounts(checked = 3, upcoming = 2, today = 1, recent = 1),
        followed = 2,
        cached = true,
    )

    // ── (a) Upcoming ────────────────────────────────────────────────────────

    @Test
    fun upcomingSitsDirectlyAfterNewAndHotInTheSidebarAndNavigates() {
        val destinations = mutableListOf<TvScreen>()
        compose.setContent {
            Frame {
                AppShell(TvScreen.Home, { destinations += it }, Fake.profiles[0]) {
                    androidx.compose.material3.Text("Home")
                }
            }
        }
        compose.waitForIdle()

        val newHot = compose.onNodeWithTag("side-nav-item-new-hot").fetchSemanticsNode().boundsInRoot
        val upcoming = compose.onNodeWithTag("side-nav-item-upcoming").fetchSemanticsNode().boundsInRoot
        val shows = compose.onNodeWithTag("side-nav-item-tv-shows").fetchSemanticsNode().boundsInRoot
        assertTrue("Upcoming must sit below New & Hot", newHot.top < upcoming.top)
        assertTrue("Upcoming must sit above TV Shows", upcoming.top < shows.top)

        compose.onNodeWithTag("side-nav-item-upcoming").performClick()
        compose.runOnIdle { assertEquals(listOf<TvScreen>(TvScreen.Upcoming), destinations) }
        compose.onNodeWithContentDescription("Upcoming").assertExists()
    }

    @Test
    fun upcomingGroupsByDayShowsJustAiredAndOpensTheShow() {
        var opened: CalendarEntry? = null
        compose.setContent {
            Frame {
                AppShell(TvScreen.Upcoming, { }, Fake.profiles[0]) {
                    UpcomingScreen(calendar, null) { opened = it }
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Today").assertExists()
        compose.onNodeWithText("Tomorrow").assertExists()
        compose.onNodeWithText("Just aired").assertExists()
        compose.onNodeWithText("2 episodes").assertExists()
        compose.onNodeWithText("Air dates for the 2 shows you are watching · refreshed periodically.").assertExists()
        compose.onAllNodesWithText("S02E03", substring = true)[0].assertExists()

        compose.onNodeWithTag("upcoming-entry-1396").performClick()
        compose.runOnIdle { assertEquals(1396, opened?.tmdbId) }
    }

    @Test
    fun upcomingEmptyStateExplainsHowToPopulateIt() {
        compose.setContent {
            Frame {
                AppShell(TvScreen.Upcoming, { }, Fake.profiles[0]) {
                    UpcomingScreen(CalendarData(followed = 0), null) { }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Watch a few episodes of a show and its next air dates will show up here.").assertExists()
    }

    @Test
    fun upcomingEmptyStateDiffersWhenShowsAreFollowed() {
        compose.setContent {
            Frame {
                AppShell(TvScreen.Upcoming, { }, Fake.profiles[0]) {
                    UpcomingScreen(CalendarData(followed = 4), null) { }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Nothing scheduled in the next two weeks.").assertExists()
    }

    // ── (b) Live TV favourites ──────────────────────────────────────────────

    @Test
    fun liveTvHasAFavouritesFilterAndADpadReachableStar() {
        var toggled: LiveChannel? = null
        compose.setContent {
            Frame {
                AppShell(TvScreen.Live, { }, Fake.profiles[0]) {
                    LiveScreen(
                        Fake.channels, null, { }, { },
                        favouriteIds = listOf("2"),
                        onToggleFavourite = { toggled = it },
                    )
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Favourites").assertExists()
        compose.onAllNodesWithContentDescription("Remove from favourites")[0].assertExists()
        compose.onAllNodesWithContentDescription("Add to favourites")[0].performClick()
        compose.runOnIdle { assertEquals("1", toggled?.channelId) }

        compose.onNodeWithText("Favourites").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Channel 2").assertExists()
        assertTrue(
            "Only pinned channels may render under Favourites",
            compose.onAllNodesWithText("Channel 1").fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun favouritesFilterStillRendersTheEmptyStateWhenNothingIsPinned() {
        compose.setContent {
            Frame {
                AppShell(TvScreen.Live, { }, Fake.profiles[0]) {
                    LiveScreen(Fake.channels, null, { }, { }, favouriteIds = emptyList())
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Favourites").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("No channels match this search or category.").assertExists()
    }

    // ── (c) Movie collection row ────────────────────────────────────────────

    private val collection = TitleCollection(
        id = 726871,
        name = "Dune Collection",
        parts = listOf(
            MediaCard("movie", 438631, "Dune", "https://image.tmdb.org/t/p/w500/poster-a.jpg", year = "2021", rating = 7.8),
            MediaCard("movie", 693134, "Dune: Part Two", "https://image.tmdb.org/t/p/w500/poster-dune.jpg", year = "2024", rating = 8.2),
            MediaCard("movie", 1000001, "Dune: Part Three", "https://image.tmdb.org/t/p/w500/poster-c.jpg", year = "2027", rating = null),
        ),
    )

    @Test
    fun movieDetailShowsTheFranchiseMarksTheCurrentFilmAndOpensAPart() {
        var opened: MediaCard? = null
        compose.setContent {
            Frame {
                DetailScreen(
                    Fake.movieDetail.copy(collection = collection),
                    emptyList(), null, { }, { }, { }, { _, _ -> }, { }, { }, { },
                    onCollectionPart = { opened = it },
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("detail-content")
            .performScrollToNode(hasTestTag("collection-part-1000001"))

        compose.onNodeWithText("Dune Collection").assertExists()
        compose.onNodeWithText("3 films").assertExists()
        compose.onNodeWithText("You are here").assertExists()

        compose.onNodeWithTag("collection-part-1000001").performClick()
        compose.runOnIdle { assertEquals(1000001, opened?.tmdbId) }
    }

    @Test
    fun aMovieWithoutACollectionShowsNoFranchiseRow() {
        compose.setContent {
            Frame {
                DetailScreen(Fake.movieDetail, emptyList(), null, { }, { }, { }, { _, _ -> }, { }, { }, { })
            }
        }
        compose.waitForIdle()
        assertTrue(
            "No collection heading is expected for a standalone film",
            compose.onAllNodesWithText("You are here").fetchSemanticsNodes().isEmpty(),
        )
    }

    // ── (d) Person page ─────────────────────────────────────────────────────

    private val person = PersonDetail(
        personId = 287,
        name = "Brad Pitt",
        biography = "A".repeat(500),
        photo = "https://image.tmdb.org/t/p/w300/brad.jpg",
        knownFor = "Acting",
        birthday = "1963-12-18",
        placeOfBirth = "Shawnee, Oklahoma, USA",
        movies = listOf(
            MediaCard("movie", 550, "Fight Club", "https://image.tmdb.org/t/p/w500/poster-fc.jpg", year = "1999", rating = 8.4),
            MediaCard("movie", 19995, "Avatar", "https://image.tmdb.org/t/p/w500/poster-av.jpg", year = "2009", rating = 7.6),
        ),
        shows = listOf(
            MediaCard("tv", 2001, "The Bear", "https://image.tmdb.org/t/p/w500/poster-bear.jpg", year = "2022", rating = 8.6),
        ),
    )

    @Test
    fun personPageShowsFactsClampedBiographyBothCreditsAndOpensATitle() {
        var back = 0
        var opened: MediaCard? = null
        compose.setContent {
            Frame { PersonScreen(person, null, onBack = { back++ }, onCard = { opened = it }) }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Brad Pitt").assertExists()
        compose.onNodeWithText("Acting · 1963-12-18 · Shawnee, Oklahoma, USA").assertExists()
        compose.onNodeWithText("Read more").assertExists()
        compose.onNodeWithText("Read more").performClick()
        compose.onNodeWithText("Show less").assertExists()

        compose.onNodeWithTag("person-content").performScrollToNode(hasTestTag("person-card-550"))
        compose.onNodeWithText("Movies (2)").assertExists()
        compose.onNodeWithTag("person-card-550").performClick()
        compose.runOnIdle { assertEquals(550, opened?.tmdbId) }

        compose.onNodeWithTag("person-content").performScrollToNode(hasTestTag("person-card-2001"))
        compose.onNodeWithText("TV Shows (1)").assertExists()
        compose.onNodeWithTag("person-card-2001").performClick()
        compose.runOnIdle { assertEquals(2001, opened?.tmdbId) }

        compose.onNodeWithText("‹ Back").performClick()
        compose.runOnIdle { assertEquals(1, back) }
    }

    @Test
    fun castCardsOnTheDetailPageOpenThePersonPage() {
        var opened: Person? = null
        compose.setContent {
            Frame {
                DetailScreen(Fake.movieDetail, emptyList(), null, { }, { }, { }, { _, _ -> }, { }, { }, { },
                    onPerson = { opened = it })
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("detail-content").performScrollToNode(hasTestTag("cast-card-501"))
        compose.onNodeWithTag("cast-card-501").performClick()
        compose.runOnIdle { assertEquals(501, opened?.id) }
    }
}