# Credits

Stashfeed stands on the work of other people. Thank you to everyone below.

## Stash

**[stashapp/stash](https://github.com/stashapp/stash)** by the [Stash team and contributors](https://github.com/stashapp/stash/graphs/contributors)
· [stashapp.cc](https://stashapp.cc) · [Documentation](https://docs.stashapp.cc) · AGPL-3.0

Stash is the self-hosted organiser that Stashfeed is a client for. Everything Stashfeed shows (scenes, performers, galleries,
images, tags, ratings, saved filters, previews and sprites) comes from a Stash server through its GraphQL API.
Without Stash there would be nothing to build an app for.

Stashfeed contains no Stash source code; it only talks to a server you run yourself.

## stash-player-android

**[gomeng-dev/stash-player-android](https://github.com/gomeng-dev/stash-player-android)** by [gomeng-dev](https://github.com/gomeng-dev) · MIT

Stashfeed is a fork of stash-player-android. Its foundations come from there: the Stash connection and sign-in, the
player with gestures, subtitles, stream selection and picture-in-picture, the queue and on-device lists, galleries and
images, saved filters, and the project setup itself. Stashfeed adds the feed, the For You reel, performer profiles and the
redesign on top of that base. The original copyright notice is kept in [LICENSE](LICENSE), as the MIT license
requires, and the project's full history is preserved in this repository.

gomeng-dev also wrote [stash-recommendation-server](https://github.com/gomeng-dev/stash-recommendation-server)
(Hybrid Recommendations), which the player uses for similar videos when it is installed on your server.

## Binge

**[ordureconnoisseur/binge](https://github.com/ordureconnoisseur/binge)** by [ordureconnoisseur](https://github.com/ordureconnoisseur) · AGPL-3.0

Binge is the Stash plugin that turns Stash into an Instagram-shaped social and discovery layer. Stashfeed's social side is
modelled on it: the Home feed with stories, posts and reposts, the vertical For You reel with its Random and Chained
modes, performer profiles with a feed, the gallery swipe and tap gestures, and the idea of keeping seen posts in the
Stash UI settings. If you like Stashfeed's feed, try Binge in your browser as well.

Stashfeed is a separate Kotlin implementation of these ideas for Android; it does not include Binge's code.

## Inter

**[Inter](https://rsms.me/inter/)** by [Rasmus Andersson](https://github.com/rsms) · SIL Open Font License 1.1

Stashfeed's typeface. The font is bundled unchanged; its license is in
[app/src/main/assets/licenses/inter-OFL.txt](app/src/main/assets/licenses/inter-OFL.txt).

## Libraries

Built with [Jetpack Compose](https://developer.android.com/compose) and Material 3,
[Media3 / ExoPlayer](https://github.com/androidx/media), [Coil](https://github.com/coil-kt/coil),
[Room](https://developer.android.com/jetpack/androidx/releases/room), [OkHttp](https://github.com/square/okhttp) and
[Moshi](https://github.com/square/moshi), each under its own license (mostly Apache 2.0).
