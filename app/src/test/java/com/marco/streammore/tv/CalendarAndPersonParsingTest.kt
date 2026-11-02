package com.marco.streammore.tv

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real payloads copied from the browser's endpoints. Every parser has to accept
 * partial data: TMDB regularly omits an episode name, a poster or a whole
 * credits block.
 */
class CalendarAndPersonParsingTest {

    private val calendarJson = """
        {
          "upcoming": [
            {"tmdbId":1396,"title":"Breaking Bad","poster":"https://image.tmdb.org/t/p/w500/bb.jpg","year":"2008","rating":8.9,"status":"Ended",
             "episode":{"season":5,"episode":16,"airDate":"2026-11-04","name":"Felina","dayIndex":0,"aired":false}}
          ],
          "recent": [
            {"tmdbId":94997,"title":"House of the Dragon","poster":null,"year":"2022","rating":8.4,"status":"Returning Series",
             "episode":{"season":2,"episode":3,"airDate":"2026-11-01","name":null,"dayIndex":-1,"aired":true}}
          ],
          "days": [
            {"date":"2026-11-04","dayIndex":0,"items":[
              {"tmdbId":1396,"title":"Breaking Bad","poster":"https://image.tmdb.org/t/p/w500/bb.jpg","year":"2008","rating":8.9,"status":"Ended",
               "episode":{"season":5,"episode":16,"airDate":"2026-11-04","name":"Felina","dayIndex":0,"aired":false}}
            ]},
            {"date":"2026-11-05","dayIndex":1,"items":[
              {"tmdbId":2316,"title":"The Office","year":"2005","episode":{"season":3,"episode":1,"airDate":"2026-11-05","dayIndex":1,"aired":false}}
            ]}
          ],
          "counts": {"checked":4,"upcoming":2,"today":1,"recent":1},
          "profileId": "p1",
          "followed": 3,
          "fetched": 4,
          "cached": true
        }
    """.trimIndent()

    @Test
    fun calendarPayloadParsesDaysRecentAndCounts() {
        val data = JSONObject(calendarJson).toCalendarData()

        assertEquals(2, data.days.size)
        assertEquals("2026-11-04", data.days[0].date)
        assertEquals(0, data.days[0].dayIndex)
        assertEquals(1, data.days[0].items.size)
        assertEquals("Breaking Bad", data.days[0].items[0].title)
        assertEquals(5, data.days[0].items[0].episode?.season)
        assertEquals("Felina", data.days[0].items[0].episode?.name)
        assertEquals(false, data.days[0].items[0].episode?.aired)
        assertEquals(8.9, data.days[0].items[0].rating!!, 0.001)
        assertEquals(1, data.recent.size)
        assertEquals(true, data.recent[0].episode?.aired)
        assertEquals(4, data.counts.checked)
        assertEquals(2, data.counts.upcoming)
        assertEquals(1, data.counts.today)
        assertEquals(1, data.counts.recent)
        assertEquals(3, data.followed)
        assertTrue(data.cached)
    }

    @Test
    fun calendarPayloadToleratesMissingFields() {
        val data = JSONObject("""{"profileId":"p1"}""").toCalendarData()

        assertTrue(data.days.isEmpty())
        assertTrue(data.recent.isEmpty())
        assertEquals(0, data.counts.upcoming)
        assertEquals(0, data.followed)
        assertEquals(false, data.cached)
    }

    @Test
    fun calendarEntryWithoutAnEpisodeIsStillUsable() {
        val data = JSONObject("""{"days":[{"date":"2026-11-04","dayIndex":0,"items":[{"tmdbId":7,"title":"Film"}]}]}""")
            .toCalendarData()

        assertEquals(1, data.days[0].items.size)
        assertNull(data.days[0].items[0].episode)
        assertEquals("Film", data.days[0].items[0].title)
    }

    private val personJson = """
        {
          "personId": 287,
          "name": "Brad Pitt",
          "biography": "William Bradley Pitt is an American actor and film producer.",
          "profile": "https://image.tmdb.org/t/p/w300/brad.jpg",
          "knownFor": "Acting",
          "birthday": "1963-12-18",
          "placeOfBirth": "Shawnee, Oklahoma, USA",
          "credits": {
            "movie": [
              {"mediaType":"movie","tmdbId":550,"title":"Fight Club","poster":"https://image.tmdb.org/t/p/w500/fc.jpg","year":"1999","rating":8.4},
              {"mediaType":"movie","tmdbId":19995,"title":"Avatar","poster":null,"year":"2009","rating":7.6}
            ],
            "tv": [
              {"mediaType":"tv","tmdbId":1396,"title":"Breaking Bad","year":"2008","rating":9.5}
            ]
          }
        }
    """.trimIndent()

    @Test
    fun personPayloadParsesFactsAndCredits() {
        val person = JSONObject(personJson).toPersonDetail()

        assertEquals(287, person.personId)
        assertEquals("Brad Pitt", person.name)
        assertEquals("https://image.tmdb.org/t/p/w300/brad.jpg", person.photo)
        assertEquals("Acting", person.knownFor)
        assertEquals("1963-12-18", person.birthday)
        assertEquals("Shawnee, Oklahoma, USA", person.placeOfBirth)
        assertEquals("William Bradley Pitt is an American actor and film producer.", person.biography)
        assertEquals(listOf("Fight Club", "Avatar"), person.movies.map { it.title })
        assertEquals(listOf("Breaking Bad"), person.shows.map { it.title })
        assertEquals(3, personCreditCount(person))
    }

    @Test
    fun personPayloadToleratesMissingCreditsAndBlankValues() {
        val person = JSONObject("""{"personId":9,"name":"Nobody"}""").toPersonDetail()

        assertEquals(9, person.personId)
        assertTrue(person.movies.isEmpty())
        assertTrue(person.shows.isEmpty())
        assertTrue(person.biography.isBlank())
        assertNull(person.photo)
        assertEquals(0, personCreditCount(person))
    }

    @Test
    fun personFactsSkipBlankValuesAndJoinWithDots() {
        val person = JSONObject(personJson).toPersonDetail()
        assertEquals("Acting · 1963-12-18 · Shawnee, Oklahoma, USA", personFacts(person))
        assertEquals("Acting", personFacts(person.copy(birthday = null, placeOfBirth = "  ")))
        assertEquals("", personFacts(null))
    }

    @Test
    fun biographyClampsAt420CharactersAndExpands() {
        val long = "A".repeat(500)
        assertEquals(421, clampBiography(long, expanded = false).length)
        assertTrue(clampBiography(long, expanded = false).endsWith("…"))
        assertEquals(500, clampBiography(long, expanded = true).length)
        assertEquals("Short bio", clampBiography("Short bio", expanded = false))
        assertEquals("", clampBiography(null, expanded = false))
    }

    private val collectionJson = """
        {
          "id": 726871,
          "name": "Dune Collection",
          "overview": "Paul Atreides and the Fremen.",
          "poster": "https://image.tmdb.org/t/p/w500/dune-collection.jpg",
          "parts": [
            {"mediaType":"movie","tmdbId":438631,"title":"Dune","poster":"https://image.tmdb.org/t/p/w500/dune1.jpg","year":"2021","rating":7.8,"releaseDate":"2021-09-15"},
            {"mediaType":"movie","tmdbId":693134,"title":"Dune: Part Two","poster":"https://image.tmdb.org/t/p/w500/dune2.jpg","year":"2024","rating":8.2,"releaseDate":"2024-02-27"}
          ]
        }
    """.trimIndent()

    @Test
    fun collectionParsesPartsInDeliveredOrder() {
        val collection = JSONObject(collectionJson).toCollection()

        assertEquals(726871, collection.id)
        assertEquals("Dune Collection", collection.name)
        assertEquals(listOf("Dune", "Dune: Part Two"), collection.parts.map { it.title })
        assertEquals(listOf("movie", "movie"), collection.parts.map { it.mediaType })
        assertEquals(438631, collectionParts(collection).first().tmdbId)
    }

    @Test
    fun aSinglePartIsNotAFranchiseRow() {
        val single = JSONObject(
            """{"id":1,"name":"One Off","parts":[{"mediaType":"movie","tmdbId":2,"title":"Only"}]}""",
        ).toCollection()
        assertTrue(collectionParts(single).isEmpty())
        assertTrue(collectionParts(null).isEmpty())
    }

    @Test
    fun collectionMetaMatchesTheWebCardCaption() {
        val part = JSONObject(collectionJson).toCollection().parts[1]
        assertEquals("2024 · ★ 8.2", collectionMeta(part))
        assertEquals("2024", collectionMeta(part.copy(rating = null)))
        assertEquals("", collectionMeta(MediaCard("movie", 1, "No meta")))
    }
}