package gomeng.dev.stashplayer.core.network

import org.json.JSONArray
import org.json.JSONObject

/**
 * Server housekeeping from the app: Stash's library tasks (generate, auto tag, clean), its plugins and their tasks,
 * and stopping jobs. Scan and the job queue live on [StashGraphQlClient] already.
 */
data class StashPluginTask(
    val name: String,
    val description: String? = null,
)

data class StashPlugin(
    val id: String,
    val name: String,
    val version: String? = null,
    val description: String? = null,
    val enabled: Boolean = true,
    val tasks: List<StashPluginTask> = emptyList(),
)

/** What Stash's Generate task should create; the defaults come from the server's own Generate settings. */
data class StashGenerateOptions(
    val covers: Boolean = true,
    val previews: Boolean = true,
    val imagePreviews: Boolean = false,
    val sprites: Boolean = true,
    val phashes: Boolean = true,
    val markers: Boolean = false,
    val imageThumbnails: Boolean = true,
    val clipPreviews: Boolean = false,
    val overwrite: Boolean = false,
)

class StashServerAdminClient(private val client: StashGraphQlClient) {
    suspend fun plugins(): List<StashPlugin> = parsePluginsResponse(client.executeRaw(PLUGINS_QUERY))

    suspend fun setPluginEnabled(pluginId: String, enabled: Boolean) {
        graphQlData(client.executeRaw(SET_PLUGINS_ENABLED_MUTATION, mapOf("map" to mapOf(pluginId to enabled))))
    }

    suspend fun reloadPlugins() {
        graphQlData(client.executeRaw(RELOAD_PLUGINS_MUTATION))
    }

    /** Queues a plugin task; returns the job ID. */
    suspend fun runPluginTask(pluginId: String, taskName: String): String = jobIdOf(
        client.executeRaw(RUN_PLUGIN_TASK_MUTATION, mapOf("pluginId" to pluginId, "taskName" to taskName)),
        "runPluginTask",
    )

    /** Runs a plugin operation right away and returns its output as JSON (null when the plugin returned nothing). */
    suspend fun runPluginOperation(pluginId: String, args: Map<String, Any?>): JSONObject? {
        val data = graphQlData(client.executeRaw(RUN_PLUGIN_OPERATION_MUTATION, mapOf("pluginId" to pluginId, "args" to args)))
        return when (val output = data.opt("runPluginOperation")) {
            is JSONObject -> output
            is String -> runCatching { JSONObject(output) }.getOrNull()
            else -> null
        }
    }

    suspend fun pluginSettings(pluginId: String): JSONObject = graphQlData(client.executeRaw(PLUGIN_SETTINGS_QUERY))
        .optJSONObject("configuration")
        ?.optJSONObject("plugins")
        ?.optJSONObject(pluginId)
        ?: JSONObject()

    suspend fun configurePlugin(pluginId: String, settings: Map<String, Any?>) {
        graphQlData(client.executeRaw(CONFIGURE_PLUGIN_MUTATION, mapOf("pluginId" to pluginId, "input" to settings)))
    }

    suspend fun generateDefaults(): StashGenerateOptions = parseGenerateDefaultsResponse(client.executeRaw(GENERATE_DEFAULTS_QUERY))

    suspend fun generate(options: StashGenerateOptions): String = jobIdOf(
        client.executeRaw(GENERATE_MUTATION, mapOf("input" to buildGenerateInput(options))),
        "metadataGenerate",
    )

    /** Auto tag the whole library with every performer, studio and tag, as the web UI's button does. */
    suspend fun autoTag(): String = jobIdOf(
        client.executeRaw(
            AUTO_TAG_MUTATION,
            mapOf("input" to mapOf("performers" to listOf("*"), "studios" to listOf("*"), "tags" to listOf("*"))),
        ),
        "metadataAutoTag",
    )

    /** Removes database entries whose files are gone. */
    suspend fun clean(): String = jobIdOf(
        client.executeRaw(CLEAN_MUTATION, mapOf("input" to mapOf("dryRun" to false))),
        "metadataClean",
    )

    suspend fun stopJob(jobId: String) {
        graphQlData(client.executeRaw(STOP_JOB_MUTATION, mapOf("id" to jobId)))
    }

    private companion object {
        val PLUGINS_QUERY = """
            query AdminPlugins {
              plugins {
                id
                name
                version
                description
                enabled
                tasks { name description }
              }
            }
        """

        val SET_PLUGINS_ENABLED_MUTATION = """
            mutation SetPluginsEnabled(${'$'}map: BoolMap!) {
              setPluginsEnabled(enabledMap: ${'$'}map)
            }
        """

        val RELOAD_PLUGINS_MUTATION = """
            mutation ReloadPlugins { reloadPlugins }
        """

        val RUN_PLUGIN_TASK_MUTATION = """
            mutation RunPluginTask(${'$'}pluginId: ID!, ${'$'}taskName: String!) {
              runPluginTask(plugin_id: ${'$'}pluginId, task_name: ${'$'}taskName)
            }
        """

        val RUN_PLUGIN_OPERATION_MUTATION = """
            mutation AdminRunPluginOperation(${'$'}pluginId: ID!, ${'$'}args: Map!) {
              runPluginOperation(plugin_id: ${'$'}pluginId, args: ${'$'}args)
            }
        """

        val PLUGIN_SETTINGS_QUERY = """
            query PluginSettings { configuration { plugins } }
        """

        val CONFIGURE_PLUGIN_MUTATION = """
            mutation ConfigurePlugin(${'$'}pluginId: ID!, ${'$'}input: Map!) {
              configurePlugin(plugin_id: ${'$'}pluginId, input: ${'$'}input)
            }
        """

        val GENERATE_DEFAULTS_QUERY = """
            query GenerateDefaults {
              configuration {
                defaults {
                  generate {
                    covers
                    previews
                    imagePreviews
                    sprites
                    phashes
                    markers
                    imageThumbnails
                    clipPreviews
                  }
                }
              }
            }
        """

        val GENERATE_MUTATION = """
            mutation AdminGenerate(${'$'}input: GenerateMetadataInput!) {
              metadataGenerate(input: ${'$'}input)
            }
        """

        val AUTO_TAG_MUTATION = """
            mutation AdminAutoTag(${'$'}input: AutoTagMetadataInput!) {
              metadataAutoTag(input: ${'$'}input)
            }
        """

        val CLEAN_MUTATION = """
            mutation AdminClean(${'$'}input: CleanMetadataInput!) {
              metadataClean(input: ${'$'}input)
            }
        """

        val STOP_JOB_MUTATION = """
            mutation StopJob(${'$'}id: ID!) { stopJob(job_id: ${'$'}id) }
        """
    }
}

/** The `data` object of a GraphQL response; throws with Stash's message when it reports errors. */
internal fun graphQlData(json: String): JSONObject {
    val root = JSONObject(json)
    val errors = root.optJSONArray("errors")
    if (errors != null && errors.length() > 0) {
        val message = (0 until errors.length())
            .mapNotNull { errors.optJSONObject(it)?.optString("message")?.takeIf(String::isNotBlank) }
            .joinToString("; ")
            .ifBlank { "Unknown GraphQL error" }
        error(message)
    }
    return root.optJSONObject("data") ?: JSONObject()
}

private fun jobIdOf(json: String, field: String): String =
    graphQlData(json).optString(field).takeIf { it.isNotBlank() && it != "null" } ?: error("Stash $field returned no job ID")

internal fun parsePluginsResponse(json: String): List<StashPlugin> {
    val plugins = graphQlData(json).optJSONArray("plugins") ?: JSONArray()
    return (0 until plugins.length()).mapNotNull { index ->
        val plugin = plugins.optJSONObject(index) ?: return@mapNotNull null
        val id = plugin.optString("id").trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val tasks = plugin.optJSONArray("tasks") ?: JSONArray()
        StashPlugin(
            id = id,
            name = plugin.optNullableString("name") ?: id,
            version = plugin.optNullableString("version"),
            description = plugin.optNullableString("description"),
            enabled = plugin.optBoolean("enabled", true),
            tasks = (0 until tasks.length()).mapNotNull { taskIndex ->
                val task = tasks.optJSONObject(taskIndex) ?: return@mapNotNull null
                val name = task.optNullableString("name") ?: return@mapNotNull null
                StashPluginTask(name = name, description = task.optNullableString("description"))
            },
        )
    }.sortedBy { it.name.lowercase() }
}

internal fun parseGenerateDefaultsResponse(json: String): StashGenerateOptions {
    val generate = graphQlData(json).optJSONObject("configuration")
        ?.optJSONObject("defaults")
        ?.optJSONObject("generate")
        ?: return StashGenerateOptions()
    val fallback = StashGenerateOptions()
    return StashGenerateOptions(
        covers = generate.optBoolean("covers", fallback.covers),
        previews = generate.optBoolean("previews", fallback.previews),
        imagePreviews = generate.optBoolean("imagePreviews", fallback.imagePreviews),
        sprites = generate.optBoolean("sprites", fallback.sprites),
        phashes = generate.optBoolean("phashes", fallback.phashes),
        markers = generate.optBoolean("markers", fallback.markers),
        imageThumbnails = generate.optBoolean("imageThumbnails", fallback.imageThumbnails),
        clipPreviews = generate.optBoolean("clipPreviews", fallback.clipPreviews),
    )
}

internal fun buildGenerateInput(options: StashGenerateOptions): Map<String, Any?> = mapOf(
    "covers" to options.covers,
    "previews" to options.previews,
    "imagePreviews" to (options.previews && options.imagePreviews),
    "sprites" to options.sprites,
    "phashes" to options.phashes,
    "markers" to options.markers,
    "imageThumbnails" to options.imageThumbnails,
    "clipPreviews" to options.clipPreviews,
    "overwrite" to options.overwrite,
)

internal fun JSONObject.optNullableString(key: String): String? =
    if (isNull(key)) null else optString(key).trim().takeIf { it.isNotBlank() }
