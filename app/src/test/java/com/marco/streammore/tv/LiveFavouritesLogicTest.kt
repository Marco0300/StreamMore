package com.marco.streammore.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Per-profile Live TV favourites: list mutation and the filtered channel grid. */
class LiveFavouritesLogicTest {

    private fun channel(id: String, name: String = "Channel $id", genre: String = "Sports") = LiveChannel(
        id = "flyx:live:$id",
        channelId = id,
        name = name,
        genre = genre,
        country = "ZA",
    )

    @Test
    fun addingAFavouriteAppendsWithoutDuplicates() {
        assertEquals(listOf("1", "2"), withFavourite(listOf("1"), "2", favourite = true))
        assertEquals(listOf("1", "2"), withFavourite(listOf("1", "2"), "2", favourite = true))
    }

    @Test
    fun removingAFavouriteDropsOnlyThatChannel() {
        assertEquals(listOf("1", "3"), withFavourite(listOf("1", "2", "3"), "2", favourite = false))
        assertEquals(emptyList<String>(), withFavourite(listOf("2"), "2", favourite = false))
    }

    @Test
    fun pinnedStateMatchesEitherTheBareIdOrTheCatalogueId() {
        val subject = channel("42")
        assertTrue(isFavourite(listOf("42"), subject))
        assertTrue(isFavourite(listOf("flyx:live:42"), subject))
        assertFalse(isFavourite(listOf("41"), subject))
    }

    @Test
    fun favouritesIsAlwaysAvailableAlongsideTheGenres() {
        val categories = liveCategories(listOf(channel("1", genre = "Movies"), channel("2", genre = "Sports")))
        assertEquals(listOf("All", "Favourites", "Movies", "Sports"), categories)
    }

    @Test
    fun favouritesFilterShowsOnlyPinnedChannels() {
        val channels = listOf(channel("1"), channel("2"), channel("3"))

        val filtered = liveFilteredChannels(channels, "Favourites", "", listOf("2", "3"))

        assertEquals(listOf("2", "3"), filtered.map { it.channelId })
    }

    @Test
    fun searchStillAppliesInsideTheFavouritesFilter() {
        val channels = listOf(channel("1", name = "SuperSport 1"), channel("2", name = "MNet"))

        val filtered = liveFilteredChannels(channels, "Favourites", "super", listOf("1", "2"))

        assertEquals(listOf("1"), filtered.map { it.channelId })
    }

    @Test
    fun genreAndSearchStillWorkForTheNormalCategories() {
        val channels = listOf(channel("1", name = "SuperSport 1", genre = "Sports"), channel("2", name = "MNet", genre = "Movies"))

        assertEquals(listOf("1"), liveFilteredChannels(channels, "Sports", "", emptyList()).map { it.channelId })
        assertEquals(listOf("2"), liveFilteredChannels(channels, "All", "mnet", emptyList()).map { it.channelId })
    }
}