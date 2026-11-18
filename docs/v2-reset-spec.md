# Schema v2 local reset spec (Android)

Status: scaffold (task M-02). Source of truth for what `LegacyStoreReset`
(`core/data/src/main/kotlin/com/avago/core/data/v2/LegacyStoreReset.kt`) clears and keeps on the
first v2-enabled launch. Implements plan §14.4 under binding decisions **L12** (fresh v2 stores,
new names and generation paths) and **L13** (local data is disposable and is **not migrated**;
the user stays signed in). `LegacyStoreReset.Plan` mirrors these lists, and
`LegacyStoreResetTest` pins them.

## When it runs

- Only when `V2Flag.isEnabled()` is true. The flag is **off by default**, and
  `V2Flag.BUILD_SUPPORTS_V2 = false` keeps release builds inert even if a server advertises
  `/v2/capabilities`. Until the v2 data layer ships, only a debuggable build with the developer
  override ("Developer → Schema v2 → On") can enable it.
- It runs synchronously in `AvagoApplication.onCreate()`, right after Hilt injection and before
  any legacy Room DB, SharedPreferences or DataStore file is opened in the process. DataStore
  forbids two live instances per file, so the reset cannot run safely later.

## Steps and marker states

The marker is `noBackupFilesDir/avago_v2_reset.marker`. It sits outside every account DB, is
excluded from backup, and is written atomically (temp file plus atomic rename).

| Marker | Meaning |
|---|---|
| absent | The reset never started. |
| `reset_started` | The reset is in progress or was interrupted. The next launch runs it again from step 1. |
| `reset_complete` | Done. `runIfNeeded()` is a no-op, and v2 stays enabled (sticky) even if a later probe says "legacy". |

Unreadable or unknown marker content is treated as `reset_started`.

1. Write `reset_started`.
2. Close open legacy DB handles (`DatabaseFactory.closeAll()`).
3. Delete the legacy per-account stores in every `files/accounts/<account_id>/`.
4. Delete local media and report files under `files/`.
5. Delete the SharedPreferences files listed below.
6. Delete the DataStore files listed below.
7. Remove the `chat_draft_*` keys from `user_prefs` (`UserPreferencesRepository.clearChatDrafts()`).
8. Delete the contents of `cacheDir`.
9. Write `reset_complete`.

Every step is idempotent: deleting a missing file is a no-op. If a step throws, the marker stays
`reset_started` and the error is logged. The app keeps starting, and the v2 store gate
(`V2StoreGate`: v2 enabled **and** marker complete) keeps every v2 DB closed until a later launch
finishes the reset. The app runs in one process, so this process-local gate is the whole gate.

## Cleared

| # | Item | Location | Notes |
|---|---|---|---|
| 1 | AvagoDatabase v20 | `files/accounts/<id>/avago.db` | Plus `-wal`, `-shm`, `-journal`. |
| 2 | ChatDatabase v8 | `files/accounts/<id>/chat.db` | Plus `-wal`, `-shm`, `-journal`. |
| 3 | RentalOpsDatabase v1 | `files/accounts/<id>/rentalops.db` | Plus `-wal`, `-shm`, `-journal`. |
| 4 | Rental-ops proof media | `files/accounts/<id>/rentalops_media/` | |
| 5 | Asset photos | `files/asset_photos/` | |
| 6 | Rental photos | `files/rental_photos/` | |
| 7 | Generated PDF reports | `files/pdf_reports/` | |
| 8 | Route ping queues | `files/rentalops_route_pings_*.json` | |
| 9 | Sync rate-limit state | `shared_prefs/sync_rate_limit.xml` | Plus `.xml.bak`, as for every cleared prefs file. |
| 10 | Cached dispatch mode | `shared_prefs/av_dispatch_mode.xml` | |
| 11 | AI recent entities | `shared_prefs/screen_context.xml` | |
| 12 | Rental onboarding drafts | `shared_prefs/rental_onboarding.xml` | |
| 13 | RBAC permission cache | `files/datastore/rbac_permissions.preferences_pb` | Re-fetched on the next sync. |
| 14 | Exchange-rate cache | `files/datastore/exchange_rates.preferences_pb` | |
| 15 | HTTP 429 backoff state | `files/datastore/rate_limit_backoff.preferences_pb` | |
| 16 | Inspection drafts | `files/datastore/inspection_drafts.preferences_pb` | |
| 17 | Driver route-tracking opt-in | `files/datastore/rentalops_route_tracking.preferences_pb` | |
| 18 | Rental-ops push prefs | `files/datastore/rentalops_push_prefs.preferences_pb` | |
| 19 | Chat composer drafts | `chat_draft_*` keys in `files/datastore/user_prefs.preferences_pb` | Only these keys are removed; the file is kept. |
| 20 | All caches | contents of `cacheDir` | Captures, statements, PDFs, exports and library caches. Disposable by contract. |

## Kept

| # | Item | Location | Why |
|---|---|---|---|
| 1 | Auth tokens: per-account access and refresh tokens, server device id, `av_device_id`, push token | `shared_prefs/avago_secure_prefs.xml` (EncryptedSharedPreferences) | L13: the user stays signed in. |
| 2 | Signed-in account list | `files/accounts_manifest.json` | Restores the last account on launch. `AccountMigrationService` prunes manifest accounts that have no tokens, so items 1 and 2 are kept together. |
| 3 | Marketplace renter session | `shared_prefs/avago_marketplace_session.xml` | Sign-in. |
| 4 | Device runtime flags (`force_offline`) | `shared_prefs/avago_runtime_flags.xml` | Device setting, not business data. |
| 5 | Route-tracking UI one-shot flag | `shared_prefs/rentalops_route_tracking_ui.xml` | Device UI state. |
| 6 | User settings (theme, language, units, currency, notifications, FRE state, and so on) | `files/datastore/user_prefs.preferences_pb` | Device settings. Only drafts are removed (cleared item 19). |
| 7 | v2 flag state | `shared_prefs/avago_v2_flags.xml` | Holds the debug override and the last server capability. |
| 8 | v2 reset marker | `no_backup/avago_v2_reset.marker` | Makes the reset idempotent. |
| 9 | v2 stores | `files/accounts/<account>/<user>/v2/` (`avago_v2.db`; `chat_v2.db` reserved) | Generation directory; never touched. |
| 10 | Third-party SDK state (Firebase, Google Play services, WorkManager's own DB) | various | Not app business data. |
| 11 | Unknown files | anywhere | The reset deletes only the enumerated items above. |

## Notes and deviations

- **Tokens kept, unlike plan §14.4 step 4.** The plan lists clearing `encrypted_prefs.xml` and
  `avago_marketplace_session.xml`. Binding L13 and task M-02 say to keep the sign-in, so both
  are kept. The real token file is `avago_secure_prefs.xml`; `backup_rules.xml` still refers to
  the stale name `encrypted_prefs.xml`.
- If the server reset invalidates a kept refresh token, the existing refresh-failure handling
  (`RefreshFailedHandler`) sends the user to sign-in. No v2-specific code is needed for this.
- Follow-ups, outside this scaffold:
  - Cancel legacy WorkManager jobs before the v2 sync exists.
  - Migrate the `RentalFleetWidget` from the legacy DB.
  - Switch to the bundled SQLite driver, which the contract's STRICT DDL requires.
  - Import the generated contract output (`schema/contract/out/sqlite/business.sql`,
    `out/kotlin/*.kt`) into `AvagoV2Database`. `V2Contract` documents this hook.
- An incompatible API version (`426`, or the client contract version is outside the range
  reported by `GET /v2/capabilities`) shows `ServiceUnavailableUpdateScreen` instead of the app
  (plan §11). This happens only while v2 is enabled.
