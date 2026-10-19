package gomeng.dev.stashplayer.core.network

/** Removes the newest O event; Stash returns the remaining count. */
internal val SCENE_DELETE_O_MUTATION = """
    mutation SceneDeleteO(${'$'}id: ID!) {
      sceneDeleteO(id: ${'$'}id) { count }
    }
"""

internal fun parseSceneDeleteOResponse(json: String): Int =
    parseGraphQlData(json).optJSONObject("sceneDeleteO")?.optInt("count", 0) ?: 0
