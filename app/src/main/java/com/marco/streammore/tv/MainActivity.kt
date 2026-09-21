package com.marco.streammore.tv

import android.app.Activity
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import androidx.compose.runtime.snapshotFlow

// ── Palette ──────────────────────────────────────────────────────────────────
//
// The scheme uses a cool purple accent and must be built with darkColorScheme(): copying the *light* scheme and
// overriding only primary/background/surface leaves onBackground and onSurface
// near-black, which renders every default-coloured Text invisible on the black
// background.
internal val Purple = Color(0xFF9B6DFF)
internal val Bg = Color(0xFF0A0810)
internal val Panel = Color(0xFF181321)
internal val PanelFocused = Color(0xFF30204A)
internal val Muted = Color(0xFFBDB4CC)
internal val TextPrimary = Color(0xFFF7F3FF)
internal val BorderIdle = Color(0xFF493A5F)
internal val BorderFocused = Color(0xFFFFFFFF)
internal val ErrorText = Color(0xFFFF8B90)
internal val ErrorFill = Color(0xFF5B171B)
internal val WarnYellow = Color(0xFFFFD54F)
internal val MatchGreen = Color(0xFF46D369)
internal val MetaText = Color(0xFFE5E5E5)
internal val BadgeBorder = Color(0xFF777777)

internal data class AvailableVideoQuality(
    val label: String,
    val group: TrackGroup,
    val trackIndices: List<Int>,
)

/**
 * Mirrors the web player's HLS level menu: expose only video renditions that
 * Media3 actually discovered in the source's current track groups.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun availableVideoQualities(tracks: Tracks): List<AvailableVideoQuality> {
    return tracks.groups
        .filter { it.type == C.TRACK_TYPE_VIDEO && it.isSupported(false) }
        .flatMap { group ->
            val byLabel = linkedMapOf<String, MutableList<Int>>()
            for (index in 0 until group.length) {
                val format = group.getTrackFormat(index)
                val label = when {
                    format.height > 0 -> "${format.height}p"
                    format.bitrate > 0 -> "${format.bitrate / 1000}kbps"
                    else -> "Video ${index + 1}"
                }
                byLabel.getOrPut(label) { mutableListOf() }.add(index)
            }
            byLabel.map { (label, indices) -> AvailableVideoQuality(label, group.mediaTrackGroup, indices) }
        }
        .distinctBy { "${it.group.id}:${it.label}" }
        .sortedWith(compareByDescending<AvailableVideoQuality> { it.label.removeSuffix("p").toIntOrNull() ?: 0 }.thenBy { it.label })
}
internal val StreammoreScheme = darkColorScheme(
    primary = Purple,
    onPrimary = Color.White,
    background = Bg,
    onBackground = TextPrimary,
    surface = Panel,
    onSurface = TextPrimary,
    surfaceVariant = Panel,
    onSurfaceVariant = Muted,
    outline = BorderIdle,
    error = ErrorText,
    onError = Color.Black,
)

// ── Metrics ──────────────────────────────────────────────────────────────────
//
// A 1080p Android TV panel is 960x540dp at xhdpi. A 270dp-tall card is half the
// viewport, so rows are sized to keep two full rows on screen.
private val Gutter = 42.dp
internal val PosterWidth = 104.dp
internal val PosterHeight = 150.dp
private val NavHeight = 54.dp

internal fun formatPlayerTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1000L).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

internal sealed interface TvScreen {
    data object Login : TvScreen
    data object Profiles : TvScreen
    data class ProfilePin(val profileId: String) : TvScreen
    data object Home : TvScreen
    data class Browse(val mediaType: String) : TvScreen
    data object Search : TvScreen
    data object NewHot : TvScreen
    data object MyList : TvScreen
    data object Activity : TvScreen
    data object Live : TvScreen
    data class Detail(val mediaType: String, val tmdbId: Int) : TvScreen
    data class Player(
        val source: String,
        val title: String,
        val subtitles: List<SubtitleTrack> = emptyList(),
        val nextEpisode: NextEpisodeInfo? = null,
        val mediaType: String? = null,
        val tmdbId: Int? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val initialPositionMs: Long? = null,
        val sources: List<StreamSource> = emptyList(),
        val introEndSeconds: Long? = null,
        val recapEndSeconds: Long? = null,
    ) : TvScreen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { StreammoreTvApp() }
    }
}

@Composable
internal fun StreammoreTvApp() {
    val context = LocalContext.current
    val api = remember(context) { StreammoreApi(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf<TvScreen>(TvScreen.Login) }
    var profiles by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var pendingProfile by remember { mutableStateOf<Profile?>(null) }
    var profileId by remember { mutableStateOf<String?>(null) }
    var rows by remember { mutableStateOf<List<HomeRow>>(emptyList()) }
    var billboard by remember { mutableStateOf<Billboard?>(null) }
    var autoplayPreviews by remember { mutableStateOf(true) }
    var cards by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var myListKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var contextCard by remember { mutableStateOf<MediaCard?>(null) }
    var browsePage by remember { mutableStateOf(1) }
    var browseTotalPages by remember { mutableStateOf(1) }
    var browseTotalResults by remember { mutableStateOf(0) }
    var browseGenres by remember { mutableStateOf<List<GenreOption>>(emptyList()) }
    var browseGenreId by remember { mutableStateOf<Int?>(null) }
    var browseLoadingMore by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<TitleDetail?>(null) }
    var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var liveChannels by remember { mutableStateOf<List<LiveChannel>>(emptyList()) }
    var activity by remember { mutableStateOf<List<ActivityEntry>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<AppUpdate?>(null) }
    var updateDismissed by remember { mutableStateOf(false) }
    var updateBusy by remember { mutableStateOf(false) }
    var updateError by remember { mutableStateOf<String?>(null) }
    // The screen that opened the player. Back from playback returns there instead
    // of always dumping the user back onto Home.
    var playerReturn by remember { mutableStateOf<TvScreen?>(null) }

    fun loadProfiles() = scope.launch {
        loading = true
        runCatching { api.profiles() }.onSuccess { profiles = it; screen = TvScreen.Profiles }
            .onFailure { error = it.message ?: "Could not load profiles" }
        loading = false
    }
    fun loadHome(id: String) = scope.launch {
        profileId = id; loading = true
        runCatching { api.home(id) }
            .onSuccess { data ->
                rows = data.rows
                billboard = data.billboard
                autoplayPreviews = data.autoplayPreviews
                runCatching { api.myList(id) }.onSuccess { list ->
                    myListKeys = list.map { "${it.mediaType}:${it.tmdbId}" }.toSet()
                }
                Log.d(
                    "StreammoreHero",
                    "billboard=${data.billboard?.title} type=${data.billboard?.mediaType} " +
                        "trailerKey=${data.billboard?.trailerKey} autoplayPreviews=${data.autoplayPreviews}",
                )
                screen = TvScreen.Home
            }
            .onFailure { error = it.message ?: "Could not load Home" }
        loading = false
    }
    fun loadCards(mediaType: String) = scope.launch {
        val id = profileId ?: return@launch
        loading = true
        runCatching {
            val page = api.browse(mediaType, id, 1)
            val genres = api.genres(id)[mediaType].orEmpty()
            page to genres
        }.onSuccess { (page, genres) ->
            cards = page.items
            browsePage = page.page
            browseTotalPages = page.totalPages
            browseTotalResults = page.totalResults
            browseGenres = genres
            browseGenreId = null
            screen = TvScreen.Browse(mediaType)
        }.onFailure { error = it.message ?: "Could not load catalogue" }
        loading = false
    }
    fun loadBrowseGenre(mediaType: String, genre: GenreOption?) = scope.launch {
        val id = profileId ?: return@launch
        loading = true
        runCatching {
            if (genre == null) api.browse(mediaType, id, 1) else api.genre(mediaType, genre.id, id, 1)
        }.onSuccess { page ->
            cards = page.items
            browsePage = page.page
            browseTotalPages = page.totalPages
            browseTotalResults = page.totalResults
            browseGenreId = genre?.id
        }.onFailure { error = it.message ?: "Could not filter catalogue" }
        loading = false
    }
    fun loadMoreBrowse(mediaType: String) = scope.launch {
        val id = profileId ?: return@launch
        if (browseLoadingMore || browsePage >= browseTotalPages) return@launch
        browseLoadingMore = true
        runCatching {
            val nextPage = browsePage + 1
            if (browseGenreId == null) api.browse(mediaType, id, nextPage)
            else api.genre(mediaType, browseGenreId!!, id, nextPage)
        }.onSuccess { page ->
            cards = cards + page.items
            browsePage = page.page
            browseTotalPages = page.totalPages
            browseTotalResults = page.totalResults
        }.onFailure { error = it.message ?: "Could not load more titles" }
        browseLoadingMore = false
    }
    fun openDetail(card: MediaCard) = scope.launch {
        val id = profileId ?: return@launch
        loading = true; detail = null; episodes = emptyList(); screen = TvScreen.Detail(card.mediaType, card.tmdbId)
        runCatching { api.detail(card.mediaType, card.tmdbId, id) }
            .onSuccess { loaded ->
                detail = loaded
                // Open the season containing the saved episode so the detail page
                // and its Play button agree about the resume location.
                val first = loaded.seasons.firstOrNull()
                val resumeSeason = loaded.resumeSeason ?: first?.number
                if (loaded.mediaType == "tv" && resumeSeason != null) {
                    runCatching { api.season(loaded.tmdbId, resumeSeason, id) }.onSuccess { episodes = it }
                }
            }
            .onFailure { error = it.message ?: "Could not load title" }
        loading = false
    }
    fun play(
        mediaType: String,
        tmdbId: Int,
        title: String,
        season: Int? = null,
        episode: Int? = null,
        nextEpisode: NextEpisodeInfo? = null,
        initialPositionMs: Long? = null,
        introEndSeconds: Long? = null,
        recapEndSeconds: Long? = null,
    ) = scope.launch {
        val id = profileId ?: return@launch
        playerReturn = if (screen is TvScreen.Player) playerReturn else screen
        loading = true
        runCatching {
            val sources = api.streams(mediaType, tmdbId, id, season, episode)
            val source = sources.firstOrNull()
                ?: error("No playable sources were found")
            val subs = runCatching { api.subtitles(mediaType, tmdbId, season, episode) }.getOrDefault(emptyList())
            TvScreen.Player(
                source.url,
                title,
                subs,
                nextEpisode?.copy(tmdbId = tmdbId),
                mediaType,
                tmdbId,
                season,
                episode,
                initialPositionMs,
                sources,
                introEndSeconds,
                recapEndSeconds,
            )
        }.onSuccess { screen = it }.onFailure { error = it.message ?: "Could not resolve playback" }
        loading = false
    }
    fun loadLive() = scope.launch {
        val id = profileId ?: return@launch
        error = null; screen = TvScreen.Live; loading = true
        runCatching { api.liveChannels(id) }.onSuccess { liveChannels = it }
            .onFailure { error = it.message ?: "Could not load Live TV" }
        loading = false
    }
    fun playLive(channel: LiveChannel) = scope.launch {
        val id = profileId
        playerReturn = screen
        error = null; loading = true
        runCatching { api.liveStreams(channel.channelId, id).firstOrNull() ?: error("This channel is temporarily unavailable") }
            .onSuccess { screen = TvScreen.Player(it.url, channel.name) }
            .onFailure { error = it.message ?: "This channel is temporarily unavailable" }
        loading = false
    }
    fun loadMyList() = scope.launch {
        profileId?.let { id -> loading = true; runCatching { api.myList(id) }.onSuccess { cards = it; myListKeys = it.map { card -> "${card.mediaType}:${card.tmdbId}" }.toSet(); screen = TvScreen.MyList }.onFailure { error = it.message }; loading = false }
    }
    fun loadNewHot() = scope.launch {
        profileId?.let { id -> loading = true; runCatching { api.newHot(id) }.onSuccess { rows = it; screen = TvScreen.NewHot }.onFailure { error = it.message }; loading = false }
    }
    fun loadActivity() = scope.launch {
        profileId?.let { id -> loading = true; runCatching { api.activity(id) }.onSuccess { activity = it; screen = TvScreen.Activity }.onFailure { error = it.message }; loading = false }
    }
    fun cardKey(card: MediaCard): String = "${card.mediaType}:${card.tmdbId}"
    fun toggleCardList(card: MediaCard) = scope.launch {
        val id = profileId ?: return@launch
        runCatching { api.toggleList(id, card) }
            .onSuccess { added ->
                val key = cardKey(card)
                myListKeys = if (added) myListKeys + key else myListKeys - key
                if (!added && screen is TvScreen.MyList) cards = cards.filterNot { cardKey(it) == key }
                contextCard = null
            }
            .onFailure { error = it.message ?: "Could not update My List" }
    }
    fun removeCardProgress(card: MediaCard) = scope.launch {
        val id = profileId ?: return@launch
        runCatching { api.removeProgress(id, card.mediaType, card.tmdbId) }
            .onSuccess {
                rows = rows.map { row ->
                    if (row.id == "continue") row.copy(items = row.items.filterNot { cardKey(it) == cardKey(card) }) else row
                }.filter { it.items.isNotEmpty() }
                contextCard = null
            }
            .onFailure { error = it.message ?: "Could not remove viewing progress" }
    }

    fun navigate(target: TvScreen) {
        when (target) {
            TvScreen.Home -> profileId?.let { loadHome(it) }
            is TvScreen.Browse -> loadCards(target.mediaType)
            TvScreen.MyList -> loadMyList()
            TvScreen.NewHot -> loadNewHot()
            TvScreen.Activity -> loadActivity()
            TvScreen.Live -> loadLive()
            TvScreen.Search, TvScreen.Profiles -> screen = target
            else -> screen = target
        }
    }

    LaunchedEffect(Unit) {
        runCatching { api.me() }.onSuccess { loadProfiles() }
        runCatching { checkForAppUpdate() }
            .onSuccess { availableUpdate = it }
    }

    MaterialTheme(colorScheme = StreammoreScheme) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            Box(Modifier.fillMaxSize()) {
                when (val current = screen) {
                    TvScreen.Login -> LoginScreen(loading, error) { email, password -> scope.launch { loading = true; runCatching { api.login(email, password) }.onSuccess { loadProfiles() }.onFailure { error = it.message ?: "Sign-in failed" }; loading = false } }
                    TvScreen.Profiles -> ProfileScreen(profiles, error) { profile ->
                        if (profile.kids && profile.hasPin) {
                            pendingProfile = profile
                            screen = TvScreen.ProfilePin(profile.id)
                        } else loadHome(profile.id)
                    }
                    is TvScreen.ProfilePin -> {
                        val profile = pendingProfile ?: profiles.firstOrNull { it.id == current.profileId }
                        if (profile == null) ProfileScreen(profiles, error) { loadHome(it.id) }
                        else ProfilePinScreen(profile, error) { pin ->
                            scope.launch {
                                loading = true
                                runCatching { api.unlockProfile(profile.id, pin) }
                                    .onSuccess { pendingProfile = null; loadHome(profile.id) }
                                    .onFailure { error = it.message ?: "Incorrect profile PIN" }
                                loading = false
                            }
                        }
                    }
                    TvScreen.Home -> AppShell(screen, ::navigate, profiles.firstOrNull { it.id == profileId }) {
                        HomeScreen(
                            rows = rows,
                            billboard = billboard,
                            autoplayPreviews = autoplayPreviews,
                            onCard = ::openDetail,
                            onLongCard = { contextCard = it },
                            onPlayBillboard = { hero ->
                                // Movies play straight away; a series needs an episode
                                // choice, so it opens its detail page (as the browser does).
                                if (hero.mediaType == "movie") play("movie", hero.tmdbId, hero.title)
                                else openDetail(hero.toCard())
                            },
                            loadTrailer = { hero ->
                                runCatching { api.trailer(hero.mediaType, hero.tmdbId) }.getOrNull()
                            },
                        )
                    }
                    is TvScreen.Browse -> AppShell(screen, ::navigate, profiles.firstOrNull { it.id == profileId }) {
                        BrowseScreen(
                            title = if (current.mediaType == "tv") "TV Shows" else "Movies",
                            cards = cards,
                            genres = browseGenres,
                            selectedGenreId = browseGenreId,
                            page = browsePage,
                            totalPages = browseTotalPages,
                            totalResults = browseTotalResults,
                            loadingMore = browseLoadingMore,
                            onGenre = { loadBrowseGenre(current.mediaType, it) },
                            onLoadMore = { loadMoreBrowse(current.mediaType) },
                            onCard = ::openDetail,
                            onLongCard = { contextCard = it },
                        )
                    }
                    TvScreen.Search -> AppShell(screen, ::navigate, profiles.firstOrNull { it.id == profileId }) { SearchScreen(cards, ::openDetail, { contextCard = it }) { query -> scope.launch { profileId?.let { id -> loading = true; runCatching { api.search(query, id) }.onSuccess { cards = it }.onFailure { error = it.message }; loading = false } } } }
                    TvScreen.NewHot -> AppShell(screen, ::navigate, profiles.firstOrNull { it.id == profileId }) { NewHotScreen(rows, ::openDetail, { contextCard = it }) }
                    TvScreen.MyList -> AppShell(screen, ::navigate, profiles.firstOrNull { it.id == profileId }) { GridScreen("My List", cards, ::openDetail, { contextCard = it }) }
                    TvScreen.Activity -> AppShell(screen, ::navigate, profiles.firstOrNull { it.id == profileId }) { ActivityScreen(activity) }
                    TvScreen.Live -> AppShell(screen, ::navigate, profiles.firstOrNull { it.id == profileId }) { LiveScreen(liveChannels, error, ::playLive, ::loadLive) }
                    is TvScreen.Detail -> {
                        val active = detail
                        if (active == null) LoadingScreen(error) else DetailScreen(
                            active,
                            episodes,
                            error,
                            { screen = TvScreen.Home },
                            {
                                if (active.mediaType == "tv") {
                                    val resumeSeason = active.resumeSeason ?: 1
                                    val resumeEpisode = active.resumeEpisode ?: 1
                                    val completedTarget = if (active.progress >= 0.95 && !active.resumeNext) {
                                        nextEpisodeAfter(episodes, resumeSeason, resumeEpisode, active.seasons)
                                    } else {
                                        null
                                    }
                                    if (completedTarget != null) {
                                        scope.launch {
                                            val targetEpisodes = profileId?.let { id ->
                                                runCatching { api.season(active.tmdbId, completedTarget.season, id) }.getOrDefault(emptyList())
                                            } ?: emptyList()
                                            if (targetEpisodes.isNotEmpty()) episodes = targetEpisodes
                                            val targetEpisode = targetEpisodes.firstOrNull { it.number == completedTarget.episode }
                                            play(
                                                "tv",
                                                active.tmdbId,
                                                active.title,
                                                completedTarget.season,
                                                completedTarget.episode,
                                                nextEpisodeAfter(targetEpisodes, completedTarget.season, completedTarget.episode, active.seasons),
                                                targetEpisode?.positionMs,
                                                active.introEndSeconds,
                                                active.recapEndSeconds,
                                            )
                                        }
                                    } else {
                                        play(
                                            "tv",
                                            active.tmdbId,
                                            active.title,
                                            resumeSeason,
                                            resumeEpisode,
                                            nextEpisodeAfter(episodes, resumeSeason, resumeEpisode, active.seasons),
                                            active.resumePositionMs,
                                            active.introEndSeconds,
                                            active.recapEndSeconds,
                                        )
                                    }
                                } else {
                                    play("movie", active.tmdbId, active.title, initialPositionMs = active.resumePositionMs, introEndSeconds = active.introEndSeconds, recapEndSeconds = active.recapEndSeconds)
                                }
                            },
                            { season -> profileId?.let { id -> scope.launch { runCatching { api.season(active.tmdbId, season, id) }.onSuccess { episodes = it }.onFailure { error = it.message ?: "Could not load episodes" } } } },
                            { season, episode ->
                                play(
                                    "tv",
                                    active.tmdbId,
                                    active.title,
                                    season,
                                    episode,
                                    nextEpisodeAfter(episodes, season, episode, active.seasons),
                                    episodes.firstOrNull { it.number == episode }?.positionMs,
                                    active.introEndSeconds,
                                    active.recapEndSeconds,
                                )
                            },
                            { profileId?.let { id -> scope.launch { val added = api.toggleList(id, MediaCard(active.mediaType, active.tmdbId, active.title, active.poster, active.backdrop, active.year, active.rating)); detail = active.copy(inMyList = added) } } },
                            { value -> profileId?.let { id -> scope.launch { detail = active.copy(myRating = api.rate(id, active.mediaType, active.tmdbId, value)) } } },
                        )
                    }
                    is TvScreen.Player -> PlayerScreen(
                        current.source,
                        current.title,
                        current.season,
                        current.episode,
                        current.subtitles,
                        api.sessionCookie(),
                        current.nextEpisode,
                        current.sources,
                        episodes,
                        { selected, position ->
                            screen = current.copy(source = selected.url, initialPositionMs = position)
                        },
                        { selectedEpisode ->
                            if (current.season != null) {
                                play("tv", current.tmdbId ?: 0, current.title, current.season, selectedEpisode.number, nextEpisodeAfter(episodes, current.season, selectedEpisode.number, detail?.seasons.orEmpty()), selectedEpisode.positionMs)
                            }
                        },
                        current.initialPositionMs,
                        current.introEndSeconds,
                        current.recapEndSeconds,
                        { position, duration ->
                            val profile = profileId
                            val mediaType = current.mediaType
                            val tmdbId = current.tmdbId
                            if (profile != null && mediaType != null && tmdbId != null) {
                                scope.launch {
                                    runCatching {
                                        api.saveProgress(
                                            profileId = profile,
                                            mediaType = mediaType,
                                            tmdbId = tmdbId,
                                            title = current.title,
                                            position = position,
                                            duration = duration,
                                            season = current.season,
                                            episode = current.episode,
                                            upNext = current.nextEpisode,
                                        )
                                    }
                                }
                            }
                        },
                        { next ->
                            scope.launch {
                                val crossesSeason = next.season != current.season
                                val nextSeasonEpisodes = if (crossesSeason) {
                                    profileId?.let { id -> runCatching { api.season(next.tmdbId, next.season, id) }.getOrDefault(emptyList()) }
                                        ?: emptyList()
                                } else {
                                    emptyList()
                                }
                                if (nextSeasonEpisodes.isNotEmpty()) episodes = nextSeasonEpisodes
                                val activeEpisodes = if (crossesSeason) nextSeasonEpisodes else episodes
                                val nextEpisode = activeEpisodes.firstOrNull { it.number == next.episode }
                                play(
                                    "tv",
                                    next.tmdbId,
                                    nextEpisode?.name ?: next.title,
                                    next.season,
                                    next.episode,
                                    nextEpisodeAfter(activeEpisodes, next.season, next.episode, detail?.seasons.orEmpty()),
                                    nextEpisode?.positionMs,
                                )
                            }
                        },
                    ) {
                        screen = playerReturn ?: TvScreen.Home
                        playerReturn = null
                    }
                }
                if (loading && screen !is TvScreen.Player) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Purple)
                if (error != null && screen !is TvScreen.Login && screen !is TvScreen.Live && screen !is TvScreen.Detail && screen !is TvScreen.Player) {
                    ErrorBanner(error!!, Modifier.align(Alignment.BottomCenter))
                }
                if (availableUpdate != null && !updateDismissed) {
                    UpdatePrompt(
                        update = availableUpdate!!,
                        modifier = Modifier.align(Alignment.Center),
                        busy = updateBusy,
                        error = updateError,
                        onLater = { updateDismissed = true },
                        onUpdate = {
                            if (!updateBusy) {
                                scope.launch {
                                    updateBusy = true
                                    updateError = null
                                    runCatching { downloadAndInstallUpdate(context, availableUpdate!!) }
                                        .onFailure { updateError = it.message ?: "Could not start the update" }
                                    updateBusy = false
                                }
                            }
                        },
                    )
                }
                if (contextCard != null && (availableUpdate == null || updateDismissed)) {
                    val selected = contextCard!!
                    CardActionMenu(
                        card = selected,
                        inMyList = myListKeys.contains("${selected.mediaType}:${selected.tmdbId}"),
                        canRemoveProgress = selected.percent > 0.0 || selected.season != null || selected.episode != null,
                        modifier = Modifier.align(Alignment.Center),
                        onDismiss = { contextCard = null },
                        onToggleList = { toggleCardList(selected) },
                        onRemoveProgress = { removeCardProgress(selected) },
                    )
                }

            }
        }
    }
}

// ── Shell ────────────────────────────────────────────────────────────────────

@Composable
internal fun AppShell(screen: TvScreen, navigate: (TvScreen) -> Unit, profile: Profile?, content: @Composable () -> Unit) {
    // Back from any section returns Home; only Back from Home leaves the app.
    BackHandler(enabled = screen !is TvScreen.Home) { navigate(TvScreen.Home) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(NavHeight)
                .background(Bg)
                .padding(horizontal = Gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.streammore_logo),
                contentDescription = "Streammore",
                modifier = Modifier.width(160.dp).height(54.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.width(20.dp))
            TopButton("Home", screen is TvScreen.Home) { navigate(TvScreen.Home) }
            TopButton("TV Shows", screen is TvScreen.Browse && screen.mediaType == "tv") { navigate(TvScreen.Browse("tv")) }
            TopButton("Movies", screen is TvScreen.Browse && screen.mediaType == "movie") { navigate(TvScreen.Browse("movie")) }
            TopButton("Search", screen is TvScreen.Search) { navigate(TvScreen.Search) }
            TopButton("New & Hot", screen is TvScreen.NewHot) { navigate(TvScreen.NewHot) }
            TopButton("Live TV", screen is TvScreen.Live) { navigate(TvScreen.Live) }
            TopButton("My List", screen is TvScreen.MyList) { navigate(TvScreen.MyList) }
            Spacer(Modifier.weight(1f))
            Text(profile?.name ?: "Profile", color = Muted, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            TextButton({ navigate(TvScreen.Profiles) }) { Text("Switch", color = TextPrimary, fontSize = 14.sp) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF222222)))
        Box(Modifier.fillMaxSize()) { content() }
    }
}

@Composable
internal fun TopButton(label: String, active: Boolean, onClick: () -> Unit) {
    TextButton(onClick) {
        Text(
            label,
            color = if (active) Color.White else Muted,
            fontSize = 14.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

// ── Building blocks ──────────────────────────────────────────────────────────

/**
 * A D-pad focusable tile. On a TV the focus ring *is* the pointer, so the focused
 * tile must be unmistakable: brighter border, lighter fill and a slight scale-up.
 */
@Composable
internal fun TvCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    verticalArrangement: Arrangement.Vertical = Arrangement.Center,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    TvCardSurface(
        focused = focused,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused || it.hasFocus },
        shape = shape,
        contentPadding = contentPadding,
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/**
 * The pure visual layer of [TvCard]. The focused appearance is a function of the
 * [focused] flag rather than of live focus ownership, so it can be rendered and
 * inspected in isolation.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvCardSurface(
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    verticalArrangement: Arrangement.Vertical = Arrangement.Center,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "focusScale")
    val border by animateColorAsState(if (focused) BorderFocused else BorderIdle, label = "focusBorder")

    Card(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = if (focused) PanelFocused else Panel),
        border = BorderStroke(if (focused) 2.dp else 1.dp, border),
    ) {
        Column(
            Modifier.padding(contentPadding).fillMaxWidth(),
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

@Composable
private fun Ribbon(label: String) {
    Text(
        label,
        color = Color.White,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .background(Purple)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

@Composable
private fun WatchProgress(percent: Double) {
    Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0xFF3A3A3A))) {
        Box(Modifier.fillMaxWidth(percent.coerceIn(0.0, 1.0).toFloat()).height(3.dp).background(Purple))
    }
}

/** Poster tile: artwork inside the focus ring, caption underneath it. */
@Composable
internal fun MediaCardView(
    card: MediaCard,
    onClick: (MediaCard) -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (MediaCard) -> Unit = {},
) {
    Column(Modifier.width(PosterWidth)) {
        TvCard(
            onClick = { onClick(card) },
            onLongClick = { onLongPress(card) },
            modifier = modifier.fillMaxWidth(),
        ) {
            Box {
                AsyncImage(
                    model = card.poster ?: card.backdrop,
                    contentDescription = card.title,
                    modifier = Modifier.fillMaxWidth().height(PosterHeight),
                    contentScale = ContentScale.Crop,
                )
                card.ribbon?.let { Box(Modifier.align(Alignment.TopStart)) { Ribbon(it) } }
                if (card.percent > 0.0) Box(Modifier.align(Alignment.BottomCenter)) { WatchProgress(card.percent) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            card.title,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        card.sub?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = Purple, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            listOfNotNull(card.year, card.rating?.let { "★ ${"%.1f".format(it)}" }).joinToString(" · "),
            color = Muted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Screens that load asynchronously lose focus when their content appears, so the
 * first D-pad press lands on the nav bar and pressing OK appears to do nothing.
 * Give each such screen a focus target and claim it once, when content arrives.
 */
@Composable
private fun FocusFirstWhenReady(ready: Boolean, requester: FocusRequester) {
    var claimed by remember { mutableStateOf(false) }
    LaunchedEffect(ready) {
        if (ready && !claimed) {
            claimed = true
            // Wait for a real layout pass. Requesting focus while the tree is still
            // being measured makes the scrollable parent scroll to a stale position,
            // which pushes the hero's title off the top of the screen.
            // The hero and its rows first appear in this very frame; claiming focus
            // before they have been laid out makes the scroller jump to a stale
            // position and clips the hero's title off the top of the screen.
            withFrameNanos { }
            delay(900)
            runCatching { requester.requestFocus() }
        }
    }
}

/**
 * A focus-aware pill button.
 *
 * Material3's Button draws no focus indicator, which on a TV means the remote gives
 * no feedback at all. The focused button therefore gets a bright ring and a small
 * scale-up, matching the poster tiles.
 */
@Composable
internal fun TvButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    selected: Boolean = false,
    onFocusGained: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "buttonFocusScale")
    val ring by animateColorAsState(if (focused) BorderFocused else BorderIdle, label = "buttonFocusRing")
    val focusModifier = modifier
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .onFocusChanged {
            val nowFocused = it.isFocused || it.hasFocus
            if (nowFocused && !focused) onFocusGained?.invoke()
            focused = nowFocused
        }

    if (primary) {
        Button(
            onClick = onClick,
            modifier = focusModifier,
            border = BorderStroke(if (focused) 2.dp else 0.dp, ring),
            content = content,
        )
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = focusModifier,
            border = BorderStroke(
                if (focused) 2.dp else 1.dp,
                when {
                    focused -> ring
                    selected -> Color.White
                    else -> BorderIdle
                },
            ),
            content = content,
        )
    }
}

// ── Screens ──────────────────────────────────────────────────────────────────

@Composable
internal fun LoginScreen(loading: Boolean, error: String?, onLogin: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val fieldWidth = 420.dp
    Column(Modifier.fillMaxSize().padding(64.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(R.drawable.streammore_logo),
            contentDescription = "Streammore",
            modifier = Modifier.width(360.dp).height(120.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            email, { email = it },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.width(fieldWidth),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            password, { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.width(fieldWidth),
        )
        Spacer(Modifier.height(20.dp))
        Button(
            { onLogin(email.trim(), password) },
            enabled = !loading && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.width(fieldWidth),
        ) {
            Text("Sign in", fontSize = 16.sp, modifier = Modifier.padding(vertical = 4.dp))
        }
        error?.let {
            Text(it, color = ErrorText, fontSize = 14.sp, modifier = Modifier.padding(top = 18.dp).width(fieldWidth))
        }
    }
}

@Composable
internal fun ProfilePinScreen(profile: Profile, error: String?, onSubmit: (String) -> Unit) {
    var pin by remember(profile.id) { mutableStateOf("") }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Profile locked", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Enter the PIN for ${profile.name}", color = Muted, fontSize = 15.sp)
            OutlinedTextField(
                value = pin,
                onValueChange = { value -> if (value.length <= 4 && value.all(Char::isDigit)) pin = value },
                label = { Text("4-digit PIN") },
                singleLine = true,
            )
            TvButton(onClick = { if (pin.length == 4) onSubmit(pin) }, primary = true) {
                Text("Unlock", fontSize = 15.sp)
            }
            error?.let { Text(it, color = ErrorText, fontSize = 14.sp) }
        }
    }
}

@Composable
internal fun ProfileScreen(profiles: List<Profile>, error: String?, onSelect: (Profile) -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Who's watching?", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(28.dp))
            if (profiles.isEmpty()) {
                Text("No profiles are available for this account.", color = Muted)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.Top) {
                    profiles.forEach { profile ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(154.dp)) {
                            TvCard(
                                { onSelect(profile) },
                                Modifier.size(142.dp),
                                shape = CircleShape,
                            ) {
                                Text(profile.avatar ?: profile.name.take(1).uppercase(), fontSize = 42.sp)
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                profile.name,
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (profile.kids) Text("KIDS", color = WarnYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            error?.let {
                Text(it, color = ErrorText, fontSize = 14.sp, modifier = Modifier.padding(top = 20.dp))
            }
        }
    }
}

private val HeroHeight = 350.dp

/**
 * The home hero, mirroring the browser's billboard: full-bleed backdrop, title,
 * a match/year/HD/type line, a three-line overview and Play / More Info.
 *
 * After a beat the backdrop becomes a muted, looping trailer — the same embed and
 * the same 2600ms delay the browser uses.
 */
@Composable
internal fun BillboardHero(
    billboard: Billboard,
    autoplayPreviews: Boolean,
    playFocus: FocusRequester? = null,
    trailerEnabled: Boolean = true,
    onHeroFocus: (() -> Unit)? = null,
    onPlay: (Billboard) -> Unit,
    onMoreInfo: (Billboard) -> Unit,
    loadTrailer: suspend (Billboard) -> String? = { null },
) {
    var trailerUrl by remember(billboard.tmdbId) { mutableStateOf<String?>(null) }
    LaunchedEffect(billboard.tmdbId, autoplayPreviews, trailerEnabled) {
        // Leaving the hero tears the player down; the browser does the same on scroll.
        trailerUrl = null
        if (trailerEnabled && autoplayPreviews && !billboard.trailerKey.isNullOrBlank()) {
            delay(2600)
            trailerUrl = loadTrailer(billboard)
        }
    }

    Box(Modifier.fillMaxWidth().height(HeroHeight)) {
        AsyncImage(
            model = billboard.backdrop ?: billboard.poster,
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
        )
        trailerUrl?.let { url -> HeroTrailer(url, Modifier.matchParentSize()) }
        // The browser's two scrims: dark on the left for legible text, and a fade
        // into the page background at the bottom so the rows below blend in.
        Box(
            Modifier.matchParentSize().background(
                Brush.horizontalGradient(
                    0.0f to Bg.copy(alpha = 0.92f),
                    0.45f to Bg.copy(alpha = 0.55f),
                    0.75f to Color.Transparent,
                ),
            ),
        )
        Box(
            Modifier.matchParentSize().background(
                // Browser-equivalent bottom fade: keep the trailer visible through
                // almost the whole hero and fade only the final 18% into the rows.
                Brush.verticalGradient(0.82f to Color.Transparent, 1.0f to Bg),
            ),
        )

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = Gutter, end = Gutter, bottom = 28.dp)
                .widthIn(max = 640.dp),
        ) {
            Text(
                billboard.title,
                color = Color.White,
                fontSize = 52.sp,
                lineHeight = 54.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                billboard.rating?.let {
                    Text("${(it * 10).toInt()}% Match", color = MatchGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                billboard.year?.let { Text(it, color = MetaText, fontSize = 15.sp) }
                Text(
                    "HD",
                    color = MetaText,
                    fontSize = 11.sp,
                    modifier = Modifier.border(1.dp, BadgeBorder, RoundedCornerShape(2.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
                )
                Text(if (billboard.mediaType == "tv") "Series" else "Film", color = MetaText, fontSize = 15.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                billboard.overview,
                color = MetaText,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(
                    onClick = { onPlay(billboard) },
                    modifier = if (playFocus != null) Modifier.focusRequester(playFocus) else Modifier,
                    primary = true,
                    onFocusGained = onHeroFocus,
                ) {
                    Text("▶ Play", fontSize = 16.sp)
                }
                TvButton(onClick = { onMoreInfo(billboard) }, onFocusGained = onHeroFocus) {
                    Text("ℹ More Info", color = TextPrimary, fontSize = 16.sp)
                }
            }
        }
    }
}

/**
 * The hero trailer: a muted, looping native player, scaled 1.35x to crop the
 * letterboxing exactly as the browser's embed does.
 *
 * Deliberately never focusable — on a TV the D-pad has to pass straight through to
 * Play / More Info rather than getting trapped in the player.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun HeroTrailer(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(url) {
        onDispose {
            Log.d("StreammoreHero", "trailer player released")
            player.release()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            HeroTextureSurface(ctx).also { surface ->
                surface.bind(player)
            }
        },
        onRelease = { surface ->
            surface.unbind(player)
        },
    )
}

/**
 * Full-bleed hero surface for Xiaomi Android TV.
 *
 * PlayerView/SurfaceView kept laying out the decoded frame at its own aspect ratio,
 * leaving a visible lower rectangle in the billboard. TextureView lets us own the
 * transform: preserve the video aspect ratio, center-crop it, and cover every pixel
 * of the hero box.
 */
private class HeroTextureSurface(context: android.content.Context) : FrameLayout(context) {
    private val texture = TextureView(context)
    private var videoWidth = 0
    private var videoHeight = 0
    private var pixelRatio = 1f

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        addView(
            texture,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        texture.isFocusable = false
        texture.isFocusableInTouchMode = false
        texture.isClickable = false
        texture.setOnTouchListener { _, _ -> true }
    }

    fun bind(player: ExoPlayer) {
        player.setVideoTextureView(texture)
        player.addListener(listener)
        applyCrop()
    }

    fun unbind(player: ExoPlayer) {
        player.removeListener(listener)
        player.clearVideoTextureView(texture)
    }

    private val listener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
            videoWidth = videoSize.width
            videoHeight = videoSize.height
            pixelRatio = videoSize.pixelWidthHeightRatio
            applyCrop()
        }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        applyCrop()
    }

    private fun applyCrop() {
        if (width <= 0 || height <= 0 || videoWidth <= 0 || videoHeight <= 0) return
        val videoAspect = videoWidth * pixelRatio / videoHeight.toFloat()
        val viewAspect = width / height.toFloat()
        val scaleX: Float
        val scaleY: Float
        if (videoAspect > viewAspect) {
            // Video is wider: crop its sides.
            scaleX = videoAspect / viewAspect
            scaleY = 1f
        } else {
            // Video is taller: crop its top/bottom.
            scaleX = 1f
            scaleY = viewAspect / videoAspect
        }
        texture.setTransform(Matrix().apply {
            setScale(scaleX, scaleY, width / 2f, height / 2f)
        })
    }
}


@Composable
internal fun HomeScreen(
    rows: List<HomeRow>,
    billboard: Billboard? = null,
    autoplayPreviews: Boolean = true,
    onCard: (MediaCard) -> Unit,
    onLongCard: (MediaCard) -> Unit = {},
    onPlayBillboard: (Billboard) -> Unit = {},
    loadTrailer: suspend (Billboard) -> String? = { null },
) {
    // Deliberately no auto-focus on this screen. Compose scrolls a scrollable parent
    // to the focused node, and claiming focus while the hero is still settling makes
    // the list jump 250-500px, which clips the hero's title off the top of the
    // screen. The first D-pad press lands on the nav bar instead, and the hero is one
    // press away from there.
    //
    // A plain scrolling Column rather than a LazyColumn for the same reason, and
    // because the rows are lazy horizontally anyway - which is where the item count
    // actually is.
    val scrollState = rememberScrollState()
    val homeScope = rememberCoroutineScope()
    val heroPlayFocus = remember { FocusRequester() }
    val heroOnScreen by remember { derivedStateOf { scrollState.value < 80 } }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
    ) {
        billboard?.let { hero ->
            BillboardHero(
                billboard = hero,
                autoplayPreviews = autoplayPreviews,
                playFocus = heroPlayFocus,
                trailerEnabled = heroOnScreen,
                onHeroFocus = {
                    homeScope.launch {
                        // Compose may run bring-into-view after focus changes;
                        // reset after that pass so the complete hero is visible.
                        delay(220)
                        scrollState.animateScrollTo(0)
                    }
                },
                onPlay = onPlayBillboard,
                onMoreInfo = { onCard(it.toCard()) },
                loadTrailer = loadTrailer,
            )
        }
        rows.forEachIndexed { index, row ->
            RowSection(
                title = row.title,
                items = row.items,
                onCard = onCard,
                onLongCard = onLongCard,
                firstCardRequester = if (index == 0) heroPlayFocus else null,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
internal fun RowSection(
    title: String,
    items: List<MediaCard>,
    onCard: (MediaCard) -> Unit,
    onLongCard: (MediaCard) -> Unit = {},
    firstCardRequester: FocusRequester? = null,
) {
    Column(Modifier.padding(horizontal = Gutter)) {
        Text(title, color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(items) { index, item ->
                val modifier = if (firstCardRequester != null) {
                    Modifier
                        .focusProperties { up = firstCardRequester }
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                                firstCardRequester.requestFocus()
                                true
                            } else {
                                false
                            }
                        }
                } else {
                    Modifier
                }
                MediaCardView(item, onCard, modifier, onLongCard)
            }
        }
    }
}

@Composable
internal fun BrowseScreen(
    title: String,
    cards: List<MediaCard>,
    genres: List<GenreOption>,
    selectedGenreId: Int?,
    page: Int,
    totalPages: Int,
    totalResults: Int,
    loadingMore: Boolean,
    onGenre: (GenreOption?) -> Unit,
    onLoadMore: () -> Unit,
    onCard: (MediaCard) -> Unit,
    onLongCard: (MediaCard) -> Unit = {},
) {
    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState, cards.size, page, totalPages, selectedGenreId) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { lastIndex ->
                if (lastIndex >= cards.size - 5 && page < totalPages) onLoadMore()
            }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = Gutter)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
            Text(title, color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            if (totalResults > 0) {
                Text("${cards.size} of $totalResults", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 14.dp))
            }
        }
        if (genres.isNotEmpty()) {
            Text("Filter by genre", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            LazyRow(
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    TvButton(onClick = { onGenre(null) }, selected = selectedGenreId == null) {
                        Text("All", color = TextPrimary, fontSize = 13.sp)
                    }
                }
                items(genres) { genre ->
                    TvButton(onClick = { onGenre(genre) }, selected = selectedGenreId == genre.id) {
                        Text(genre.name, color = TextPrimary, fontSize = 13.sp)
                    }
                }
            }
        }
        if (cards.isEmpty() && !loadingMore) {
            Text("Nothing to show.", color = Muted, modifier = Modifier.padding(top = 18.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = PosterWidth),
            state = gridState,
            contentPadding = PaddingValues(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(cards) { card -> MediaCardView(card, onCard, Modifier, onLongCard) }
            if (loadingMore) {
                item { CircularProgressIndicator(color = Purple, modifier = Modifier.padding(24.dp)) }
            }
        }
    }
}

@Composable
internal fun GridScreen(
    title: String,
    cards: List<MediaCard>,
    onCard: (MediaCard) -> Unit,
    onLongCard: (MediaCard) -> Unit = {},
) {
    val firstCard = remember { FocusRequester() }
    FocusFirstWhenReady(cards.isNotEmpty(), firstCard)
    Column(Modifier.fillMaxSize().padding(horizontal = Gutter)) {
        Text(title, color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        if (cards.isEmpty()) {
            Text("Nothing here yet.", color = Muted, modifier = Modifier.padding(top = 18.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = PosterWidth),
            contentPadding = PaddingValues(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(cards) { index, card ->
                MediaCardView(card, onCard, if (index == 0) Modifier.focusRequester(firstCard) else Modifier, onLongCard)
            }
        }
    }
}

@Composable
internal fun SearchScreen(
    cards: List<MediaCard>,
    onCard: (MediaCard) -> Unit,
    onLongCard: (MediaCard) -> Unit = {},
    onSearch: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = Gutter)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                query, { query = it },
                label = { Text("Search titles") },
                singleLine = true,
                modifier = Modifier.width(420.dp),
            )
            Spacer(Modifier.width(12.dp))
            Button({ onSearch(query) }, enabled = query.isNotBlank()) { Text("Search") }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = PosterWidth),
            contentPadding = PaddingValues(vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(cards) { MediaCardView(it, onCard, Modifier, onLongCard) }
        }
    }
}

@Composable
internal fun NewHotScreen(
    rows: List<HomeRow>,
    onCard: (MediaCard) -> Unit,
    onLongCard: (MediaCard) -> Unit = {},
) {
    val firstCard = remember { FocusRequester() }
    FocusFirstWhenReady(rows.any { it.items.isNotEmpty() }, firstCard)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        rows.forEach { row -> RowSection(row.title, row.items, onCard, onLongCard, null) }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
internal fun ActivityScreen(items: List<ActivityEntry>) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = Gutter, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("Viewing activity", color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
        if (items.isEmpty()) {
            item { Text("No viewing activity yet.", color = Muted, modifier = Modifier.padding(top = 12.dp)) }
        }
        items(items) { entry ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Panel).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    entry.poster, entry.title,
                    Modifier.width(52.dp).height(74.dp).clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop,
                )
                Column(Modifier.padding(start = 16.dp)) {
                    Text(entry.title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        buildString {
                            append("${(entry.percent * 100).toInt()}% watched")
                            if (entry.season != null && entry.episode != null) append(" · S${entry.season} E${entry.episode}")
                        },
                        color = Muted, fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

@Composable
internal fun LiveScreen(
    channels: List<LiveChannel>,
    error: String?,
    onPlay: (LiveChannel) -> Unit,
    onRefresh: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = remember(channels) {
        listOf("All") + channels.map { it.genre }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val activeCategory = selectedCategory.takeIf { it in categories } ?: "All"
    val filteredChannels = channels.filter { channel ->
        val matchesCategory = activeCategory == "All" || channel.genre == activeCategory
        val needle = search.trim()
        val matchesSearch = needle.isBlank() || channel.name.contains(needle, ignoreCase = true) ||
            channel.genre.contains(needle, ignoreCase = true) || channel.country.contains(needle, ignoreCase = true)
        matchesCategory && matchesSearch
    }
    val firstChannel = remember { FocusRequester() }
    FocusFirstWhenReady(filteredChannels.isNotEmpty(), firstChannel)

    Column(Modifier.fillMaxSize().padding(horizontal = Gutter)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
            Text("Live TV", color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(20.dp))
            TvButton(onRefresh) { Text("Refresh", color = TextPrimary) }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                singleLine = true,
                label = { Text("Search channels") },
                modifier = Modifier.width(360.dp),
            )
            Text("${filteredChannels.size} channels", color = Muted, fontSize = 13.sp)
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(categories) { category ->
                TvButton(onClick = { selectedCategory = category }, selected = category == activeCategory) {
                    Text(category, color = TextPrimary, fontSize = 13.sp)
                }
            }
        }
        error?.let { Text(it, color = ErrorText, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp)) }
        if (filteredChannels.isEmpty()) {
            Text("No channels match this search or category.", color = Muted, modifier = Modifier.padding(top = 20.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 168.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(filteredChannels) { index, channel ->
                val tileModifier = if (index == 0) Modifier.height(116.dp).focusRequester(firstChannel) else Modifier.height(116.dp)
                TvCard({ onPlay(channel) }, tileModifier, contentPadding = PaddingValues(10.dp)) {
                    Text("📺", fontSize = 26.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(channel.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${channel.genre} · ${channel.country}", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
internal fun DetailScreen(
    detail: TitleDetail,
    episodes: List<Episode>,
    error: String?,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onSeason: (Int) -> Unit,
    onEpisode: (Int, Int) -> Unit,
    onList: () -> Unit,
    onRate: (String?) -> Unit,
) {
    var selectedSeason by remember(detail.tmdbId) { mutableStateOf(detail.resumeSeason ?: detail.seasons.firstOrNull()?.number ?: 1) }
    // Opening a title otherwise leaves nothing focused, so the first D-pad press
    // lands on "Back" and pressing OK appears to do nothing. Start on Play.
    val playFocus = remember(detail.tmdbId) { FocusRequester() }
    LaunchedEffect(detail.tmdbId) { runCatching { playFocus.requestFocus() } }
    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize()) {
        // Backdrop hero behind the metadata, faded into the page background.
        AsyncImage(
            model = detail.backdrop ?: detail.poster,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(430.dp),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier.fillMaxWidth().height(430.dp).background(
                Brush.verticalGradient(
                    0.0f to Bg.copy(alpha = 0.35f),
                    0.6f to Bg.copy(alpha = 0.85f),
                    1.0f to Bg,
                ),
            ),
        )

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 60.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column {
                    TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                        Text("‹ Back", color = TextPrimary, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(detail.title, color = TextPrimary, fontSize = 40.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    detail.tagline?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = Muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        listOfNotNull(
                            detail.year,
                            detail.runtime?.let { "${it}m" },
                            detail.rating?.let { "${(it * 10).toInt()}% Match" },
                        ).joinToString(" · "),
                        color = Muted, fontSize = 14.sp,
                    )
                    if (detail.genres.isNotEmpty()) {
                        Text(
                            "Genre · ${detail.genres.joinToString(" · ")}",
                            color = Purple,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(detail.overview, color = TextPrimary.copy(alpha = 0.85f), fontSize = 15.sp, modifier = Modifier.width(820.dp), maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        TvButton(onPlay, Modifier.focusRequester(playFocus), primary = true) {
                            Text(
                                if (detail.mediaType == "tv" && detail.resumeSeason != null && detail.resumeEpisode != null)
                                    "▶ Resume S${detail.resumeSeason}E${detail.resumeEpisode}"
                                else "▶ Play",
                                fontSize = 15.sp,
                            )
                        }
                        TvButton(onList) {
                            Text(if (detail.inMyList) "✓ My List" else "+ My List", color = TextPrimary, fontSize = 15.sp)
                        }
                        TvButton({ onRate(if (detail.myRating == "up") null else "up") }) { Text("👍") }
                        TvButton({ onRate(if (detail.myRating == "down") null else "down") }) { Text("👎") }
                    }
                    error?.let {
                        Text(it, color = ErrorText, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }

            if (detail.mediaType == "tv" && detail.seasons.isNotEmpty()) {
                item {
                    Column {
                        Text("Episodes", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(detail.seasons) { season ->
                                val isSelected = season.number == selectedSeason
                                TvButton(
                                    onClick = {
                                        selectedSeason = season.number
                                        onSeason(season.number)
                                    },
                                    selected = isSelected,
                                ) {
                                    Text(
                                        "Season ${season.number}",
                                        color = if (isSelected) Color.White else Muted,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }
                }
                items(episodes) { episode ->
                    EpisodeRow(episode) { onEpisode(selectedSeason, episode.number) }
                }
            }

            if (detail.cast.isNotEmpty()) {
                item {
                    Column {
                        Text("Cast", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(detail.cast) { person -> CastCard(person) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: Episode, onClick: () -> Unit) {
    TvCard(
        onClick,
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.width(112.dp).height(82.dp)) {
                AsyncImage(
                    episode.still, episode.name,
                    Modifier.fillMaxWidth().height(63.dp).clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop,
                )
                if (episode.progress > 0.0) {
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 64.dp)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Panel.copy(alpha = 0.95f))
                            .clip(RoundedCornerShape(2.dp)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(episode.progress.toFloat())
                                .height(4.dp)
                                .background(if (episode.watched) Purple else Purple.copy(alpha = 0.9f)),
                        )
                    }
                }
                if (episode.watched) {
                    Surface(
                        Modifier.align(Alignment.TopStart).padding(5.dp),
                        color = Color.Black.copy(alpha = 0.78f),
                        shape = RoundedCornerShape(3.dp),
                    ) {
                        Text("WATCHED", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp))
                    }
                }
            }
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(
                    "${episode.number}. ${episode.name}",
                    color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                episode.overview?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun CastCard(person: Person) {
    Column(Modifier.width(108.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AsyncImage(
            person.profile, person.name,
            Modifier.size(88.dp).clip(CircleShape).background(Panel),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(8.dp))
        Text(person.name, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (person.character.isNotBlank()) {
            Text(person.character, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CardActionMenu(
    card: MediaCard,
    inMyList: Boolean,
    canRemoveProgress: Boolean,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onToggleList: () -> Unit,
    onRemoveProgress: () -> Unit,
) {
    val firstFocus = remember(card.mediaType, card.tmdbId) { FocusRequester() }
    LaunchedEffect(card.mediaType, card.tmdbId) {
        delay(80)
        runCatching { firstFocus.requestFocus() }
    }
    BackHandler(onBack = onDismiss)
    Surface(
        modifier = modifier.widthIn(min = 300.dp, max = 430.dp),
        color = Panel.copy(alpha = 0.99f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(2.dp, Purple),
        shadowElevation = 18.dp,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(card.title, color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Card actions", color = Muted, fontSize = 12.sp)
            TvButton(
                onClick = onToggleList,
                primary = true,
                modifier = Modifier.fillMaxWidth().focusRequester(firstFocus),
            ) {
                Text(if (inMyList) "Remove from My List" else "Add to My List", fontSize = 14.sp)
            }
            if (canRemoveProgress) {
                TvButton(onClick = onRemoveProgress, modifier = Modifier.fillMaxWidth()) {
                    Text("Remove from Continue Watching", color = TextPrimary, fontSize = 14.sp)
                }
            }
            TvButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel", color = TextPrimary, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun UpdatePrompt(
    update: AppUpdate,
    modifier: Modifier = Modifier,
    busy: Boolean,
    error: String?,
    onLater: () -> Unit,
    onUpdate: () -> Unit,
) {
    val updateFocus = remember(update.versionName) { FocusRequester() }
    LaunchedEffect(update.versionName, busy, error) {
        if (!busy) {
            delay(80)
            runCatching { updateFocus.requestFocus() }
        }
    }

    Surface(
        modifier = modifier
            .padding(28.dp)
            .widthIn(max = 520.dp),
        color = Panel,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(2.dp, Purple),
        shadowElevation = 18.dp,
    ) {
        Column(Modifier.padding(26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("STREAMMORE UPDATE", color = Purple, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text("Version ${update.versionName} is available", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            if (update.releaseNotes.isNotBlank()) {
                Text(update.releaseNotes.take(500), color = Muted, fontSize = 14.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
            }
            if (error != null) Text(error, color = ErrorText, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = Purple, strokeWidth = 2.dp)
                    Text("Downloading update…", color = Muted, fontSize = 14.sp)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton(
                        onClick = onUpdate,
                        primary = true,
                        modifier = Modifier.focusRequester(updateFocus),
                    ) { Text("Update now", fontSize = 14.sp) }
                    TvButton(onClick = onLater) { Text("Later", color = TextPrimary, fontSize = 14.sp) }
                }
            }
        }
    }
}

@Composable
internal fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    Surface(modifier.padding(28.dp), color = ErrorFill, shape = RoundedCornerShape(8.dp)) {
        Text(
            message,
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun LoadingScreen(error: String?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (error == null) CircularProgressIndicator(color = Purple)
        else Text(error, color = ErrorText, fontSize = 15.sp)
    }
}

// Media3's DefaultHttpDataSource/DefaultMediaSourceFactory are marked @UnstableApi.
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun PlayerScreen(
    source: String,
    title: String,
    season: Int? = null,
    episode: Int? = null,
    subtitles: List<SubtitleTrack>,
    cookie: String?,
    nextEpisode: NextEpisodeInfo? = null,
    sources: List<StreamSource> = emptyList(),
    episodeList: List<Episode> = emptyList(),
    onSelectSource: (StreamSource, Long) -> Unit = { _, _ -> },
    onSelectEpisode: (Episode) -> Unit = {},
    initialPositionMs: Long? = null,
    introEndSeconds: Long? = null,
    recapEndSeconds: Long? = null,
    onProgress: (position: Long, duration: Long) -> Unit = { _, _ -> },
    onPlayNext: (NextEpisodeInfo) -> Unit = {},
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var playerError by remember(source) { mutableStateOf<String?>(null) }
    var chromeVisible by remember(source) { mutableStateOf(false) }
    var interactionTick by remember(source) { mutableStateOf(0) }
    var qualityMenuOpen by remember(source) { mutableStateOf(false) }
    var subtitleMenuOpen by remember(source) { mutableStateOf(false) }
    var sourceMenuOpen by remember(source) { mutableStateOf(false) }
    var episodeMenuOpen by remember(source) { mutableStateOf(false) }
    var settingsMenuOpen by remember(source) { mutableStateOf(false) }
    var autoNextEnabled by remember(source) { mutableStateOf(true) }
    var selectedSubtitle by remember(source) { mutableStateOf<String?>(null) }
    var selectedQualityLabel by remember(source) { mutableStateOf("Auto") }
    var availableQualities by remember(source) { mutableStateOf<List<AvailableVideoQuality>>(emptyList()) }
    var nextPromptVisible by remember(source, nextEpisode) { mutableStateOf(false) }
    var nextPromptDismissed by remember(source, nextEpisode) { mutableStateOf(false) }
    var countdownSeconds by remember(source, nextEpisode) { mutableStateOf(30) }
    var nextStarted by remember(source, nextEpisode) { mutableStateOf(false) }
    var nextActionFocused by remember(source, nextEpisode) { mutableStateOf("play") }
    var playbackPosition by remember(source) { mutableStateOf(0L) }
    var playbackDuration by remember(source) { mutableStateOf(0L) }
    var isPlaying by remember(source) { mutableStateOf(true) }
    var skipLabel by remember(source) { mutableStateOf<String?>(null) }
    var skipTargetMs by remember(source) { mutableStateOf<Long?>(null) }
    val qualityFocus = remember(source) { FocusRequester() }
    val qualityOptionFocus = remember(source) { FocusRequester() }
    val nextFocus = remember(source, nextEpisode) { FocusRequester() }
    val nextCloseFocus = remember(source, nextEpisode) { FocusRequester() }
    val playerFocus = remember(source) { FocusRequester() }
    val playFocus = remember(source) { FocusRequester() }
    val seekFocus = remember(source) { FocusRequester() }
    val episodeFocus = remember(source) { FocusRequester() }
    val trackSelector = remember(source) { DefaultTrackSelector(context) }
    val player = remember(source, trackSelector) {
        // Subtitle files come from the authenticated /api/subtitles/file route, so
        // ExoPlayer's own requests have to carry the session cookie.
        val headers = if (cookie.isNullOrBlank()) emptyMap() else mapOf("Cookie" to cookie)
        val dataSourceFactory = DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers)
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onTracksChanged(tracks: Tracks) {
                        availableQualities = availableVideoQualities(tracks)
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        playerError = "Playback network error: ${error.errorCodeName} (${error.message ?: "unknown error"})"
                        Log.e("StreammorePlayer", "Playback failed for ${Uri.parse(source).host}:${Uri.parse(source).port}", error)
                    }
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }
                })
                val tracks = subtitles.map { item ->
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(item.url))
                        .setMimeType(MimeTypes.TEXT_VTT)
                        .setLanguage(item.language)
                        .setLabel(item.label)
                        .setSelectionFlags(C.SELECTION_FLAG_AUTOSELECT)
                        .build()
                }
                setMediaItem(MediaItem.Builder().setUri(Uri.parse(source)).setSubtitleConfigurations(tracks).build())
                initialPositionMs?.takeIf { it > 0L }?.let { seekTo(it) }
                prepare()
                playWhenReady = true
            }
    }

    LaunchedEffect(source) {
        withFrameNanos { }
        delay(150)
        runCatching { playerFocus.requestFocus() }
    }

    fun applyQuality(quality: AvailableVideoQuality?) {
        val parameters = trackSelector.buildUponParameters()
            .clearVideoSizeConstraints()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .setForceLowestBitrate(false)
            .setForceHighestSupportedBitrate(false)
        if (quality != null) {
            parameters.addOverride(TrackSelectionOverride(quality.group, quality.trackIndices))
            selectedQualityLabel = quality.label
        } else {
            selectedQualityLabel = "Auto"
        }
        trackSelector.parameters = parameters.build()
        qualityMenuOpen = false
    }

    fun applySubtitle(language: String?) {
        val parameters = trackSelector.buildUponParameters()
            .setRendererDisabled(C.TRACK_TYPE_TEXT, language == null)
            .setPreferredTextLanguage(language)
            .build()
        trackSelector.parameters = parameters
        selectedSubtitle = language
        subtitleMenuOpen = false
    }

    fun seekToPosition(positionMs: Long) {
        val duration = player.duration.takeIf { it > 0L } ?: playbackDuration
        if (duration <= 0L) return
        val target = positionMs.coerceIn(0L, duration)
        player.seekTo(target)
        playbackPosition = target
        chromeVisible = true
        interactionTick++
    }

    fun seekBy(deltaMs: Long) {
        seekToPosition(player.currentPosition + deltaMs)
    }

    LaunchedEffect(player, nextEpisode, nextPromptDismissed) {
        if (nextEpisode == null || nextPromptDismissed) return@LaunchedEffect
        while (true) {
            val duration = player.duration
            val position = player.currentPosition
            if (duration > 0 && duration - position <= 30_000) {
                val remaining = (duration - position).coerceAtLeast(0)
                countdownSeconds = ((remaining + 999) / 1_000).toInt()
                nextPromptVisible = true
                if (remaining <= 0 && autoNextEnabled && !nextStarted) {
                    nextStarted = true
                    onPlayNext(nextEpisode)
                    break
                }
            }
            delay(500)
        }
    }

    LaunchedEffect(source) {
        delay(700)
        if (chromeVisible) playFocus.requestFocus()
    }

    LaunchedEffect(chromeVisible) {
        if (chromeVisible) {
            delay(80)
            playFocus.requestFocus()
        }
    }

    LaunchedEffect(episodeMenuOpen) {
        if (episodeMenuOpen && episodeList.isNotEmpty()) {
            delay(80)
            episodeFocus.requestFocus()
        }
    }

    LaunchedEffect(qualityMenuOpen) {
        delay(80)
        runCatching {
            if (qualityMenuOpen) qualityOptionFocus.requestFocus() else qualityFocus.requestFocus()
        }
    }

    LaunchedEffect(player, chromeVisible) {
        while (chromeVisible) {
            playbackPosition = player.currentPosition.coerceAtLeast(0L)
            playbackDuration = player.duration.coerceAtLeast(0L)
            isPlaying = player.isPlaying
            val seconds = playbackPosition / 1000L
            val recap = recapEndSeconds ?: 0L
            val intro = introEndSeconds ?: 0L
            if (recap > 0 && seconds > 1 && seconds < recap - 2) {
                skipLabel = "Skip recap"
                skipTargetMs = recap * 1000L
            } else if (intro > 0 && seconds > maxOf(recap, 1L) && seconds < intro - 2) {
                skipLabel = "Skip intro"
                skipTargetMs = intro * 1000L
            } else {
                skipLabel = null
                skipTargetMs = null
            }
            delay(250)
        }
    }

    LaunchedEffect(interactionTick, chromeVisible, qualityMenuOpen, subtitleMenuOpen, sourceMenuOpen, episodeMenuOpen, settingsMenuOpen) {
        if (chromeVisible && !qualityMenuOpen && !subtitleMenuOpen && !sourceMenuOpen && !episodeMenuOpen && !settingsMenuOpen) {
            delay(5_000)
            chromeVisible = false
        }
    }

    LaunchedEffect(player) {
        while (true) {
            delay(10_000)
            val duration = player.duration
            if (duration > 0L && player.currentPosition >= 0L) {
                onProgress(player.currentPosition, duration)
            }
        }
    }

    LaunchedEffect(nextPromptVisible, nextEpisode) {
        if (nextPromptVisible && nextEpisode != null && !nextPromptDismissed) {
            nextActionFocused = "play"
            repeat(4) {
                withFrameNanos { }
                delay(80)
                val focused = runCatching { nextFocus.requestFocus(); true }.getOrDefault(false)
                if (focused) return@LaunchedEffect
            }
        }
    }

    DisposableEffect(player, nextEpisode) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && nextEpisode != null && !nextPromptDismissed && !nextStarted) {
                    nextStarted = true
                    onPlayNext(nextEpisode)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            val duration = player.duration
            if (duration > 0L && player.currentPosition >= 0L) {
                onProgress(player.currentPosition, duration)
            }
            player.removeListener(listener)
            player.release()
        }
    }
    BackHandler {
        when {
            nextPromptVisible && !nextPromptDismissed -> {
                nextPromptDismissed = true
                nextPromptVisible = false
            }
            qualityMenuOpen -> qualityMenuOpen = false
            subtitleMenuOpen -> subtitleMenuOpen = false
            sourceMenuOpen -> sourceMenuOpen = false
            episodeMenuOpen -> episodeMenuOpen = false
            settingsMenuOpen -> settingsMenuOpen = false
            else -> onBack()
        }
    }

    fun keepControlsVisible(modifier: Modifier = Modifier): Modifier = modifier.onFocusChanged {
        if (it.isFocused) {
            chromeVisible = true
            interactionTick++
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(playerFocus)
            .focusable()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && nextPromptVisible && nextEpisode != null && !nextPromptDismissed && event.key in setOf(Key.DirectionCenter, Key.Enter)) {
                    if (nextActionFocused == "close") {
                        nextPromptDismissed = true
                        nextPromptVisible = false
                    } else if (!nextStarted) {
                        nextStarted = true
                        onPlayNext(nextEpisode)
                    }
                    true
                } else if (event.type == KeyEventType.KeyDown && !chromeVisible && event.key == Key.DirectionLeft) {
                    player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                    chromeVisible = true
                    interactionTick++
                    true
                } else if (event.type == KeyEventType.KeyDown && !chromeVisible && event.key == Key.DirectionRight) {
                    player.seekTo((player.currentPosition + 10_000L).coerceAtMost(player.duration.coerceAtLeast(0L)))
                    chromeVisible = true
                    interactionTick++
                    true
                } else if (event.type == KeyEventType.KeyDown && event.key in setOf(
                        Key.DirectionUp,
                        Key.DirectionDown,
                        Key.DirectionCenter,
                        Key.Enter,
                    )) {
                    chromeVisible = true
                    interactionTick++
                    false
                } else {
                    false
                }
            },
    ) {
        AndroidView(
            { PlayerView(it).apply {
                this.player = player
                isFocusable = false
                isFocusableInTouchMode = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setEnableComposeSurfaceSyncWorkaround(true)
                setKeepContentOnPlayerReset(true)
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            } },
            update = { view ->
                if (view.player !== player) view.player = player
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (chromeVisible) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.72f),
                        0.42f to Color.Transparent,
                        0.70f to Color.Transparent,
                        1.0f to Color.Black.copy(alpha = 0.90f),
                    ),
                ),
            )
            Row(
                Modifier.align(Alignment.TopStart).fillMaxWidth().padding(start = 24.dp, top = 20.dp, end = 28.dp),
                verticalAlignment = Alignment.Top,
            ) {
                TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("←", color = Color.White, fontSize = 26.sp)
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("NOW PLAYING", color = Purple, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (season != null && episode != null) "S${season}E${episode} · Streammore" else "Streammore",
                        color = Muted,
                        fontSize = 13.sp,
                    )
                }
            }

            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 28.dp, vertical = 22.dp),
            ) {
                if (skipLabel != null && skipTargetMs != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TvButton(onClick = { player.seekTo(skipTargetMs!!); skipLabel = null }) {
                            Text(skipLabel!!, color = TextPrimary, fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (playbackDuration > 0L) {
                    Slider(
                        value = (playbackPosition.toFloat() / playbackDuration.toFloat()).coerceIn(0f, 1f),
                        onValueChange = { fraction ->
                            seekToPosition((fraction * playbackDuration).toLong())
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Purple,
                            activeTrackColor = Purple,
                            inactiveTrackColor = Color.White.copy(alpha = 0.28f),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .focusRequester(seekFocus)
                            .focusable()
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (event.key) {
                                    Key.DirectionLeft -> { seekBy(-10_000L); true }
                                    Key.DirectionRight -> { seekBy(10_000L); true }
                                    else -> false
                                }
                            }
                            .focusProperties { down = playFocus }
                            .onFocusChanged { if (it.isFocused) { chromeVisible = true; interactionTick++ } },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { player.playWhenReady = !player.isPlaying },
                        modifier = Modifier
                            .focusRequester(playFocus)
                            .focusProperties { up = seekFocus }
                            .onFocusChanged { if (it.isFocused) { chromeVisible = true; interactionTick++ } },
                    ) {
                        Text(if (isPlaying) "Ⅱ" else "▶", color = Color.White, fontSize = 25.sp)
                    }
                    TextButton(onClick = { player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)) }, modifier = keepControlsVisible()) {
                        Text("↶10", color = Color.White, fontSize = 15.sp)
                    }
                    TextButton(onClick = { player.seekTo((player.currentPosition + 10_000L).coerceAtMost(player.duration.coerceAtLeast(0L))) }, modifier = keepControlsVisible()) {
                        Text("10↷", color = Color.White, fontSize = 15.sp)
                    }
                    Text(
                        "${formatPlayerTime(playbackPosition)} / ${formatPlayerTime(playbackDuration)}",
                        color = Color(0xFFE0DCE6),
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    if (subtitles.isNotEmpty()) {
                        TextButton(onClick = {
                            subtitleMenuOpen = !subtitleMenuOpen
                            qualityMenuOpen = false
                            sourceMenuOpen = false
                            episodeMenuOpen = false
                        }, modifier = keepControlsVisible()) { Text("CC${selectedSubtitle?.let { " $it" } ?: ""}", color = Color.White, fontSize = 13.sp) }
                    }
                    if (sources.size > 1) {
                        TextButton(onClick = {
                            sourceMenuOpen = !sourceMenuOpen
                            qualityMenuOpen = false
                            subtitleMenuOpen = false
                            episodeMenuOpen = false
                        }, modifier = keepControlsVisible()) { Text("☰ Sources", color = Color.White, fontSize = 13.sp) }
                    }
                    if (episodeList.isNotEmpty()) {
                        TextButton(onClick = {
                            episodeMenuOpen = !episodeMenuOpen
                            qualityMenuOpen = false
                            subtitleMenuOpen = false
                            sourceMenuOpen = false
                        }, modifier = keepControlsVisible()) { Text("▣ Episodes", color = Color.White, fontSize = 13.sp) }
                    }
                    if (season != null && episode != null) {
                        TextButton(onClick = { }) { Text("S${season}E${episode}", color = Muted, fontSize = 13.sp) }
                    }
                    TvButton(
                        onClick = { qualityMenuOpen = !qualityMenuOpen },
                        modifier = keepControlsVisible(Modifier.focusRequester(qualityFocus)),
                    ) { Text("▦ $selectedQualityLabel", color = TextPrimary, fontSize = 13.sp) }
                    TvButton(onClick = { settingsMenuOpen = !settingsMenuOpen }, modifier = keepControlsVisible()) {
                        Text("⚙", color = TextPrimary, fontSize = 14.sp)
                    }
                }
            }
            if (qualityMenuOpen) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 92.dp).widthIn(min = 180.dp),
                    color = Panel.copy(alpha = 0.98f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, BorderIdle),
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("VIDEO QUALITY", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        TvButton(
                            onClick = { applyQuality(null) },
                            selected = selectedQualityLabel == "Auto",
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(qualityOptionFocus)
                                .focusProperties { up = qualityFocus },
                        ) {
                            Text("Auto", color = TextPrimary, fontSize = 14.sp)
                        }
                        if (availableQualities.isEmpty()) {
                            Text("Waiting for stream renditions…", color = Muted, fontSize = 12.sp)
                        } else {
                            availableQualities.forEachIndexed { index, quality ->
                                TvButton(
                                    onClick = { applyQuality(quality) },
                                    selected = quality.label == selectedQualityLabel,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusProperties { if (index == availableQualities.lastIndex) down = qualityFocus },
                                ) {
                                    Text(quality.label, color = TextPrimary, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
            if (settingsMenuOpen) {
                Surface(Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 92.dp).widthIn(min = 240.dp), color = Panel.copy(alpha = 0.98f), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, BorderIdle)) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("PLAYER SETTINGS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        TvButton(onClick = { autoNextEnabled = !autoNextEnabled }, selected = autoNextEnabled, modifier = Modifier.fillMaxWidth()) {
                            Text("Auto-play next episode: ${if (autoNextEnabled) "On" else "Off"}", color = TextPrimary, fontSize = 14.sp)
                        }
                        Text("Quality and subtitles are available in the player bar.", color = Muted, fontSize = 12.sp)
                    }
                }
            }
            if (subtitleMenuOpen) {
                Surface(Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 92.dp).widthIn(min = 230.dp), color = Panel.copy(alpha = 0.98f), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, BorderIdle)) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("SUBTITLES", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        TvButton(onClick = { applySubtitle(null) }, selected = selectedSubtitle == null, modifier = Modifier.fillMaxWidth()) { Text("Off", color = TextPrimary, fontSize = 14.sp) }
                        subtitles.forEach { track ->
                            TvButton(onClick = { applySubtitle(track.language) }, selected = selectedSubtitle == track.language, modifier = Modifier.fillMaxWidth()) { Text(track.label, color = TextPrimary, fontSize = 14.sp) }
                        }
                    }
                }
            }
            if (sourceMenuOpen) {
                Surface(Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 92.dp).widthIn(min = 280.dp), color = Panel.copy(alpha = 0.98f), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, BorderIdle)) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("SOURCES", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        sources.forEachIndexed { index, sourceItem ->
                            TvButton(onClick = { onSelectSource(sourceItem, player.currentPosition); sourceMenuOpen = false }, selected = sourceItem.url == source, modifier = Modifier.fillMaxWidth()) {
                                Text("${sourceItem.quality.ifBlank { "Source ${index + 1}" }} · ${sourceItem.name}", color = TextPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            if (episodeMenuOpen) {
                Surface(Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 92.dp).widthIn(min = 330.dp), color = Panel.copy(alpha = 0.98f), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, BorderIdle)) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("EPISODES", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        LazyColumn(Modifier.heightIn(max = 360.dp)) {
                            items(episodeList) { item ->
                                TvButton(
                                    onClick = { onSelectEpisode(item); episodeMenuOpen = false },
                                    selected = item.number == episode,
                                    modifier = keepControlsVisible(Modifier.then(if (item.number == episodeList.firstOrNull()?.number) Modifier.focusRequester(episodeFocus) else Modifier)).fillMaxWidth(),
                                ) {
                                    Text("${item.number}. ${item.name}${if (item.watched) " · WATCHED" else if (item.progress > 0) " · ${(item.progress * 100).toInt()}%" else ""}", color = TextPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (nextPromptVisible && nextEpisode != null && !nextPromptDismissed) {
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 34.dp, bottom = 42.dp).widthIn(max = 360.dp),
                color = Color(0xF21A1425),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderIdle),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("UP NEXT", color = Purple, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text("${nextEpisode.season}x${nextEpisode.episode}  ${nextEpisode.title}", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("Playing next in ${countdownSeconds.coerceAtLeast(0)}…", color = Muted, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TvButton(
                            onClick = {
                                if (!nextStarted) {
                                    nextStarted = true
                                    onPlayNext(nextEpisode)
                                }
                            },
                            primary = true,
                            modifier = Modifier
                                .focusRequester(nextFocus)
                                .focusProperties { right = nextCloseFocus },
                            onFocusGained = { nextActionFocused = "play" },
                        ) {
                            Text("▶ Play next", fontSize = 14.sp)
                        }
                        TvButton(
                            onClick = {
                                nextPromptDismissed = true
                                nextPromptVisible = false
                            },
                            modifier = Modifier
                                .focusRequester(nextCloseFocus)
                                .focusProperties { left = nextFocus },
                            onFocusGained = { nextActionFocused = "close" },
                        ) {
                            Text("Close", color = TextPrimary, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
        playerError?.let { ErrorBanner(it, Modifier.align(Alignment.BottomCenter)) }
    }
}

internal fun listOfNotNull(vararg values: Any?): List<String> = values.filterNotNull().map { it.toString() }
