# Avago Android

Android client for Avago — fleet, equipment, and property maintenance.

## Prerequisites

| Tool | Version |
|---|---|
| JDK | 21 (LTS) — Temurin or Microsoft Build of OpenJDK |
| Android Studio | Ladybug 2024.2.1+ |
| Android SDK | API 35 (Android 15) |
| Gradle wrapper | 8.10 (auto-installed by repo) |
| Kotlin | 2.1.0 (managed by Gradle) |

## First-time setup

1. Install JDK 21 and Android Studio. Install Android 15 SDK + Pixel 8 emulator image.
2. Clone the repo:
   ```powershell
   git clone https://github.com/mlongton09/avagoAndroid.git C:\avagoAndroid
   cd C:\avagoAndroid
   ```
3. Place `google-services.json` at `app/google-services.json` (from Firebase console → Project Settings → Android app `com.avago`).
4. Open the project in Android Studio and wait for Gradle sync.

## Build & run

```powershell
# Debug APK on a connected emulator/device
./gradlew :app:installDebug

# All unit tests
./gradlew testDebugUnitTest

# Lint + detekt
./gradlew detekt lintDebug

# Release AAB (requires upload keystore env vars)
./gradlew :app:bundleRelease
```

## Backend

Talks to AvagoSvc at `https://api.avagomate.com` (production) or `http://10.0.2.2:8080` (local dev).

Configure via `local.properties`:
```
avago.base.url=https://api.avagomate.com
```

## Project structure

Multi-module — `:app` is thin, business logic in `:core:*`, screens in `:feature:*`.

See `android.md` for the full build brief and phase-by-phase implementation plan.

## Releasing

`./gradlew :app:bundleRelease` → upload AAB to Play Console internal track.
