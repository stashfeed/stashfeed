package gomeng.dev.stashplayer.core.model

import androidx.annotation.StringRes
import gomeng.dev.stashplayer.R
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/** Same thresholds as the Binge plugin: 8+ scenes of one performer added within 7 days become a pack. */
const val HOME_FEED_PACK_MIN_SCENES = 8
const val HOME_FEED_PACK_WINDOW_MS = 7L * 24 * 60 * 60 * 1000
val HOME_FEED_LOOKBACK_DAY_OPTIONS = listOf(7, 14, 30, 60, 90)
const val HOME_FEED_DEFAULT_LOOKBACK_DAYS = 30
val HOME_FEED_DEFAULT_IGNORED_GALLERY_FOLDERS = listOf("screen*", "scr", "cover", "covers", "proof")

enum class StashPerformerGender(val graphQlValue: String, @StringRes val labelRes: Int) {
    Female("FEMALE", R.string.gender_female),
    Male("MALE", R.string.gender_male),
    TransgenderFemale("TRANSGENDER_FEMALE", R.string.gender_transgender_female),
    TransgenderMale("TRANSGENDER_MALE", R.string.gender_transgender_male),
    NonBinary("NON_BINARY", R.string.gender_non_binary),
    Intersex("INTERSEX", R.string.gender_intersex),
    ;

    companion object {
        fun fromGraphQl(value: String?): StashPerformerGender? = entries.firstOrNull { it.graphQlValue == value }
    }
}

/** Which kinds of posts the Home feed shows; toggled in the feed's filter menu. */
enum class HomeFeedKind(@StringRes val labelRes: Int) {
    Posts(R.string.home_feed_kind_posts),
    Reposts(R.string.home_feed_kind_reposts),
    Packs(R.string.home_feed_kind_packs),
    Galleries(R.string.home_feed_kind_galleries),
    Unidentified(R.string.home_feed_kind_unidentified),
}

data class HomeFeedSettings(
    val lookbackDays: Int = HOME_FEED_DEFAULT_LOOKBACK_DAYS,
    /** Performers of these genders get stories and packs; others still appear on the posts they're in. */
    val allowedGenders: Set<StashPerformerGender> = StashPerformerGender.entries.toSet(),
    val ignoredGalleryFolders: List<String> = HOME_FEED_DEFAULT_IGNORED_GALLERY_FOLDERS,
    val hiddenKinds: Set<HomeFeedKind> = setOf(HomeFeedKind.Unidentified),
) {
    fun isAllowed(gender: StashPerformerGender?): Boolean = gender == null || gender in allowedGenders
}

/** Narrows the Home feed to performers / tags / studios (any match), applied to scenes and galleries alike. */
data class HomeFeedFilter(
    val performers: List<StashSelectedEntity> = emptyList(),
    val tags: List<StashSelectedEntity> = emptyList(),
    val studios: List<StashSelectedEntity> = emptyList(),
) {
    val activeCount: Int get() = performers.size + tags.size + studios.size

    fun toGraphQlCriteria(): Map<String, Any?> = buildMap {
        if (performers.isNotEmpty()) put("performers", mapOf("value" to performers.map { it.id }, "modifier" to "INCLUDES"))
        if (tags.isNotEmpty()) put("tags", mapOf("value" to tags.map { it.id }, "modifier" to "INCLUDES", "depth" to 0))
        if (studios.isNotEmpty()) put("studios", mapOf("value" to studios.map { it.id }, "modifier" to "INCLUDES", "depth" to 0))
    }
}

data class FeedPerformer(
    val id: String,
    val name: String,
    val imageUrl: String? = null,
    val gender: StashPerformerGender? = null,
    val favorite: Boolean = false,
)

data class FeedScene(
    val scene: SceneCardModel,
    val performers: List<FeedPerformer>,
    /** Short preview clip (`paths.preview`), used by stories. */
    val previewUrl: String? = null,
    /** Animated WebP preview; a fallback when the video preview can't be decoded. */
    val animatedPreviewUrl: String? = null,
    val createdAt: String,
    val date: String? = null,
)

data class FeedGallery(
    val gallery: GalleryCardModel,
    val performers: List<FeedPerformer>,
    val createdAt: String,
    /** Folder and file paths, used to drop screenshot / cover folders. */
    val paths: List<String> = emptyList(),
)

sealed interface HomeFeedItem {
    val key: String
    val createdAt: String

    data class Post(val scene: FeedScene, val isRepost: Boolean) : HomeFeedItem {
        override val key: String get() = "scene:${scene.scene.id}"
        override val createdAt: String get() = scene.createdAt
    }

    data class GalleryPost(val gallery: FeedGallery) : HomeFeedItem {
        override val key: String get() = "gallery:${gallery.gallery.id}"
        override val createdAt: String get() = gallery.createdAt
    }

    /** A batch of scenes one performer got in a short time, shown as one swipeable post. */
    data class Pack(val performer: FeedPerformer, val scenes: List<FeedScene>, val isRepost: Boolean) : HomeFeedItem {
        override val key: String get() = "pack:${performer.id}:${scenes.first().scene.id}"
        override val createdAt: String get() = scenes.first().createdAt
    }
}

/** A performer with new scenes in the window; the story plays their preview clips. */
data class HomeStory(
    val performer: FeedPerformer,
    val scenes: List<FeedScene>,
) {
    val latestCreatedAt: String get() = scenes.firstOrNull()?.createdAt.orEmpty()
}

data class HomeFeed(
    val items: List<HomeFeedItem>,
    val stories: List<HomeStory>,
    val unidentifiedSceneCount: Int,
    val unidentifiedGalleryCount: Int,
)

/**
 * Builds the Home feed the way the Binge plugin does: newest-added first, a post per scene (with its
 * performers), packs for bursts of one performer, reposts for back-catalog scenes added recently, gallery posts,
 * and a story per performer with new scenes.
 *
 * @param windowStartDate `yyyy-MM-dd`; a scene released before it but added inside the window is a repost.
 */
fun buildHomeFeed(
    scenes: List<FeedScene>,
    galleries: List<FeedGallery>,
    settings: HomeFeedSettings,
    windowStartDate: String,
): HomeFeed {
    val distinctScenes = scenes.distinctBy { it.scene.id }.sortedByDescending { it.createdAt }
    val (identified, unidentified) = distinctScenes.partition { it.performers.isNotEmpty() }
    fun isRepost(scene: FeedScene) = scene.date != null && scene.date < windowStartDate

    // Packs: per primary (allowed) performer, the newest scenes that sit within 7 days of the newest one.
    val packedSceneIds = mutableSetOf<String>()
    val packs = mutableListOf<HomeFeedItem.Pack>()
    if (HomeFeedKind.Packs !in settings.hiddenKinds) {
        identified
            .groupBy { scene -> scene.primaryPerformer(settings) }
            .forEach { (performer, performerScenes) ->
                if (performer == null) return@forEach
                val newest = performerScenes.first().createdAt.toEpochMillisOrNull() ?: return@forEach
                val burst = performerScenes.filter { scene ->
                    val added = scene.createdAt.toEpochMillisOrNull() ?: return@filter false
                    newest - added <= HOME_FEED_PACK_WINDOW_MS
                }
                if (burst.size >= HOME_FEED_PACK_MIN_SCENES) {
                    packs += HomeFeedItem.Pack(performer, burst, isRepost = burst.all(::isRepost))
                    burst.forEach { packedSceneIds += it.scene.id }
                }
            }
    }

    val posts = buildList {
        identified.filter { it.scene.id !in packedSceneIds }.forEach { scene ->
            val repost = isRepost(scene)
            val kind = if (repost) HomeFeedKind.Reposts else HomeFeedKind.Posts
            if (kind !in settings.hiddenKinds) add(HomeFeedItem.Post(scene, repost))
        }
        if (HomeFeedKind.Unidentified !in settings.hiddenKinds) {
            unidentified.forEach { add(HomeFeedItem.Post(it, isRepost(it))) }
        }
    }
    val packItems = packs.filter { pack -> (if (pack.isRepost) HomeFeedKind.Reposts else HomeFeedKind.Posts) !in settings.hiddenKinds }

    val noise = galleryNoiseMatcher(settings.ignoredGalleryFolders)
    val visibleGalleries = galleries.distinctBy { it.gallery.id }.filterNot { noise(it.paths) }
    val (identifiedGalleries, unidentifiedGalleries) = visibleGalleries.partition { it.performers.isNotEmpty() }
    val galleryItems = if (HomeFeedKind.Galleries in settings.hiddenKinds) {
        emptyList()
    } else {
        identifiedGalleries.map(HomeFeedItem::GalleryPost)
    }

    val stories = identified
        .flatMap { scene -> scene.performers.filter { settings.isAllowed(it.gender) }.map { it to scene } }
        .groupBy({ it.first.id }, { it })
        .map { (_, pairs) -> HomeStory(performer = pairs.first().first, scenes = pairs.map { it.second }.distinctBy { it.scene.id }) }
        .sortedWith(compareByDescending<HomeStory> { it.performer.favorite }.thenByDescending { it.latestCreatedAt })

    return HomeFeed(
        items = (posts + packItems + galleryItems).sortedByDescending { it.createdAt },
        stories = stories,
        unidentifiedSceneCount = unidentified.size,
        unidentifiedGalleryCount = unidentifiedGalleries.size,
    )
}

/** First performer whose gender is allowed: who a post "belongs" to for packs. */
private fun FeedScene.primaryPerformer(settings: HomeFeedSettings): FeedPerformer? =
    performers.firstOrNull { settings.isAllowed(it.gender) }

/** Folder names to ignore; a trailing `*` matches by prefix ("screen*" matches "screenshots"). */
internal fun galleryNoiseMatcher(patterns: List<String>): (List<String>) -> Boolean {
    val exact = mutableSetOf<String>()
    val prefixes = mutableListOf<String>()
    patterns.map { it.trim().lowercase() }.filter { it.isNotBlank() }.forEach { pattern ->
        if (pattern.endsWith("*")) pattern.dropLast(1).takeIf { it.isNotBlank() }?.let(prefixes::add) else exact += pattern
    }
    if (exact.isEmpty() && prefixes.isEmpty()) return { false }
    return { paths ->
        paths.any { path ->
            path.replace('\\', '/').split('/').map { it.trim().lowercase() }.filter { it.isNotBlank() }.any { segment ->
                segment in exact || prefixes.any(segment::startsWith)
            }
        }
    }
}

internal fun String.toEpochMillisOrNull(): Long? = try {
    OffsetDateTime.parse(this).toInstant().toEpochMilli()
} catch (_: DateTimeParseException) {
    runCatching { Instant.parse(this).toEpochMilli() }.getOrNull()
}
