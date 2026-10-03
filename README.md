This page tells you what MusicZ does, which versions the project expects, how you build a debug APK, and which files stay out of git. Read the build section before you run the wrapper.

## What it does

MusicZ is an Android app with application id `com.cybai.musicplayer`. You type a URL in a field labeled "YouTube URL". If `ConnectivityManager` reports no internet, the download does not start and the screen shows "No internet connection". Otherwise the app enqueues `DownloadWorker`.

`DownloadWorker` extracts MP3 audio with `youtubedl-android` and `FFmpeg` (both `0.18.1`) into app external-files music storage. The app skips a duplicate source URL before enqueue. Songs play through `Media3` (`MusicController` binds to `PlaybackService`, which uses `ExoPlayer`). Playlists are `Room` rows.

Stats show total listening time and the top 10 songs by play count. Navigation includes these screens:

- Download
- Songs
- Playlists
- Stats

## Requirements

You build with the toolchain this repository already pins. The Gradle files declare these values:

- Kotlin
- `minSdk` `26`
- `compileSdk` `34`
- `targetSdk` `34`
- `versionName` `1.0`
- Java `17` source and target
- Kotlin `jvmTarget` `17`
- Android Gradle Plugin `8.2.2`
- Kotlin Gradle plugin `1.9.22`
- Gradle `8.5` wrapper distribution

## Build

You assemble a debug build from `App/`. `App/gradle/wrapper/` contains `gradle-wrapper.properties` and does not contain `gradle-wrapper.jar`, so `./gradlew` cannot start until that jar is beside the properties file:

- Run `./gradlew assembleDebug` from `App/`.
- `App/gradle/wrapper/` contains `gradle-wrapper.properties`.
- `App/gradle/wrapper/` does not contain `gradle-wrapper.jar`.

## Left out of git

You keep these paths out of git. The set covers the SDK file, the IDE directory, Gradle and build outputs, and the two private briefs:

- `local.properties`, the SDK file
- `.idea/`, the IDE directory
- `.gradle/` and `build/`, plus apk outputs (`*.apk`)
- `App/instructions.md` and `App/instructions_2.md`, the private briefs

## License

No license is included yet, so the code is not yet under an open-source grant.
