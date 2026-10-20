package gomeng.dev.stashplayer.core.model

import androidx.annotation.StringRes
import gomeng.dev.stashplayer.R
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

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
    /** Kept so saved settings that hide it still parse; scenes are never grouped any more. */
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
    /** Move posts seen before the last refresh below a "You're all caught up" divider. */
    val hideSeen: Boolean = true,
) {
    fun isAllowed(gender: StashPerformerGender?): Boolean = gender == null || gender in allowedGenders
}

/** Narrows the Home feed to performers / tags / studios (any match), applied to scenes and galleries alike. */
data class HomeFeedFilter(
    val performers: List<StashSelectedEntity> = emptyList(),
    val tags: List<StashSelectedEntity> = emptyList(),
    val studios: List<StashSelectedEntity> = emptyList(),
    /** True: the list is "all except these" (Stash EXCLUDES), which also covers entries added later. */
    val performersExcluded: Boolean = false,
    val tagsExcluded: Boolean = false,
    val studiosExcluded: Boolean = false,
) {
    val activeCount: Int get() = performers.size + tags.size + studios.size

    fun toGraphQlCriteria(): Map<String, Any?> = buildMap {
        criterion(performers, performersExcluded, hierarchical = false)?.let { put("performers", it) }
        criterion(tags, tagsExcluded, hierarchical = true)?.let { put("tags", it) }
        criterion(studios, studiosExcluded, hierarchical = true)?.let { put("studios", it) }
    }

    private fun criterion(entities: List<StashSelectedEntity>, excluded: Boolean, hierarchical: Boolean): Map<String, Any?>? {
        if (entities.isEmpty()) return null
        return buildMap {
            put("value", entities.map { it.id })
            put("modifier", if (excluded) "EXCLUDES" else "INCLUDES")
            if (hierarchical) put("depth", 0)
        }
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
    /** Video frame size, for showing the post in its real aspect ratio. */
    val width: Int? = null,
    val height: Int? = null,
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
 * performers), reposts for back-catalog scenes added recently, gallery posts,
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

    // Every scene is its own post, even when one performer got many on the same day.
    val posts = buildList {
        identified.forEach { scene ->
            val repost = isRepost(scene)
            val kind = if (repost) HomeFeedKind.Reposts else HomeFeedKind.Posts
            if (kind !in settings.hiddenKinds) add(HomeFeedItem.Post(scene, repost))
        }
        if (HomeFeedKind.Unidentified !in settings.hiddenKinds) {
            unidentified.forEach { add(HomeFeedItem.Post(it, isRepost(it))) }
        }
    }

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
        items = (posts + galleryItems).sortedByDescending { it.createdAt },
        stories = stories,
        unidentifiedSceneCount = unidentified.size,
        unidentifiedGalleryCount = unidentifiedGalleries.size,
    )
}

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

/** Seen posts are kept this long (the longest feed window), then dropped so the stored list stays small. */
const val SEEN_FEED_POSTS_KEEP_DAYS = 90L

/** A post counts as seen after being at least half on screen for this long, like Instagram. */
const val SEEN_FEED_POST_DWELL_MS = 1_000L
const val SEEN_FEED_POST_MIN_VISIBLE_FRACTION = 0.5f

/** Drops seen entries whose post was added more than [keepDays] before [today] (`yyyy-MM-dd` strings). */
fun pruneSeenFeedPosts(seen: Map<String, String>, today: java.time.LocalDate, keepDays: Long = SEEN_FEED_POSTS_KEEP_DAYS): Map<String, String> {
    val cutoff = today.minusDays(keepDays).toString()
    return seen.filterValues { addedDate -> addedDate >= cutoff }
}

data class HomeFeedSections(
    val unseen: List<HomeFeedItem>,
    val seen: List<HomeFeedItem>,
)

/** Splits the feed into posts not yet seen (in feed order) and the ones seen before the last refresh. */
fun HomeFeed.splitBySeen(seenKeys: Set<String>): HomeFeedSections {
    val (seen, unseen) = items.partition { it.key in seenKeys }
    return HomeFeedSections(unseen = unseen, seen = seen)
}
