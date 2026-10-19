package gomeng.dev.stashplayer.core.model

import kotlin.random.Random

/** How many related candidates each related query asks for. */
const val FOR_YOU_CHAINED_RELATED_PAGE_SIZE = 30
/** Only the best-scoring candidates take part in the weighted pick. */
private const val CHAINED_WEIGHTED_POOL = 10
/** How many of the strongest performers / tags a related query uses. */
private const val CHAINED_TOP_SIGNALS = 3
/** Performers count more than tags when scoring a candidate. */
private const val CHAINED_PERFORMER_WEIGHT = 1.5
private const val CHAINED_MIN_WEIGHT = 0.05

/**
 * Session-adaptive "Chained" reel, ported from the Binge plugin.
 *
 * Every played scene adds weight to its performers and tags, and all weights decay a little per play. Each
 * next pick is, with probability [chainRate], a weighted choice among scenes related to the strongest
 * performers / tags; otherwise it is a random scene. After [branchThreshold] picks that keep the same dominant
 * performer or tag, the next pick is forced random so the reel doesn't get stuck on one person.
 */
class ForYouChainedFeed(
    private val fetchRelated: suspend (filter: StashVideoFilterState) -> List<SceneCardModel>,
    private val fetchRandomPage: suspend (page: Int, perPage: Int) -> List<SceneCardModel>,
    private val chainRate: Double = 0.8,
    private val decayRate: Double = 0.85,
    private val branchThreshold: Int = 4,
    private val random: Random = Random.Default,
    /** Constraints that also apply to related picks, e.g. "short clips only". */
    private val relatedBaseFilter: StashVideoFilterState = StashVideoFilterState(),
    /** First page [fetchRandomPage] is asked for; pages before it were already shown. */
    firstRandomPage: Int = 1,
) {
    private val performerWeights = linkedMapOf<String, Double>()
    private val tagWeights = linkedMapOf<String, Double>()
    private val visited = mutableSetOf<String>()
    private var sameDominantStreak = 0
    private var lastDominantKey: String? = null
    private var nextRandomPage = firstRandomPage
    private var randomExhausted = false

    val performerSignal: Map<String, Double> get() = performerWeights
    val tagSignal: Map<String, Double> get() = tagWeights
    val dominantStreak: Int get() = sameDominantStreak

    fun onPlay(scene: SceneCardModel) {
        visited += scene.id
        decay(performerWeights)
        decay(tagWeights)
        scene.performerChips.forEach { performerWeights[it.id] = (performerWeights[it.id] ?: 0.0) + 1.0 }
        scene.tagChips.forEach { tagWeights[it.id] = (tagWeights[it.id] ?: 0.0) + 1.0 }
        updateDominantStreak()
    }

    /** Marks scenes as already shown so they are never picked again (e.g. the first random page). */
    fun markVisited(sceneIds: Collection<String>) {
        visited += sceneIds
    }

    suspend fun nextBatch(size: Int): List<SceneCardModel> {
        val related = fetchRelatedCandidates().sortedByDescending(::score)
        val picked = mutableListOf<SceneCardModel>()
        val pickedIds = mutableSetOf<String>()
        val randomBuffer = ArrayDeque<SceneCardModel>()

        suspend fun refillRandom() {
            while (randomBuffer.isEmpty() && !randomExhausted) {
                val page = fetchRandomPage(nextRandomPage, maxOf(size, 10))
                nextRandomPage += 1
                if (page.isEmpty()) randomExhausted = true
                page.filter { it.id !in visited && it.id !in pickedIds }.forEach(randomBuffer::addLast)
            }
        }

        repeat(size) {
            val forceBranch = sameDominantStreak >= branchThreshold
            val hasSignal = performerWeights.isNotEmpty() || tagWeights.isNotEmpty()
            if (!forceBranch && hasSignal && random.nextDouble() < chainRate) {
                weightedPick(related.filter { it.id !in pickedIds })?.let { scene ->
                    picked += scene
                    pickedIds += scene.id
                    return@repeat
                }
            }
            refillRandom()
            val scene = randomBuffer.removeFirstOrNull() ?: return picked
            picked += scene
            pickedIds += scene.id
            if (forceBranch) sameDominantStreak = 0
        }
        return picked
    }

    internal fun score(scene: SceneCardModel): Double =
        scene.performerChips.sumOf { (performerWeights[it.id] ?: 0.0) * CHAINED_PERFORMER_WEIGHT } +
            scene.tagChips.sumOf { tagWeights[it.id] ?: 0.0 }

    private fun weightedPick(candidates: List<SceneCardModel>): SceneCardModel? {
        if (candidates.isEmpty()) return null
        val pool = candidates.take(CHAINED_WEIGHTED_POOL)
        val total = pool.sumOf(::score)
        if (total <= 0.0) return pool[random.nextInt(pool.size)]
        var remaining = random.nextDouble() * total
        for (scene in pool) {
            remaining -= score(scene)
            if (remaining <= 0.0) return scene
        }
        return pool.last()
    }

    private suspend fun fetchRelatedCandidates(): List<SceneCardModel> {
        val topPerformers = performerWeights.topKeys(CHAINED_TOP_SIGNALS)
        val topTags = tagWeights.topKeys(CHAINED_TOP_SIGNALS)
        val queries = buildList {
            if (topPerformers.isNotEmpty()) {
                add(relatedBaseFilter.copy(performers = topPerformers.map { StashSelectedEntity(it, it) }))
            }
            if (topTags.isNotEmpty()) {
                add(relatedBaseFilter.copy(tags = topTags.map { StashSelectedTag(it, it) }, tagsMatchAny = true))
            }
        }
        val seen = mutableSetOf<String>()
        return queries.flatMap { filter -> runCatching { fetchRelated(filter) }.getOrDefault(emptyList()) }
            .filter { it.id !in visited && seen.add(it.id) }
    }

    private fun decay(weights: MutableMap<String, Double>) {
        val iterator = weights.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val next = entry.value * decayRate
            if (next < CHAINED_MIN_WEIGHT) iterator.remove() else entry.setValue(next)
        }
    }

    private fun updateDominantStreak() {
        val performer = performerWeights.maxByOrNull { it.value }
        val tag = tagWeights.maxByOrNull { it.value }
        val dominant = when {
            performer != null && tag != null -> if (performer.value >= tag.value) "p:${performer.key}" else "t:${tag.key}"
            performer != null -> "p:${performer.key}"
            tag != null -> "t:${tag.key}"
            else -> null
        }
        if (dominant != null && dominant == lastDominantKey) {
            sameDominantStreak += 1
        } else {
            lastDominantKey = dominant
            sameDominantStreak = if (dominant == null) 0 else 1
        }
    }
}

private fun Map<String, Double>.topKeys(count: Int): List<String> =
    entries.sortedByDescending { it.value }.take(count).map { it.key }
