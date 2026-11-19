# Android schema-v2 status (P3-12)

This doc covers what the Android v2 local store, sync engine and command outbox do today, which
reads use v2, and what is left. The iOS equivalent is `avago/docs/v2-ios-status.md` (P3-11).

- Enablement and reset: `docs/v2-reset-spec.md`.
- Contract: the AvagoSvc files `schema/contract/design/sync-wire.md`,
  `out/openapi/sync.yaml` and `out/sqlite/*.sql`.
- The DDL is embedded at revision `89df356`; see `V2GeneratedSchema.SOURCE_REVISION`.

Everything below runs only while `V2Flag.isEnabled()` is true **and** the legacy reset marker is
complete (`V2StoreGate`). `V2Flag.BUILD_SUPPORTS_V2` stays `false`, so v2 is enabled only by the
debug override (Settings → debug v2 toggle, M-02) until cutover.

## 1. Done

All code is in `core/data/src/main/kotlin/com/avago/core/data/v2/` unless a path is given. Tests
are in `core/data/src/test/kotlin/com/avago/core/data/v2/` and use a mock transport and xerial
sqlite-jdbc. Run them with:

```
gradlew :core:data:testDebugUnitTest --tests "com.avago.core.data.v2.*"
```

| Piece | Where | Tests |
|---|---|---|
| Generator for the contract (DDL + projection manifest + sha256s); regenerate with `python scripts/gen_v2_contract.py --svc C:\v2\AvagoSvc --ref origin/schema-v2` | `scripts/gen_v2_contract.py`, `src/main/resources/com/avago/core/data/v2/{business,chat}.sql` (LF, `-text` in `.gitattributes`), `V2GeneratedSchema.kt` | DDL sha check on open |
| Store: bundled SQLite (`androidx.sqlite:sqlite-bundled`; STRICT tables need ≥ 3.37), contract DDL as-is, `database_identity` check, rebuild on DDL/user/account mismatch (L12), WAL, single writer, table-level change notifications for `Flow`s | `V2Connection.kt`, `V2Database.kt`, `V2StoreFactory.kt`, `V2StorePaths.kt` | `V2StoreResetTest` |
| Wire DTOs, `V2Api` (contract + device headers, 426 → `UpgradeRequired`, error envelope, `Retry-After`), canonical JSON, request hash, UUIDv7 | `V2Wire.kt`, `V2Json.kt` | engine/outbox tests |
| Body → row mapping from the generated table metadata (micros, decimals, booleans, JSON columns, tombstones) | `V2ProjectionApplier.kt`, `V2Projection.kt` | engine tests |
| Sync engine (below) | `V2SyncEngine.kt` | `V2SyncEngineTest` (13) |
| Command outbox (below) | `V2CommandOutbox.kt` | `V2CommandOutboxTest` (12) |
| Reset-on-upgrade (M-01/M-02), then v2 bootstrap | `LegacyStoreReset.kt`, `AvagoApplication.startSchemaV2Cutover()` → `observeIdentityForV2Sync()` | `LegacyStoreResetTest`, `V2StoreResetTest` |
| App wiring | `core/sync/.../v2/V2HttpTransport.kt` (ktor, shared bearer client), `V2SyncCoordinator.kt` (opens and publishes stores, pull, then outbox flush, 426 → `ServerV2Capability.INCOMPATIBLE` → update-required screen), `V2SyncHook.kt`. `SyncEngine.sync()`/`pushIfNeeded()` forward to the hook while v2 is on, so the worker, connectivity, foreground and DAO nudges all drive v2. | — |

### Sync engine (sync-wire.md)

- **Device registration.** `POST /v2/devices` runs once per device id; the id is recorded in the store's meta. On `409 device_owned_elsewhere` the coordinator generates a new UUIDv7 and
  retries.
- **Sessions.**
  - Opens bootstrap, incremental or continuation sessions, with `projection_versions`.
  - `bootstrap_required`, `epoch_changed` and `feed_reset` restart the session.
  - A restart is capped at 4 attempts.
- **Paging.**
  - Each page is applied **in one transaction** together with its cursor (`sync_state`) and the
    chain's `after`/page token. A crash resumes from the last applied page.
  - The engine checks contiguity (`session_id`, `after` == previous `last_seq`).
  - It retries the same token on network errors and 5xx, with backoff and `Retry-After`.
- **Tombstones.**
  - Deletes run children-first (descending table rank). `ON DELETE RESTRICT` fires immediately,
    even with deferred FKs.
  - Upserts check `version`: an older version never overwrites a newer row.
- **Constraint violations.**
  - A constraint violation fails the batch: nothing from the page is applied, it is logged, and
    the engine returns `V2SyncResult.Failed`. **Rows are never silently skipped.**
  - Exception: a foreign-key failure on a non-final page is carried into the next page's
    transaction (at most 32 pages), because a parent may arrive later in the same session.
  - On the final page an FK failure sets `bootstrap_required`.
- **Ack.** `POST …/sync/acks` is sent after the final page, with `bootstrap_completed` on
  bootstrap.
- **426.** Any endpoint returning 426 gives `V2SyncResult.UpgradeRequired`. The coordinator
  records `INCOMPATIBLE`, and `V2Flag.updateRequired` raises the M-02 screen.
- **`base_version` conflicts.** Commands carry `base_version`. A `rejected` outcome (e.g.
  `version_conflict`, with its `conflict_id`) is stored as the command's error, its overlay is
  dropped, and queued dependents are rejected; the server row stays authoritative.

### Command outbox

- **Enqueue.**
  - The command id is a UUIDv7 and doubles as the idempotency key.
  - `request_hash` = sha256 of the canonical JSON of the request body.
  - The command gets a monotonically increasing `causal_order`.
  - Dependency edges, upload jobs and the optional overlay are stored in one transaction.
- **Flush.**
  - Commands run in causal order, one at a time, with a lease.
  - A command waits while an earlier command for the same entity is unresolved, while one of its
    dependencies is unacknowledged, or while an upload is not `finalized` (the upload gate).
- **Replays.** A replay reuses the stored id and body. Unknown outcomes (network loss, 5xx, a
  crash while leased) are resolved with `GET …/commands/{id}` first (receipt reconciliation).
- **Retries.** Exponential backoff (1 s doubling, capped at 5 min); `Retry-After` is honoured
  for 429/503/5xx.
- **Outcomes.**
  - `committed` becomes `acknowledged`, and is retired once a sync covers its `result_watermark`.
  - A rejection or final 4xx becomes `rejected` with the stored error, and dependent commands
    are rejected with `dependency_rejected`.
  - `device_not_registered` clears the registration so the next sync re-registers.

## 2. Reads switched to v2

`LocationBinReads` (`LocationBinReads.kt`) is the facade every caller now uses. It reads v2 when
`V2ActiveStores` holds the stores for the requested account, and the legacy DAOs otherwise.
Callers:

- core/ui `LocationPickerViewModel`
- inventory `BinViewModel` (list + detail), `CycleCountDetailViewModel`, `AddEditPartViewModel`,
  `CreateTransferRequestViewModel`, `LocationPickerSheet`, `BinPickerSheet`
- rentals `OnboardingRepository`
- rentalsops `RentalOpsRepository`
- settings `MyTechProfileViewModel`
- workorders `WorkOrderRepository`

| Legacy read | v2 projection | Mapping (`V2LocationReader`) |
|---|---|---|
| `LocationDao` observe/get | `locations` (places_parties) | `code` → `shortCode`, `address_line1` → `address`, `locality/region/country_code` → `city/state/country`, decimal lat/lng → `Double`, `version` → `serverVersion`, `archived_at` → `archived`/`active` |
| `BinDao` observe/get | `bins` (stock) | `capacity_qty_micros / 1e6` → `capacity`, `bin_kind` → `binType`, `name` falls back to `code`. Aisle/shelf/slot and the current count are null (stock is in `stock_positions`). |

Location and bin **writes** still go to the legacy tables. They need `location.*` and `bin.*`
commands through the outbox, with overlays.

## 3. Remaining DAOs and repositories

These still read the emptied legacy tables when v2 is on. Most have v2 projections in the
generated schema, but the models differ (ids, micros, enums, split tables). Each needs an adapter
or a model rewrite, plus outbox commands for its writes.

| Legacy DAO / repository | v2 projection(s) | Server area |
|---|---|---|
| `AssetDao`, `AssetModelDao`, `AssetStatusDao`, `AssetCustomStatusDao`, `AssetCriticalityDao`, `AssetLocationHistoryDao`, `OwnerAssignmentDao`, `MeterReadingDao`, `MeterTriggerDao` | `assets`, `asset_*_details`, `asset_models`, `asset_manufacturers`, `account_asset_*`, `asset_meters`, `meter_readings`, `asset_status_intervals`, `asset_responsibilities` | maint |
| `LogDao`, `LogCostLineDao`, `PhotoDao`, `ServiceDao`, `JobDao`, `CategoryDao`, `EventDao`, `ScheduleDao` | `activity_records`, `activity_cost_lines`, `attachments`, `categories` | maint (+ core `files`) |
| `WorkOrderDao`, `WorkOrderAssetDao`, `WoAssignmentDao`, `WoChecklistItemDao`, `WoCommentDao`, `WoTemplateDao`, `WorkPermitDao`, `WorkPermitSignatureDao`, `RcaReportDao`, `RequestPortalDao` (the rest of `WorkOrderRepository`) | `work_orders`, `work_order_assets`, `work_order_assignments`, `work_order_templates`, `procedure_*`, `comments`, `comment_*`, `work_order_sla_policies`, `signatures` | maint |
| `InspectionDefectDao` | `inspection_defects` | maint |
| `TechProfileDao`, `TechLaborRateDao` | `workers`, `work_assignments`, `time_entries` | maint |
| `PartDao`, `ItemDao`, `InventoryDao`, `InventoryTransactionDao`, `StockingLevelDao`, `CycleCountDao`, `CycleCountLineDao`, `PartIssueDao`, `PartIssueLineDao`, `PartTransferRequestDao`, `GrnDao`, `GrnLineDao` | `products`, `product_lots`, `stock_units`, `stock_positions`, `stock_movements`, `stock_movement_lines`, `stock_ledger_legs`, `stocking_policies`, `stock_reservations` | stock |
| `PurchaseOrderDao`, `PoLineDao`, `PoCommentDao`, `ReorderSuggestionDao`, `LabelTemplateDao` | `purchase_orders`, `purchase_order_lines`, `reorder_suggestions`, `label_templates` | stock |
| `VendorDao`, `VendorContactDao`, `VendorPartDao` | `parties`, `supplier_profiles`, `product_suppliers`, `party_contacts`, `party_contact_points` | places_parties / stock |
| `AccountRolePermissionsDao`, `RolePermissionDefaultsDao`, `RoleLabelCacheDao`, `UserDao`, `DeviceDao` | `effective_permissions`, `account_members`, `location_roles` | core / places_parties |
| `DocDao` | `files`, `attachments` | core / maint |
| `GlAccountDao` | none | not in the v2 contract yet |
| `PmPlanDao`, `PmPlanIntervalDao` | `pm_plans`, `pm_triggers`, `pm_occurrences` | maint |
| `RentalDao`, feature/rentals, feature/rentalsops (incl. `RentalSyncWorker`, `RentalFleetWidget`) | `rental_*`, `resource_allocations`, `signatures`, `invoices`, `invoice_lines`, `customer_balances` | rental |
| `ScoutHistoryDao`, `ScoutPendingDao`, marketplace | `listing_*`, `storefront*`, `meetup_requests`, `reviews`, `review_*`, `trust_case*` | specialist |
| `Chat*Dao` (feature/chat, `BackgroundSyncCoordinator`, realtime) | chat store `threads`, `thread_members`, `messages`, `message_*`, `reactions`, `thread_last_read`, `user_thread_pins` | specialist (chat) |
| `ConfigDao`, `CustomFieldDefinitionDao` | `config_documents`, `config_overrides`, `reference_values`, `custom_field_*` | core |
| `SyncMetadataDao`, `SyncQueueDao`, `SyncConflictDao` | replaced by `sync_state`, `pending_commands`, `sync_conflicts` | sync (legacy only) |

Also remaining:

- `V2Uploader` implementation for `upload_jobs`. The E12 upload endpoints are not on the server
  yet; the gate already holds the commands back.
- `detail_cache` reads.
- UI for `V2CommandOutbox.failures()` and `sync_conflicts`.
- The command catalogue for each feature: each screen's writes as `V2NewCommand`s with overlays,
  using `V2SyncCoordinator.outbox()`.
- Other legacy workers that still run while v2 is on: the rentals pull, chat background sync and
  realtime, and the photo cache sweeper.

## 4. Tests

Mock transport (`V2TestSupport.kt`: `MockTransport`, `ScriptedServer`, fixtures) with an
in-memory xerial SQLite via `JdbcV2Connection`.

**`V2SyncEngineTest`** (13) covers:
- bootstrap paging through both streams, ack and cursor commit
- incremental sessions with committed cursors and tombstones
- per-page atomicity: a failing row rolls back its whole page, surfaces `Failed` and keeps the
  staged position
- a non-contiguous page
- FK carry-over to a later page, and a final-page FK failure → `bootstrap_required`
- 426 on open and on a page
- `bootstrap_required` and feed-generation changes → re-bootstrap
- 410 on a page → continuation session
- transient page errors retry the same token
- the version guard (older never overwrites newer)

**`V2CommandOutboxTest`** (12) covers:
- the request hash
- acknowledgement and retirement by watermark (pending and already covered)
- a lost response resolved by receipt and replayed with the same id and body; an already
  committed receipt is not resent
- capped exponential backoff
- a version conflict surfaced, rejecting queued dependents
- dependencies and per-entity order
- the upload gate
- 426 keeps the command queued
- a final 4xx is recorded, not retried
- unsupported operations refused at enqueue

**`V2StoreResetTest`** (2) covers reopening for the same user / refusing another user, and
refusing a store built from another contract DDL.

The existing `LegacyStoreResetTest` and `V2FlagTest` still pass.

## 5. Open questions

1. Chat projections arrive on the account stream and are applied to the chat store in their own
   transaction, before the business page. Plan §12 describes a separate chat stream. Confirm.
2. `effective_permissions` keys drop the leading user part locally (the store is per user).
   Confirm the key format used for permission checks.
3. The generated Kotlin DTOs (`out/kotlin`) are not vendored; the wire DTOs are hand-written.
   Vendor them once the contract freezes?
4. Canonical JSON golden vectors from the server (P1-10) are needed. The request hash matches
   Python `json.dumps(sort_keys=True, separators=(",", ":"))` but has not been checked byte for
   byte against the server.
5. A page checksum mismatch is only logged, because the canonical form is unconfirmed (see 4).
6. FK carry-over across pages (max 32) and `bootstrap_required` on a final-page FK failure are an
   interpretation of "fail the batch". Confirm that parents can follow children within a session.
7. If the ack is lost, the cursors are already committed per page, so the next session resumes
   from them and the server re-sends from its last acked watermark (idempotent by `version`).
8. Device id: the v2 device id is a fresh UUIDv7 in `avago_v2_flags.xml` (`v2_device_id`). iOS
   reuses the legacy id when it is a UUID instead.
9. The legacy account and user ids are used as-is for the v2 store paths and identity; they are
   assumed to be the v2 UUIDs.

Deviations from sync-wire.md: none. The iOS port stages the whole bootstrap and skips
constraint-violating rows; Android applies each page atomically and fails the batch.
