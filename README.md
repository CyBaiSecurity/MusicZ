# MusicZ 🎵

MusicZ is an Android app that saves a YouTube link as an MP3 on your phone, then plays it.

The app id is `com.cybai.musicplayer`.

## What you can do 🎧

Paste a link in the field labeled "YouTube URL".

- If the phone is offline, the download does not start. The screen says "No internet connection".
- If that link is already saved, the app skips it.
- A new link is saved as an MP3 with youtubedl-android and FFmpeg `0.18.1`, in the app's music folder.
- Songs play with Media3 (ExoPlayer).
- Playlists are saved in the app database.
- Stats show your total listening time and the 10 songs you played most.

The screens are Download, Songs, Playlists, and Stats.

## What you need 🧰

Use the versions already set in the Gradle files:

- Kotlin and Java 17
- Android 8.0 or newer (API 26), compile SDK 34, target SDK 34
- App version 1.0
- Android Gradle Plugin 8.2.2
- Kotlin Gradle plugin 1.9.22
- Gradle 8.5

## How to build 🔨

From the `App/` folder, run:

```bash
./gradlew assembleDebug
```

A new copy of this repo cannot run that command yet. `App/gradle/wrapper/` has `gradle-wrapper.properties` and does not have `gradle-wrapper.jar`. Put the jar next to the properties file, then run the command.

## Files kept off GitHub 🔒

These stay on your computer:

- `local.properties` is your Android SDK path
- `.idea/` is Android Studio's project data
- `.gradle/` and `build/` are build output, including APK files
- `App/instructions.md` and `App/instructions_2.md` are private notes

## License 📄

MusicZ is under the MIT License. The full text is in [LICENSE](LICENSE).
