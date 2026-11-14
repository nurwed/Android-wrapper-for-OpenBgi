# Subahibi - Android wrapper for OpenBGI

Android Studio (Gradle) project that builds an Android app for running *Subarashiki Hibi* (English release) with the modified OpenBGI SDL2 engine.

- Engine (OpenBGI with an SDL back end and the Android port): [OpenBgi-Android](https://github.com/nurwed/openbgi-subahibi-android)


## You must own the game

No game data is included: no scripts, archives, audio or artwork. Copy the files from your own copy of the game to your phone.

## Building

Requirements: Android Studio with the NDK and CMake 3.22.1, a clone of the engine repo, the SDL2 2.32.8 source archive (the 2.x series, not SDL3) and the FreeType 2.15.0 source archive.

1. Copy `local.properties.example` to `local.properties` (it is gitignored).
2. Fill in:
   - `sdk.dir`: your Android SDK
   - `sdl2.dir`: the extracted SDL2 2.32.8 folder
   - `freetype.dir`: the extracted FreeType 2.15.0 folder
   - `engine.dir`: your engine clone, the folder that contains `android/CMakeLists.txt`
3. Open the project in Android Studio, sync, build.
4. Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

## First run

1. Put the game files in a folder on the phone.
2. Start the app and grant "All files access".
3. Pick the folder with the game's `.arc` files.
4. If `games.json` is missing, the app offers to create it. To make it by hand, use this content:

```json
[ { "match": "SubarashikihibiENG", "profile": "1.653", "product": "SubarashikihibiENG" } ]
```


## License and source

This project is licensed under the GNU General Public License v2.0 (see `LICENSE`). The corresponding source of the app is this repository plus the engine repository linked above.

Third-party components built into the app:
- SDL2: zlib license
- FreeType: FreeType License (FTL) or GPLv2
- pl_mpeg, stb_vorbis: see the engine repository's `THIRD_PARTY.md`
