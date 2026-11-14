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

## Recommended emulator (Windows)

Use **Android Studio Device Manager** to create a Pixel 8 Pro AVD:

| Setting | Value |
|---|---|
| Device | Pixel 8 Pro |
| System image | API 35 — AArch64 (Google Play) |
| RAM | 4096 MB |
| VM heap | 512 MB |
| Internal storage | 8192 MB |
| Graphics | Hardware — GLES 3.1 |

On Windows, enable **Hyper-V** or **Windows Hypervisor Platform (WHPX)** for best performance:

```powershell
# Run as Administrator — enable WHPX
dism /Online /Enable-Feature /FeatureName:HypervisorPlatform /All /NoRestart
```

Restart, then launch the AVD from Android Studio. The emulator maps `10.0.2.2` to `localhost` on the host machine for local backend access.

## Backend

Talks to AvagoSvc at `https://api.avagomate.com` (production) or `http://10.0.2.2:8080` (local dev).

Configure via `local.properties`:
```
avago.base.url=https://api.avagomate.com
```

## Offline sync & conflicts

Rental edits are queued in `sync_queue` and replayed by `RentalOutbox` with a stable idempotency
key. Offline attempts don't consume the retry budget (`QueueRetryPolicy`). When a versioned edit
(customer update, order transition) gets `409 conflict` from the server:

- If the fields changed locally and on the server don't overlap, the edit is rebased onto the
  server row and resent once.
- Otherwise it is parked as a conflict that names the clashing fields. **Retry** keeps the
  user's values and **Discard** keeps the server's.

The merge is the pure `ConflictMerge` (feature/rentals). Details are in
`docs/quality-review-android.md`.

## Project structure

Multi-module — `:app` is thin, business logic in `:core:*`, screens in `:feature:*`.

See `android.md` for the full build brief and phase-by-phase implementation plan.

## Releasing

`./gradlew :app:bundleRelease` → upload AAB to Play Console internal track.
