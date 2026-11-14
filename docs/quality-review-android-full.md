# Android quality review — full codebase pass

Scope: the whole app outside the earlier rentals/rental-ops review
(`docs/quality-review-android.md`). This pass covers app startup, Compose collection and
recomposition, Room, the Ktor/OkHttp client and token refresh, WorkManager sync, coroutines,
Coil, leaks, `!!` on runtime paths and R8. It also includes a localization audit of every
rental user-visible string in en-US and de-DE. Base: `origin/master` ab63057.

Severity: **High** = data loss, wrong sync state or crash on a normal path. **Medium** =
reliability, latency or usability defect with a workaround. **Low** = maintainability or
polish.

## Fixed

| # | Sev | Area | Finding (file:line at base) | Fix |
|---|-----|------|-----------------------------|-----|
| 1 | High | core/network | `AvagoHttpClient.kt` `refreshTokens {}` caught **every** exception from `POST /auth/refresh`, including IOException, timeouts, 5xx/429 and CancellationException. It then cleared the tokens and called `onRefreshFailed`, which leads to `IdentityManager.reAuthenticateSilently` and then `signOut`, and sign-out deletes the account database. So one 401 while offline or during a server blip could sign the user out and lose unsynced outbox rows. This applies to every API on the shared client, including rentalsops (the deferred "rentalsops 401 has no refresh" item: rentalsops uses the same client, so it does refresh; the real gap was this sign-out policy). | New `TokenRefresh` policy. The session ends only when the refresh endpoint rejects the refresh token (400/401/403). Every other failure is rethrown unchanged, so callers see their usual transport/server error ("offline, retry later") and keep the tokens. Cancellation always propagates. `TokenRefreshTest` covers this. |
| 2 | Medium | core/network | Each token refresh built a new unauthenticated `HttpClient`, with a new OkHttp dispatcher, connection pool and TLS handshake, then closed it. That added latency on the critical path for every request waiting behind a 401. | One lazily created refresh client per authenticated client, reused for all refreshes. |
| 3 | Medium | core/network | The rate-limit OkHttp interceptor did a `runBlocking` DataStore read on **every** request, which blocked an OkHttp dispatcher thread on disk I/O and a DataStore flow collection (`AvagoHttpClient.kt:112`). | `RateLimitBackoffStore` keeps an in-memory mirror. DataStore is read once and then lookups are non-blocking map reads. The persisted key format is unchanged, so 429 gates still survive a restart. `RateLimitBackoffStoreTest` covers this. |
| 4 | Medium | core/auth | `IdentityManager` launched role refresh, account prefetch, push-token registration and dev-role permission refresh on `GlobalScope` (`:262, :641, :744, :785`). `refreshPermissionStoreForActiveAccount` has no try/catch, so a failure there would crash the process. | A private `CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler)` replaces GlobalScope: failures are logged and isolated. |
| 5 | Medium | core/data | `FeatureFlags.observeFlag` called `runBlocking { databaseFactory.get() }` inside `flatMapLatest`, whose transform is already suspending. This blocked the collector's thread, often Main for UI collectors, while the account DB opened. Room also re-emits on any write to the `config` table, so every flag observer recomposed on unrelated config syncs. | Removed `runBlocking`; added `distinctUntilChanged()`. |
| 6 | Low | core/data | `DisplayNameResolver.resolveSync` was a `runBlocking` helper with no callers (checked with grep across all modules; it is not referenced from DI or reflection). `resolve` also swallowed `CancellationException`. | Removed `resolveSync`; `resolve` now rethrows cancellation. |
| 7 | Medium | app, features | 63 `collectAsState()` call sites in 17 files (MainActivity, MainScaffold, Settings, KPI dashboard, custom fields, permission sets, PM plans, Scout palette, location picker, all log screens, chat settings and nav, gallery) kept collecting while the app was in the background. That wasted CPU and battery, and DB/network-backed flows stayed hot. | Switched to `collectAsStateWithLifecycle()` (`initialValue =` where an initial value was given). |
| 8 | Medium | feature/reports | `CostReportScreen.kt:211` used `data!!` inside an "Expand all" `onClick`. The delegated state is read at click time, so a tap during a reload (state null) crashed. A second `data!!` at `:234` re-read state after the null check. | Null-safe click handler; the builder reads the state once into a local. |

## Localization — rental user-visible text (en-US / de-DE)

Audit method: a key-parity and placeholder check over every `strings.xml` used by rental
code, plus a scan for string literals in the rental Kotlin sources.

- **feature/rentals (331 keys)** and **feature/rentalsops (225 keys)**: full en/de parity
  with matching placeholders. The five values that are the same in both files (Name, Barcode,
  Text, Status, "Status: %1$s") are correct German. Notifications, snackbars, accessibility
  labels and status/enum labels (`readinessItemLabel`, `invoiceStatusText`) were already
  localized. Dates use `ofLocalized*` and money uses `MoneyFormatter`.
- **Legacy asset-scoped rental screens** (AssetRentals, CreateRentalSheet, RentalBooking,
  RentalInvoice, RentalCustomers*, reachable from `RentalsNavGraph`, strings in
  `feature/assets`) had the gaps. **61 gaps fixed:**
  - 13 referenced keys had no German: asset_cancel/save/detail_back, date_picker_ok and nine
    invoice/chip strings.
  - 35 hardcoded English texts, raw enum values or wrong labels became en+de resources
    (34 strings and 1 plural; the wire values are unchanged):
    - condition, meter-unit and rate-unit chips, which showed raw API values
    - the end/start sheet title, labels and hints
    - Internal, OVERDUE and the condition/meter summary lines
    - "/ daily" rate suffixes, "is required" and "Period"
    - the five payment methods
    - "N days", now `<plurals>`
    - the Unauthorized/Unknown error/No active account snackbars, which are now error codes
      that the screens resolve
    - the start-meter field, which was labelled "Final meter reading"
  - 4 date sites used the fixed pattern "MMM d, yyyy". They now use the medium localized date.
  - 4 money sites:
    - Two picked the locale from the currency (USD?en-US, EUR?de-DE), so a German user saw
      `$1,234.50`.
    - Any other currency fell back to the device locale's currency symbol, which was the wrong
      currency.
    - Invoice line items used `"%.2f"`.
    - All now use the user's locale with the rental's currency.
  - 1 meter-reading display used raw doubles and `"%.1f"`. It now uses the locale's number format.
  - 1 Gantt day header was hardcoded as "d/M". It now uses the locale skeleton: en-US `5/14`,
    de-DE `14.5.`.
  - 2 corrupted "?" arrows showed as "?".
  - 1 cp1252 byte in a source comment, fixed to UTF-8.
  - After the fix, every key referenced by rental code exists in both en and de, and every new
    key is used.
- `LegacyRentalTextTest` covers locale-aware money and date formatting, the label coverage for
  every offered wire value, and the error-code mapping.

## Deferred (not low-risk enough for this pass, or needs a device/release check)

| Sev | Area | Finding | Why deferred / next step |
|-----|------|---------|--------------------------|
| Medium | app | `MainActivity.kt:43-47` field-injects SyncEngine, SyncGate, IdentityManager and PermissionStore non-lazily, so their graphs (DB factory, HTTP client) are built on the main thread before the first frame. AvagoApplication already uses `dagger.Lazy`. | Converting these to `Lazy`/`Provider` changes init ordering with the splash gate. Measure with the macrobenchmark first. |
| Medium | app | Baseline profile is a hand-written 30-line `app/src/main/baseline-prof.txt`. The `macrobenchmark` module exists, but the `androidx.baselineprofile` plugin and a generator are not wired up. | Needs a device or emulator run to generate. This is the largest remaining cold-start win. |
| Medium | app | `proguard-rules.pro:16-49` keeps all of `io.ktor`, `okhttp3`, `androidx.navigation`, `androidx.work`, `com.google.firebase` and `dagger.hilt`. These libraries ship consumer rules, so the blanket keeps defeat shrinking and inflate the dex and startup class loading. The `coil.**` rule (`:41`) is stale: Coil 3 is `coil3`. | Needs a release build smoke test (serialization, Hilt workers, navigation args). |
| Medium | core/data | `FeatureFlags.kt:223` synchronous property getters still use `runBlocking` (public API used from composition and non-suspending code). | Needs an API change to `StateFlow`-backed flags across callers. |
| Medium | core/data | `DatabaseFactory.kt:52` `fallbackToDestructiveMigration(dropAllTables = true)`: a missing migration silently wipes local data, including unsynced rows. `instances` (`:33`) is touched outside the mutex in close/delete (`:57`, `:73`). | Policy decision (fail loudly instead?) plus a concurrency refactor. Migrations 4→20 currently exist. |
| Low | app | `MainActivity.kt:111-113` runs `syncEngine.sync()` on every RESUMED, on top of the Application foreground SyncWorker. `SyncEngine` coalesces with `mutex.tryLock`, so this is redundant work rather than a race. | Keep one trigger after checking the product expectation for "sync on resume". |
| Low | core/network | Rate-limit gates are keyed per path, including ids, so the DataStore keys grow without bound across ids. | Needs a route-template key or TTL pruning. |
| Low | core/permissions | `PermissionsCompose.kt:22,35` still uses `collectAsState`. The module has no `lifecycle-runtime-compose` dependency. | Add the dependency in a follow-up; the impact is small (an in-memory StateFlow). |
| Low | feature/chat | `ChatRealtimeClient.kt:75` builds its own `OkHttpClient` for the websocket instead of sharing the app pool and dispatcher. | Share it through DI with `newBuilder()`; needs a websocket regression check. |
| Low | various | About 53 guarded `!!` remain outside rentals: smart-cast workarounds on delegated state in log, work-order and report screens. None found on an unguarded runtime path other than #8. | Mechanical cleanup when those files are next touched. |
| Low | feature/assets | The new legacy rental keys exist in en and de only. The es/fr/it/nl/no/pt/sv asset files already lacked many keys before this pass (no `MissingTranslation` gate is configured). | Only en/de were in scope. |
| Low | feature/rentals | Invoice line-item `unit` and server error messages (`NetworkResult.Error.message`) are shown as sent by the API. | Server-side localization. |

Verified OK (no change needed): AvagoApplication (dagger.Lazy fields, SupervisorJob app
scope), single shared Ktor client and timeouts, Coil 3 default memory/disk caches, Room indexes
on work-order/log/photo/sync-queue filter columns, SyncWorker (unique work, CONNECTED
constraint, exponential backoff).

## Validation

- `gradlew --max-workers=2 :core:network:testDebugUnitTest :core:data:testDebugUnitTest
  :feature:rentals:testDebugUnitTest :app:assembleDebug`: passed (new: TokenRefreshTest 5,
  RateLimitBackoffStoreTest 2, LegacyRentalTextTest 8).
- `:core:ai`, `:feature:log` and `:app:testDebugUnitTest`: passed (24, 112 and 3 tests).
  core:auth, feature:reports/settings/chat/assets/schedule/inventory have no unit tests. They
  were compiled by `:app:assembleDebug`.
- Lint: `:core:network:lintDebug :feature:rentals:lintDebug` — pass (0 errors). The first run flagged
  2 `NonObservableLocale` errors in new code (`labelOrRaw`, schedule day header); both now read
  `LocalConfiguration.current.locales[0]`. Remaining rentals warnings (PluralsCandidate, widget
  `targetCell*` UnusedAttribute, ModifierParameter) are pre-existing and left as-is.
  `core:ui` lint (313 pre-existing errors) was not run, per scope.
