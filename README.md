<div align="center">

<img src="docs/assets/stashfeed-logo.svg" alt="Stashfeed logo" width="96" height="96">

# Stashfeed

### A premium Android app for your Stash server.

A social-style feed, a For You reel, performer profiles and a smooth player, all running against the
[Stash](https://stashapp.cc) server you already host. Native Kotlin and Jetpack Compose, one dark design, no cloud.

<p>
  <img alt="Android 10+" src="https://img.shields.io/badge/Android-10%2B-34d399?style=for-the-badge&logo=android&logoColor=white">
  <img alt="Kotlin + Jetpack Compose" src="https://img.shields.io/badge/Kotlin%20%2B%20Compose-B26BFF?style=for-the-badge&logo=kotlin&logoColor=white">
  <img alt="MIT license" src="https://img.shields.io/badge/license-MIT-6AA9FF?style=for-the-badge">
</p>

<img src="docs/assets/readme/hero.png" alt="Stashfeed: Home feed, Explore, the player with seek preview, and a performer profile" width="100%">

<sub>Previews use placeholder artwork. Stashfeed ships no media; everything you see comes from your own server.</sub>

</div>

> **Built on the work of others.** Stashfeed is a client for [**Stash**](https://github.com/stashapp/stash) by the
> Stash team, a fork of [**stash-player-android**](https://github.com/gomeng-dev/stash-player-android) by
> [gomeng-dev](https://github.com/gomeng-dev), and its feed and reel follow the
> [**Binge**](https://github.com/ordureconnoisseur/binge) plugin by
> [ordureconnoisseur](https://github.com/ordureconnoisseur). See [Credits](#credits) for who made what.

---

## What it does

Stash is a great server for organising a private media library. Stashfeed is the phone side of it: quick to
browse with one hand, built around your library's performers, and pleasant to just scroll through.

### Home: a feed of what's new

- **Stories** for every performer with new scenes, like on Instagram.
- **One post per video**, shown in its own shape: portrait stays portrait, landscape stays landscape.
- **Videos play right in the feed**, muted by default. Turn the sound on once and it stays on for the session.
  Tap a video to pause it; **Open** takes you to the full player.
- **Gallery posts** you swipe through photo by photo, with a fullscreen viewer.
- **Seen posts** move below a "You're all caught up" line and sync through your Stash server, so every device
  knows what you've already seen.

### For You: a full-screen reel

Vertical swiping through your library, **Random** or **Chained** (each next scene shares tags or performers with
the one before), with rating, O counter and favorites right on the video.

### Explore: videos, photos and galleries

- Search plus **one fullscreen filter**: sort, tags, performers and studios, date and duration, rating and quality.
  Every section shows the live result count before you apply it.
- **Your saved filters from the Stash web UI**, including its default filter.
- **Compact or Large layout**. Large plays the centred card's preview on its own; in Compact, touch and hold a card.
- The header folds into a slim search bar as you scroll, and comes back when you scroll up.

### Performers

Following and All, sorting, and a profile per performer: a hero header, a feed of their new scenes and galleries,
and their scenes and photos in Compact or Large grids. Follow and unfollow writes straight back to Stash.

### The player

- **Mini player**: swipe down or press back, and the video keeps playing above the tab bar while you browse.
- **Seek previews** from Stash's sprite images while you drag; the video jumps once, exactly where you let go.
- Double-tap ±10 s, gestures for brightness and volume, speed hold, subtitles, picture-in-picture and background
  playback.
- Rating, O counter, favorites, watch later and queue on the watch page.

### Design

One dark design built for media: Inter typography, layered near-black surfaces, shared-element transitions from
thumbnails and performer photos, predictive back, spring motion and loading placeholders instead of spinners.
Pick one of five accent colours or let Android take it from your wallpaper.

<img src="docs/assets/readme/screens.png" alt="Performers, Explore in the large layout, the filter screen, and the You page" width="100%">

## Requirements

- Android 10 or newer.
- A Stash server your phone can reach: on your LAN, over a VPN or Tailscale, or behind your own HTTPS reverse
  proxy. A recent Stash version (0.27 or newer) is recommended; saved and default filters come from its UI settings.
- One way to sign in: no authentication on a trusted local server, a Stash **API key**, or your Stash **username
  and password**.
- Optional: generated **previews** and **sprites** in Stash (Tasks → Generate) make card previews and seek previews
  nicer. Without them, Stashfeed falls back to a short muted loop of the video itself and to a time-only seek bubble.

## Install

Download the latest APK from the [Releases](../../releases/latest) page on your phone, open it, and allow installs
from your browser or file manager if Android asks. Updates install over it, and Stashfeed tells you when a new version
is out.

To build it yourself:

```bash
./gradlew :app:assembleRelease
```

You need JDK 21 and the Android SDK (compile SDK 36). More in [DEVELOPMENT.md](DEVELOPMENT.md).

## First connection

On first launch, enter your server address (for example `http://192.168.1.20:9999` or your own HTTPS address),
choose how to sign in and tap **Test connection**. Once it works, Stashfeed opens Home.

## Privacy

Stashfeed talks only to your Stash server. There is no account, no analytics and no third-party service.

- Server settings, API keys, session cookies and on-device lists stay on the phone.
- The list of seen feed posts is stored in your Stash server's UI settings, under the `stashfeed` key.
- An option hides the app preview in Android's recent apps, and an optional app lock uses your device's
  fingerprint or screen lock.

## Roadmap

Possible next steps are collected in the pinned [Roadmap](../../issues/6) issue: Binge-compatible collections (📁)
and advanced ratings (★), StashDB discover, more languages, and editing tags from the app. Ideas and questions are
welcome in [Discussions](../../discussions), bugs in [Issues](../../issues).

## Credits

| Project | By | What Stashfeed owes it | License |
| --- | --- | --- | --- |
| [**Stash**](https://github.com/stashapp/stash) · [stashapp.cc](https://stashapp.cc) | [Stash team and contributors](https://github.com/stashapp/stash/graphs/contributors) | The server Stashfeed is a client for: every scene, performer, gallery, tag, rating, preview and saved filter comes from it. | AGPL-3.0 |
| [**stash-player-android**](https://github.com/gomeng-dev/stash-player-android) | [gomeng-dev](https://github.com/gomeng-dev) | The app Stashfeed is forked from: Stash connection and sign-in, the player, queue and lists, galleries and images, saved filters and the project itself. Its history is kept in this repo. | MIT |
| [**Binge**](https://github.com/ordureconnoisseur/binge) | [ordureconnoisseur](https://github.com/ordureconnoisseur) | The model for Stashfeed's social side: Home feed with stories and reposts, the For You reel with Random and Chained modes, performer profile feeds, gallery gestures and seen posts. | AGPL-3.0 |
| [**Stash Hybrid Recommendations**](https://github.com/gomeng-dev/stash-recommendation-server) | [gomeng-dev](https://github.com/gomeng-dev) | Similar-video recommendations in the player, when installed on your server. | – |
| [**Inter**](https://rsms.me/inter/) | [Rasmus Andersson](https://github.com/rsms) | The typeface. | OFL-1.1 |

Thank you to all of them. More detail, including the libraries Stashfeed is built with, is in [CREDITS.md](CREDITS.md).

Stashfeed is an independent project and is not affiliated with Stash or its developers. It contains no media of its
own; what it shows depends entirely on your library. If your library contains adult content, keep the app and its
screenshots away from minors and out of public places.

## License

MIT. See [LICENSE](LICENSE). The original stash-player-android copyright notice is kept, as the license requires.
