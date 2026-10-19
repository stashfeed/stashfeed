package gomeng.dev.stashplayer.core.model

data class SceneCardMetadataBadge(
    val id: String,
    val label: String,
)

data class SceneCardTagChip(
    val id: String,
    val label: String,
)

data class SceneCardModel(
    val id: String,
    val title: String,
    val durationText: String,
    val studio: String,
    val progress: Float,
    val isInWatchLater: Boolean = false,
    val thumbnailUrl: String? = null,
    val playCount: Int? = null,
    val metadataBadges: List<SceneCardMetadataBadge> = emptyList(),
    val tagChips: List<SceneCardTagChip> = emptyList(),
    val performerChips: List<SceneCardTagChip> = emptyList(),
    /** Release date (`date`), `yyyy-MM-dd`. */
    val date: String? = null,
    /** When the scene was added to Stash (`created_at`), ISO-8601. */
    val createdAt: String? = null,
    val oCounter: Int? = null,
    val rating100: Int? = null,
) {
    val subtitle: String
        get() = buildList {
            studio.takeIf { it.isNotBlank() }?.let(::add)
            durationText.takeIf { it.isNotBlank() }?.let(::add)
            playCount?.takeIf { it > 0 }?.let { add("$it plays") }
        }.joinToString(" · ")
}

data class QueueItemModel(
    val id: String,
    val title: String,
    val subtitle: String,
)

data class ServerProfileDraft(
    val name: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
)
