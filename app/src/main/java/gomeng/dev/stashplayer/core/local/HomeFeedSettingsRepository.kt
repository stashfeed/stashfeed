package gomeng.dev.stashplayer.core.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import gomeng.dev.stashplayer.core.model.HOME_FEED_DEFAULT_IGNORED_GALLERY_FOLDERS
import gomeng.dev.stashplayer.core.model.HOME_FEED_DEFAULT_LOOKBACK_DAYS
import gomeng.dev.stashplayer.core.model.HOME_FEED_LOOKBACK_DAY_OPTIONS
import gomeng.dev.stashplayer.core.model.HomeFeedKind
import gomeng.dev.stashplayer.core.model.HomeFeedSettings
import gomeng.dev.stashplayer.core.model.StashPerformerGender
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.homeFeedSettingsDataStore by preferencesDataStore(name = "binge_home_feed")

/** Home feed preferences (window, genders, ignored folders, hidden post kinds), kept apart from the app settings. */
class HomeFeedSettingsRepository(private val context: Context) {
    val settings: Flow<HomeFeedSettings> = context.homeFeedSettingsDataStore.data.map { prefs ->
        val defaults = HomeFeedSettings()
        HomeFeedSettings(
            lookbackDays = prefs[Keys.LookbackDays]?.takeIf { it in HOME_FEED_LOOKBACK_DAY_OPTIONS } ?: HOME_FEED_DEFAULT_LOOKBACK_DAYS,
            allowedGenders = prefs[Keys.AllowedGenders]?.let { stored ->
                stored.split(',').mapNotNull { name -> StashPerformerGender.entries.firstOrNull { it.name == name } }.toSet()
            } ?: defaults.allowedGenders,
            ignoredGalleryFolders = prefs[Keys.IgnoredGalleryFolders]?.let(::parseFolderList) ?: HOME_FEED_DEFAULT_IGNORED_GALLERY_FOLDERS,
            hiddenKinds = prefs[Keys.HiddenKinds]?.let { stored ->
                stored.split(',').mapNotNull { name -> HomeFeedKind.entries.firstOrNull { it.name == name } }.toSet()
            } ?: defaults.hiddenKinds,
            hideSeen = prefs[Keys.HideSeen] ?: defaults.hideSeen,
        )
    }

    suspend fun save(settings: HomeFeedSettings) {
        context.homeFeedSettingsDataStore.edit { prefs ->
            prefs[Keys.LookbackDays] = settings.lookbackDays
            prefs[Keys.AllowedGenders] = settings.allowedGenders.joinToString(",") { it.name }
            prefs[Keys.IgnoredGalleryFolders] = settings.ignoredGalleryFolders.joinToString(", ")
            prefs[Keys.HiddenKinds] = settings.hiddenKinds.joinToString(",") { it.name }
            prefs[Keys.HideSeen] = settings.hideSeen
        }
    }

    private object Keys {
        val LookbackDays = intPreferencesKey("lookback_days")
        val AllowedGenders = stringPreferencesKey("allowed_genders")
        val IgnoredGalleryFolders = stringPreferencesKey("ignored_gallery_folders")
        val HiddenKinds = stringPreferencesKey("hidden_kinds")
        val HideSeen = booleanPreferencesKey("hide_seen")
    }
}

fun parseFolderList(value: String): List<String> = value.split(',').map { it.trim() }.filter { it.isNotBlank() }
