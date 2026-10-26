package gomeng.dev.stashplayer.feature.downloads

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Links shared to Stashfeed from another app, waiting for the Downloads screen to pick them up. */
object StashShareInbox {
    private val _pending = MutableStateFlow<List<String>>(emptyList())
    val pending: StateFlow<List<String>> = _pending.asStateFlow()

    /** Takes the web links out of shared text ("Look at this https://…" from Reddit and others). */
    fun offer(sharedText: String?) {
        val links = extractSharedUrls(sharedText)
        if (links.isNotEmpty()) _pending.value = (_pending.value + links).distinct()
    }

    fun take(): List<String> = _pending.value.also { _pending.value = emptyList() }
}

private val SHARED_URL = Regex("""https?://[^\s<>"']+""")

/** Every web link in [text], in order and without duplicates; trailing punctuation from the sentence is dropped. */
internal fun extractSharedUrls(text: String?): List<String> =
    text?.let { SHARED_URL.findAll(it).map { match -> match.value.trimEnd('.', ',', ')', ']', ';', '!', '?') }.distinct().toList() }.orEmpty()

internal fun extractSharedUrl(text: String?): String? = extractSharedUrls(text).firstOrNull()
