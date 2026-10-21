# Development

This document is for people building Stash Android Player from source.

## Build From Source

Install JDK 21 and the Android SDK (compile SDK 36), then run:

```bash
./gradlew --no-daemon :app:assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/
```

## Signed Release Build

To build a signed release APK, provide these environment variables:

```bash
export ANDROID_SIGNING_KEYSTORE_BASE64="<base64 encoded keystore>"
export ANDROID_SIGNING_STORE_PASSWORD="<store password>"
export ANDROID_SIGNING_KEY_ALIAS="<key alias>"
export ANDROID_SIGNING_KEY_PASSWORD="<key password>"
./gradlew --no-daemon :app:assembleRelease
```

Locally you can instead keep the signing values in `~/.stashfeed/keystore.properties` (or point
`STASHFEED_KEYSTORE_PROPERTIES` at another file):

```properties
android.signing.storeFile=/home/you/.stashfeed/stashfeed-release.jks
android.signing.storePassword=...
android.signing.keyAlias=stashfeed
android.signing.keyPassword=...
```

Without signing values, release builds fall back to the debug key and cannot update an official Stashfeed install.

Do not commit keystores, signing credentials, APKs, AABs, or build outputs.

## Tech Stack

- Kotlin
- Jetpack Compose and Material 3
- AndroidX Navigation
- Media3/ExoPlayer
- Coil
- Room
- DataStore
- Retrofit, OkHttp, and Moshi

## Validation

Useful local checks:

```bash
./gradlew --no-daemon :app:testDebugUnitTest
./gradlew --no-daemon :app:assembleDebug
./gradlew --no-daemon :app:lintDebug
git diff --check
```
