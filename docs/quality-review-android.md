# Android quality & usability review — rentals, rental ops, sync, defects

Scope: everything touched on `master` over the last ~10 days in feature/rentals (P1 +
readiness checklist + line swap), feature/rentalsops (P2: dispatch, deliveries, transfers,
billing, statements, utilization, widget, push), core/sync (SyncEngine, outbox, DefectOutbox,
DefectRegisterSync, rental sync), open defects, vehicle/tire/brake sketches, charts and
logs/work orders. Base: `origin/master` 26a40a1. Spec: AvagoWeb `docs/rental-plan.md` and
`rental-plan-detailed.md` (§11.1 fleet visibility, §15 offline-first, §16 localization).

Severity: **High** = data loss, wrong sync state or crash on a normal path. **Medium** =
reliability, latency or usability defect with a workaround. **Low** = maintainability or
polish.

## Fixed

| # | Sev | Area | Finding | Fix |
|---|-----|------|---------|-----|
| 1 | High | core/sync, rentals, rentalsops, defects | Every outbox (SyncEngine defects, DefectRegisterSync, RentalOutbox, RentalOpsOutbox) counted "no response" (IOException / code ≤ 0 / transport offline) as a failed attempt. A device that was offline for a few drains marked queued work `failed` after MAX_ATTEMPTS. This is the same bug iOS had. | `QueueRetryPolicy` in core/data is now the single policy (MAX_ATTEMPTS, transient HTTP, no-response). `SyncQueueDao.markOfflineRetry` keeps `attempts`. `Disposition.OFFLINE` / `DrainOutcome.Offline` stop the drain without consuming an attempt. Tests cover each outbox. f7460b9 |
| 2 | High | core/sync | Cancelling SyncEngine mid-push left rows `in_flight`, so they were never retried. | In-flight rows are reset under `NonCancellable`, and the cancellation is rethrown. f7460b9 |
| 3 | High | rentals | Optimistic record writes and their `sync_queue` rows were separate Room writes. A crash between them left a local change with no queued action, or the reverse. | `RentalRepository.write{}` runs both in one transaction and wakes the worker once after the commit. 5c52b88 |
| 4 | Medium | rentals, sync | `!!` in production paths (RentalRepository, OutboxPlanner payloads, SyncEngine defect payload/version, DefectRegisterSync, HandoverScreen, SwapScreen). | Replaced with explicit errors (`MalformedItem`, `requireNotNull` with a message) or `mapNotNull`. f7460b9, 5c52b88, 3cb65aa |
| 5 | Medium | rentals | Pulling rentals did one `dao.get` per row (N+1) to protect unsynced local rows. | The unsynced ids are read once per page (`RentalDao.unsyncedIds`). 5c52b88 |
| 6 | Medium | rentals | RentalSyncWorker ran its own weekly full resync on top of the one `RentalSync.pull` already does, which doubled the work. Widget refresh errors also swallowed cancellation. | Removed the duplicate resync and rethrow cancellation. "Last synced" ignores `_meta` rows. 5c52b88 |
| 7 | Medium | rentals | Fleet snapshot, agenda, notices and per-asset status were recomputed on the main thread for every rental row. Notices also re-parsed every queue payload per row on each recomposition (`queueFor`). | `flowOn(Dispatchers.Default)` is used throughout. Notices pair each record with its queue row once per change (`RentalNotice`). 176720a |
| 8 | Medium | rentals | The scan screen cast `LocalContext` to `Activity`. Under a `ContextWrapper` the cast silently disabled NFC (lint error ContextCastToActivity). | Uses `LocalActivity`. 176720a |
| 9 | Medium | rentals UX | Not-ready reason codes (`open_defect`, `pm_overdue`, …) showed raw on fleet cards, and invoice status showed as `partially paid` and similar. | Localized labels (`readinessReasons`, `invoiceStatusText`). The asset rental screen now shows the reasons too. 176720a |
| 10 | Medium | rentals | Legacy screens hardcoded "Customers", "Book" and the void-invoice confirmation in English. | Localized. 176720a |
| 11 | Low | rentals | The asset "make rentable" form lost its input on process death. | `rememberSaveable`. 176720a |
| 12 | Low | rentalsops | The RentalOpsDatabase header still said "TODO(P1 reconcile)", although fleet and allocations were already read from P1 `rental_records` / `LocalFleet`. | Header corrected. f7460b9 |
| 13 | Medium | rentals ↔ rentalsops | P1/P2 duplication: P1 order screens localized invoice statuses, but the P2 customer statement appended the raw API value (`· partially_paid`). | The label mapping and its strings in all 9 locales moved to core:ui (`invoiceStatusText`), and both modules now use it. 04d2bae |
| 14 | High | rentals | A queued customer edit or order transition that hit a 409 version `conflict` was always parked. Retry then resent the same stale version, so it could never succeed, and the server's other changes were not applied locally. | Hybrid policy (shared with iOS; see the section below): disjoint edits rebase and resend once, overlapping edits are parked naming the clashing fields, and Retry keeps the user's values on the latest version. Pure `ConflictMerge`, 11 tests. 35a6974 |

### Rental UX / navigation (176720a)

Before this change, Rentals was only reachable from the side drawer. Its home was a grid of
six equal outlined buttons above a single combined "Today" list. Rental ops appeared as a
separate drawer item, and drivers landed on the counter home.

Now:

- **Rentals is a bottom-nav tab** (CarRental) on accounts with rentals, between Work orders
  and Chat. It is selected for all `rentals/*` and `rentalops/*` screens. The drawer entry
  remains. Drivers without counter permissions (`rental.drive` without
  `rental.statements`) land on **My deliveries** from both the tab and the drawer.
- **The home is task-first:**
  1. Sync status ("last synced", pending count, Sync now) plus an offline explanation when
     disconnected.
  2. Three large labelled actions: **New booking**, **Scan & check out** and **Check in**.
  3. A fleet summary with a prominent **"N available now"** that opens the board filtered to
     Available, followed by the status chips.
  4. Notices (conflicts and drafts) with Retry and Discard.
  5. **Today**: *Overdue*, *Going out today* and *Due back today*, each with a count and
     empty-state guidance. Every pickup or return row has a direct Check out / Check in
     button. *Maintenance* appears only when there is some.
  6. **Fleet & availability**: Fleet board (with status filter including Available),
     Schedule (upcoming bookings timeline) and **Find free units** (opens the date-range
     availability sheet directly).
  7. The permission-gated **My deliveries** and **Operations** entries. Operations covers
     dispatch, transfers, billing, statements and utilization in one place.
  8. Bookings & customers.
- **Scan → handover:** scanning a unit in check-out or check-in mode resolves its pending
  handover from the offline fleet (`HandoverTarget`) and offers it as the primary button. If
  the unit has no pending handover, the screen explains that. Fleet cards and the asset
  rental screen offer the same next step.
- The home keeps the shared app bar, so the menu stays one tap away as on the other tabs,
  and window insets are no longer applied twice.
- Every new string exists in en, de, es, fr, it, nl, no, pt and sv as UTF-8. A scan found
  no `?` corruption and no missing keys in feature/rentals or feature/rentalsops.

## Hybrid 409 version-conflict policy (rental edits)

Scope: the queued edits that carry a version are customer updates (`PATCH ?/customers/{id}`,
`server_version`) and order transitions (`POST ?/orders/{id}/{action}`, `version`). The server
answers a stale version with `409 {error:"conflict", message, current}`. Its idempotency
middleware caches only 2xx responses, so a resend with the same key is safe.
Readiness decisions keep their own "already applied" 409 handling.

- **Enqueue.** `RentalRepository` stores the synced row the edit was made on (`merge_base`) and
  the field values the edit sets (`merge_changes`) in the outbox payload. Locally created
  records have no base.
- **On 409** (`RentalOutbox.sendRebasing` ? `ConflictMerge.resolve`, which is pure):
  - Local changed fields are the changes that differ from the base. Server changed fields are
    the fields where `current` differs from the base. Bookkeeping fields (version,
    `updated_at`, ?) are ignored.
  - Values compare as whole JSON values, so a nested object or array is one field. Timestamps
    compare as instants. Numbers compare by value, but two strings are compared as text.
  - **Disjoint:** the request is rebased. It keeps only the locally changed fields, so stale
    form values never overwrite server edits, and takes `version` from `current`. It is resent
    once with the same idempotency key, and `merge_rebased=true` is persisted before the resend.
  - **Overlap, no base, no usable `current`, or a 409 after a rebase:** the item is parked as a
    conflict with the notice `field_conflict:<fields>`. The UI shows it as "Changed here and by
    someone else: Email, Phone?" in all 9 locales. The payload is rewritten to the user's
    edits on top of `current` (new base and version), so **Retry** keeps the user's values and
    **Discard** keeps the server's.
  - Either way, the server's non-clashing changes are applied to the cached record.
- **Older items** queued before this change have no base, so they are parked as before. Their
  Retry now sends `current`'s version instead of the stale one.
- iOS should mirror this policy: the same payload keys (`merge_base`, `merge_changes`,
  `merge_rebased`), the same notice prefix, and the same cases (disjoint ? one resend;
  overlap, missing base or repeated 409 ? conflict).

## Verified, no change needed

- **Idempotency:** the rentals outbox stores `client_uuid` / `Idempotency-Key` in the queued
  payload, and rental ops use the queue id as the key. Defect mutations persist
  `mutation_id`. Retries therefore replay the same key. `409 duplicate_client_uuid` →
  retry.
- **Conflicts:** DefectRegisterSync applies `server_row` and `deleted: true` tombstones.
  The rentals pull deletes tombstoned rows and never overwrites unsynced local rows.
  Rental ops re-pull `my-tasks` to receive reassignment tombstones.
- **WorkManager:** the rentals and rental-ops workers use `NetworkType.CONNECTED` with
  exponential backoff starting at 30 s, as unique work.
- **Scopes and cancellation:** no `GlobalScope` and no `runBlocking` in the reviewed rental,
  sync or defect code. ViewModels use `viewModelScope`, and Flows are collected with
  `collectAsStateWithLifecycle`.
- **P1/P2 duplication:** rental ops read fleet and allocations through P1 `LocalFleet` /
  `rental_records`, and money uses the shared `MoneyFormatter`. No second copy of the fleet
  or allocation model remains.

## Deferred (documented, not changed)

| Sev | Area | Item | Why deferred |
|-----|------|------|--------------|
| Medium | core/sync | DefectRegisterSync "NotYetRegistered" (404 before the register exists) still consumes an attempt. | It needs a server contract decision on how long to wait for the register to appear. The offline case is fixed. |
| Medium | rentalsops | A 401 inside `DrainOutcome` is a plain failure, with no token refresh and retry. | Auth refresh belongs to core/network's interceptor. Changing it affects every module. |
| Low | core/sync | `ConnectivityMonitor` derives online/offline from the last `onAvailable`/`onLost`. It can show offline while another network is still up. | This only affects the offline hint text. Queue behavior already uses response-based offline detection (#1). |
| Low | core/data | No index on `sync_queue(entity_type, sync_status)`. | The table stays small (it is drained continuously). A migration and version bump isn't justified yet. |
| Low | UI | MinTouch, EmptyState and the top bars are duplicated between rentals (`RentalsChrome` / `RentalsCommon`) and rentalsops (`ui/Common.kt`). | Moving them to core:ui touches every screen in both modules. It's a mechanical follow-up with no behavior change. |
| Low | rentalsops | Lint: `android.media.ExifInterface` instead of the androidx version, the KTX `toUri` / `createBitmap`, an obsolete SDK_INT check. | These are warnings only. |
| Low | rentals | Lint suggests plurals for count strings ("%d units" and similar). | These need plural forms in all 9 locales. |
| Low | log, work orders, reports | `!!` after explicit null checks (LogPacks, LogAnalytics, InspectionFormRenderer, CostReportScreen, etc.). `FeatureFlags` uses `runBlocking` for its synchronous property API. | They are guarded, so not a crash path today. The `FeatureFlags` change is an API change across the app. |
| Low | app-wide | Missing translations in non-rental modules (app, core/ai, core/ui, assets, auth, chat, docs, inventory, log, reports, schedule, settings, workorders). | These gaps predate this work and are outside the rentals scope. |

## Validation

- `gradlew --max-workers=2 :app:assembleDebug :core:data:testDebugUnitTest
  :core:sync:testDebugUnitTest :feature:rentals:testDebugUnitTest
  :feature:rentalsops:testDebugUnitTest`: BUILD SUCCESSFUL.
  - core/data: 20 tests, 0 failures.
  - core/sync: 307 tests, 0 failures.
  - feature/rentals: 100 tests, 0 failures (89, plus 11 `ConflictMergeTest` for the hybrid 409 policy).
  - feature/rentalsops: 53 tests, 0 failures.
- `:feature:rentals:lintDebug :feature:rentalsops:lintDebug`: 0 errors (13 and 12 warnings,
  listed above). The one error found was the NFC cast (#8), which is now fixed.
- `:core:ui:lintDebug` (touched by #13) fails on its baseline: 312 `MissingTranslation`
  errors in existing `strings_log_categories.xml` / `strings.xml` keys and 1 in
  `GlobalCategoryPickerScreen.kt`. None involve the moved invoice-status keys, which exist in
  all 9 locales. This is part of the app-wide translation gap deferred above.
- No device or UI test run. The navigation changes were verified by compilation and route
  review only.
