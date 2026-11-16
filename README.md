# Herhyme

A minimalist dark offline music player for Android, built with Kotlin, Jetpack Compose, and AndroidX Media3.

## Starter features
- Scans audio files indexed by Android MediaStore
- Requests local-audio permission
- Lists local tracks
- Plays selected tracks through a Media3 playback service

## Build a debug APK
A GitHub Actions workflow builds the APK whenever changes are pushed to `main`, or when manually started from the Actions tab.

Download `Herhyme-debug-apk` from the workflow run's **Artifacts** section. Install it on a physical Android device to test local music scanning and playback.

This is an initial starter implementation and should be tested on-device before release.
