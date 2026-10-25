# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Tilawa (تلاوة): a single-module Android app (Kotlin + Jetpack Compose, Material3, Media3/ExoPlayer) that shows the full Quran in Uthmani script, plays reciters ayah by ayah with live highlighting, and checks the user's own recitation. The UI is fully Arabic and RTL. Code comments and the README are in Arabic, so keep new comments in Arabic to match.

## Commands

```bash
./gradlew assembleDebug        # → app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # signed only if TILAWA_KEYSTORE_PATH / _PASSWORD, TILAWA_KEY_ALIAS, TILAWA_KEY_PASSWORD are set
./gradlew testDebugUnitTest    # JVM unit tests (JUnit 4)
./gradlew testDebugUnitTest --tests "app.quran.tilawa.RecitationTest"   # single class (append .methodName for one test)
```

The build uses JDK 17, compileSdk/targetSdk 35 and minSdk 26. CI ([.github/workflows/android.yml](.github/workflows/android.yml)) builds the debug APK on every push to main and every PR, and builds a signed release APK when the `KEYSTORE_B64` secret exists. The project has no lint or format configuration.

## Architecture

All source lives in one package, `app.quran.tilawa` ([app/src/main/java/app/quran/tilawa/](app/src/main/java/app/quran/tilawa/)):

- **QuranData.kt**: static data: surah names and ayah counts, the `Reciters.all` list, and the `Arabic` text helpers (diacritic stripping/normalization, Arabic-Indic digits, basmala splitting). To add a reciter, add one line to `Reciters.all` with an **ayah-by-ayah** audio edition id from `api.alquran.cloud/v1/edition?format=audio`.
- **QuranRepository.kt**: fetches the text and audio URLs from `api.alquran.cloud` and caches them locally for offline use. `Prefs` (in the same file) holds the settings.
- **QuranViewModel.kt**: the single app state holder. It owns playback logic and audio/text sync.
- **PlaybackService.kt**: a Media3 `MediaSessionService` for background playback with a ~1 GB audio cache.
- **Screens.kt / MushafScreen.kt**: the Compose UI. MushafScreen lays out printed-mushaf pages (it builds line layouts and fits the font size to the screen width).
- **Recitation.kt + Review.kt**: the recitation-review feature. `Recitation` compares speech-recognizer text to the Uthmani ayah word by word. It normalizes the words first and tolerates spelling differences between Uthmani and modern script (for example, dropped alifs). `ReviewSession` and the review UI track missed words for each ayah.

**How sync works (key design):** audio is one file per ayah, so there are no timing tables. Playing a surah builds an ExoPlayer playlist `basmala → 1 → 2 → …`, and each `MediaItem` id is `"surah:ayah"`. `onMediaItemTransition` tells the ViewModel exactly which ayah is playing, so it can highlight that ayah and scroll to it. Changing reciters mid-play rebuilds the playlist at the current ayah.

The mushaf text comes from Tanzil via AlQuran Cloud. The fonts are in `res/font` (KFGQPC, Amiri Quran under OFL, plus a Thuluth font for surah names).



## Testing requirements
Before mark it task as complete :
1. Write unit test for the new functionality.
2. Run the full test suite.
3. if it test fail:
  - analyze the failure output
  - fix the code not the test unless the tests are incorrect