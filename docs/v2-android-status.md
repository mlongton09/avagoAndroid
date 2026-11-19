# Android schema-v2 status (P3-12, P3-12b, P3-12c)

This doc describes the native v2 Android app: the screens read the bundled v2 SQLite store through
domain repositories, and every write is a typed P3-14 catalog command sent through the command
outbox. The iOS equivalent is `avago/docs/v2-ios-status.md` (P3-11).

- Enablement and reset: `docs/v2-reset-spec.md`.
- Contract (AvagoSvc): `schema/contract/design/sync-wire.md`, `out/openapi/sync.yaml`,
  `out/sqlite/*.sql` and `out/commands/catalog.json`. Notes: `schema/v2/notes/p3-07.md`,
  `p3-08.md` and `p3-14.md`.
- The DDL and the catalog are generated from AvagoSvc `origin/schema-v2` at `be4d70f` (see
  `V2GeneratedSchema.SOURCE_REVISION`): the P3-14 catalog plus the P3-14b rental commands. The
  contract changes up to `9dbc7c4` (P4-06, P3-15b) are server-only (functions, privileges,
  triggers) and change neither the mobile DDL nor the catalog.
- P3-15b (L37) replaced the `sync_nudge` push with the E13 silent hint
  `{"t":"sync_hint","account_id":…,"w":…}`; `AvagoFcmService` starts one pull + flush for it.

P3-12b's legacy bridge (Room read mirror plus REST write shim) was removed in P3-12c. Nothing reads
legacy Room tables for business data, and nothing writes business data over REST.

## 1. Enablement

The v2 store is used when `V2Flag.isEnabled()` is true **and** the legacy reset marker is complete
(`V2StoreGate`).

- **Internal builds.** Build with `-Pv2Default=true` (`BuildConfig.V2_DEFAULT` →
  `V2Flag(defaultEnabled = true)`).
- **Debug override.** Settings → debug v2 toggle (M-02).
- **Flag off.** With the flag off the app is inert by design: per U5/U6 there are no external users
  and cutover is fresh, so the legacy sync path was deleted rather than kept alive. `SyncEngine`
  is a no-op, `SyncGate` stays closed, and the repositories emit empty lists while
  `V2ActiveStores` holds no store.

## 2. Native architecture

```
Compose screen ─► ViewModel ─► domain repository interface   (core:data domain/*)
                                   │
                                   ▼
                        V2*Repository (core:data v2/repo)
              reads: SQL over the v2 store + V2OverlayMerge  ─► Flow<domain model>
              writes: command builder (v2/commands) ─► V2CommandService.submit
                                   │
                                   ▼
   V2CommandOutbox: pending_commands + optimistic overlay + upload_jobs, in one transaction
                                   │  flush (V2FlushScheduler / SyncEngine)
                                   ▼
   E12 upload (V2ApiUploader: register → PUT → finalize) ─► POST /v2/…/commands
   committed → acknowledged → retired once a sync covers result_watermark (overlay dropped)
   rejected  → overlay deleted (rollback); FailedChange shown on the Failed changes screen
```

### Domain layer (`core/data/.../domain`)

Screens depend only on these interfaces and on plain domain models. Storage types never reach a
screen.

| Interface | Implementation | Covers |
|---|---|---|
| `AssetRepository`, `ReferenceRepository`, `WorkerRepository` | `V2AssetRepository`, `V2WorkerRepository` | assets (incl. tool/vehicle details read-only, meters, readings, attachments/photos, status), asset types/models/makes, workers and assignments, tech profile |
| `WorkOrderRepository` | `V2WorkOrderRepository` | work orders, assets, assignments, checklists (procedure responses), comments, labour (time entries, timer), parts/materials (cost lines), completion notes, failure/cause/remedy codes, permits + signatures, RCA, templates |
| `ActivityRepository` | `V2ActivityRepository` | log (activity records), cost lines, inspections, defects, services, photos |
| `PmRepository` | `V2PmRepository` | PM occurrences (they replace schedules): list, calendar, complete/skip/defer |
| `DocumentRepository` | `V2DocumentRepository` | documents (file staged, then uploaded through E12, then `core.documents.create`) |
| `LocationRepository` | `V2LocationRepository` | locations and bins (on-hand per bin is overlay-aware) |
| `InventoryRepository` | `V2InventoryRepository` | products/parts, stock positions, movements (stock transaction history), receipts/issues/transfers/counts, part issues, cycle counts |
| `SupplierRepository`, `PurchaseOrderRepository` | `V2PurchasingRepositories` | vendors, contacts, product suppliers, purchase orders, PO lines, goods receipts |
| `ConfigRepository` | `V2ConfigRepository` | config documents, reference values |
| `ChangeQueueRepository` | `V2ChangeQueueRepository` | pending count, per-record sync state (saved/pending/failed), failed commands, open conflicts, dismiss |
| `RentalRepository` | `V2RentalRepository` | rental orders, lines, allocations, hand-overs (check-out/check-in with photos and signature), swaps, bulk returns, payments, invoice drafts, agreement signatures, readiness checks, customers + customer documents, rental classes, asset rental enablement, fleet inputs (`LocalFleet`), refused rental commands with hand-over retry |
| `RentalDispatchRepository` | `V2RentalDispatchRepository` | delivery tasks (assign/start/arrive/complete with proof photos + signature/fail/cancel/reopen) and unit transfers (create/ship/receive/cancel) |
| `SyncRepository` | `EngineSyncRepository` (core:sync) | "refresh now" for ViewModels; feature modules no longer depend on core:sync |
| `CostReportRepository` | core:reports | cost report from v2 activities, assets and stock value |

All of these are bound in `RepositoryModule` (Hilt).

### Reads

Each repository runs SQL against the v2 store (`V2StoreAccess`) and re-queries when a table it
reads changes (table-level change notifications), so it returns a `Flow` of domain models.

Pending optimistic changes are merged by `V2OverlayMerge`, which handles:
- upserts, including explicit null clears;
- creates and deletes;
- additive deltas, such as stock quantities and purchase-order totals;
- re-checking the query filter after a row changes.

Money and quantities are converted from micros and minor units (`V2Micros`, `V2Money`) only at
this boundary.

### Writes

- **Payload types.** `scripts/gen_v2_contract.py` generates `V2GeneratedCommands.kt` from
  `catalog.json`. It contains 137 typed payload classes (93 from P3-14 and 44 rental/customer commands from P3-14b), each with its operation name and a `Clear`
  enum listing the fields that can be cleared.
- **Builders.** The builders are `StockCommands`, `InventoryCommands`, `PurchasingCommands`,
  `WorkOrderCommands`, `MaintCommands` and `CoreCommands`. Each builder:
  - validates the domain rules (for example, a PO can be approved only while it is pending approval);
  - computes the `set`/`clear` diff against the stored row with `V2UpdateDiff`;
  - sets `base_version`;
  - builds the optimistic overlay patch.
- **Clearing fields.** A field emptied in a form is sent in `clear` and overlaid as `NULL`.
  If the field's command has no `Clear` entry for it, the builder throws
  `FieldNotClearableException` and the form shows the error. It is not silently ignored.
- **Unchanged forms.** An edit that changes nothing returns no command.
- **Submit.** `V2CommandService.submit` enqueues the command, its overlay, its dependency edges
  and its upload jobs in one transaction, then schedules a flush.

### Outbox

| Topic | Behaviour |
|---|---|
| Ids | UUIDv7 ids (L32), which double as idempotency keys; `request_hash` |
| Ordering | causal order; per-entity ordering |
| Gating | dependency gate and upload gate |
| Retries | receipt reconciliation for unknown outcomes; capped backoff and `Retry-After` |
| Upgrade | 426 → update-required screen |

**On rejection:**
- The overlay is deleted, which is the rollback: the screen shows the server row again.
- Dependent commands are rejected with `dependency_rejected`.
- The failure (operation, entity, code, message) appears in **Settings → Failed changes**
  (`FailedChangesScreen`), where it can be dismissed.

**Per-record sync state.** Detail screens show it via `observeRecordState`.

**Conflicts.**
- A `version_conflict` rejection carries its `conflict_id`. It is listed with the failed changes,
  and the server row stays authoritative.
- `sync_conflicts` rows are informational, because the catalog has no resolve or dismiss command
  (see catalog requests).
- The old keep-mine/use-server conflict UI was deleted.

### Uploads (binaries)

1. `V2FileStager` copies a picked or captured file into app storage under its UUIDv7 `file_id`.
   That copy also serves local display.
2. The link command (`maint.attachments.create`, `core.documents.create`, etc.) is enqueued
   together with an `upload_jobs` row.
3. The upload gate holds the command until `V2ApiUploader` has completed E12 register → PUT
   (`V2HttpBlobTransport`, presigned URL) → finalize.

### Sync

- **`V2SyncEngine`** handles:
  - sessions and paging, with each page applied atomically together with its cursor;
  - tombstones and the version guard;
  - FK carry-over;
  - acks and 426.
- **`V2SyncCoordinator`** handles:
  - device registration;
  - opening and publishing the stores;
  - pull, then outbox flush.
- **`SyncEngine`** (core:sync) is a thin facade over the coordinator, used for sync/resync, rate
  limiting and account-gone events.
- **`SyncWorker`** runs periodically and on connectivity, foreground and FCM nudges.
- **`SyncGate`** opens from the v2 store's bootstrap state.

The sync-engine details are unchanged from P3-12 (see git history of this file).

## 3. Features converted (P3-12c)

| Feature | Screens | Commands used |
|---|---|---|
| Inventory | parts list/detail/add/edit, warehouse, bins, stock transaction history, part issues, transfers (read-only), reorder | `stock.products.*`, `stock.documents.*` (receipt/issue/transfer/count, reverse), `stock.part_issues.*` |
| Cycle counts | list, detail, floor scan | `stock.cycle_counts.create/count_line/…`: **floor scans now reach the server**, which fixes the old bug where scans stayed local |
| Purchasing | vendors (+ contacts, product suppliers), POs (lines, submit/approve/reject/order/cancel/close), goods receipts (post/reverse) | `places_parties.vendors.*`, `places_parties.party_contacts.*`, `stock.product_suppliers.*`, `stock.purchase_orders.*`, `stock.purchase_order_lines.*`, `stock.goods_receipts.*` |
| Locations/bins | pickers, bin list/detail (the app has no location/bin edit screens) | ? |
| Work orders | list, available jobs, calendar, dispatch board, detail, create/edit, close-out (notes, failure/cause/remedy), checklists, labour timer, cost lines (parts/materials), comments, permits + signatures, RCA, templates, tech picker | `maint.work_orders.*`, `maint.work_order_assignments.*`, `maint.procedure_responses.*`, `maint.comments.*`, `maint.work_permits.*`, `maint.rca_reports.*`, `maint.work_order_templates.*`, `maint.activity_cost_lines.*` |
| Assets | detail, logs, meters + readings, documents, gallery/photos, picker, scanner, add/edit, wheel, onboarding | `maint.assets.*`, `maint.meter_readings.*`, `maint.attachments.*` |
| Log | list/detail/edit, inspections, open defects, services, performed-by picker, maintenance scanner | `maint.activity_records.*`, `maint.activity_cost_lines.*`, `maint.inspection_defects.*`, `maint.attachments.*` |
| PM | schedule list/detail/calendar on PM occurrences | `maint.pm_occurrences.complete/skip/defer` |
| Documents | add document (asset-owned, OCR prefill) | E12 upload + `core.documents.create` |
| Tech profile | my profile | `maint.workers.update` |
| Reports | cost report (v2 aggregation) | — |
| Scout | HITL-off executor writes through the domain repositories | catalog commands |
| Settings | Failed changes screen (replaces Sync conflicts) | — |
| Rentals | home, fleet, schedule, scan, quick book, order detail, hand-over (check-out/in), swap, readiness, sign agreement, customers, asset rental settings | `rental.rental_orders.*` (create/quote/reserve/hold/update/extend/off_rent/close/cancel/no_show/record_payment/generate_agreement/request_signatures), `rental.rental_order_lines.*` (create/assign_unit/swap), `rental.rental_handovers.*` (checkout/checkin, readiness `create_check`/`update_check`), `rental.rental_bulk_returns.create`, `rental.invoices.create_draft`, `rental.signatures.*`, `places_parties.customers.*`, `maint.assets.enable_rental/disable_rental`; photos and signatures through E12 |
| Rental ops | home, task list, driver route, delivery (proof photos + signature), assign units, transfers, finance (AR aging, statement PDF), push settings | `rental.rental_delivery_tasks.*`, `rental.rental_transfers.*`, `rental.rental_order_lines.assign_unit`, through `RentalDispatchRepository` / `RentalRepository` |

### Intentionally still REST

These are not business-data writes, or the notes keep them on REST:
- **Chat** (p3-08).
- **Rental online reads** (GET or side-effect-free POST): availability search, pricing preview,
  instant-book evaluation, credit check, fee breakdown, KPI summary, payments status, order
  payments, signature requests and their PDF, scan resolve, meter readings, driver route, AR aging,
  customer statement and statement PDF.
- **Driver location pings** (`POST …/routing/pings`): telemetry, not business data (catalog
  request 14).
- **Renter marketplace** (`MarketplaceApi`, renter token): the public marketplace for renters who
  are not workspace members and have no v2 store (magic-link sign-in, reserve, meetups, booking
  changes).
- **KPI dashboard:** `GET /accounts/{id}/reports/kpis` is a server-side read of the v2 `maint.*`
  tables.
- **Lookups:** VIN decode, geocode, OCR extract, WO chat-thread resolve.
- **Account:** account and content deletion, user preferences, invites, sample workspaces.
- **Account admin:** permission sets and custom field definitions (see catalog requests).
- **Crew/growth/provider/marketplace.**

Legacy Room (`AvagoDatabase`) remains only for device-local or REST-backed data: chat and scout
history. The rental tables (`rental_records`, `rental_sync_state`) are dropped by `MIGRATION_20_21`
(schema 21); the other legacy entities were not pruned, so the Room schema and migrations stay
consistent.

## 4. Removed

| Area | Removed |
|---|---|
| Bridge (step 1) | `V2LegacyProjector`, `V2LegacyWritePlanner` (`V2BridgePlan`, `V2RestCall`), `V2LegacyBridge` and their tests; `AvagoServiceClient.sendJson`; the bridge-only `PhotoDao` queries; `SyncEngine` mirroring and the second sync run |
| Legacy sync (core:sync) | `DeltaPushApplier`, `DefectOutbox`, `DefectRegisterSync`, `PhotoDownloader`, `PhotoCacheSweeper`, `PreferencesSync`, `SyncConflictCoordinator`, `SyncConflict`, `SyncPayloadBuilder`, `ExternalOutbox`, `TechLocationService`, `ui/SyncConflictSheet`, `ui/SyncConflictViewModel`, `di/ExternalOutboxModule`, `v2/V2SyncHook`; tests `SyncEngineTest`, `DeltaPushApplierTest`, `DefectOutboxTest`, `DefectRegisterSyncTest`, `SyncPayloadBuilderTest`, `NetworkModelTest` |
| Other modules | whole `core/seed` module (`ConfigSeeder`); core/push `WorkOrderNotificationService`; rentalsops `di/RentalOpsModule`; chat `ConflictBottomSheet`; settings `SyncConflictsScreen`; feature-local legacy repositories/services in workorders (`WorkOrderRepository`, `WoV3Service`), log, assets, inventory, schedule and docs |
| Reports | core/reports `ReportAggregator`, `DepreciationCalc`, `ReportsModule`; feature/reports Financial/Maintenance/WorkOrder report VMs and `ReportsListScreen` (unreachable) |
| core/network | 195 uncalled `AvagoServiceClient` methods (legacy REST writes, sync push/pull, conflicts); 128 unreferenced DTOs; the files `AccountFeatureModels`, `ApprovalModels`, `BulkUpdateModels`, `CategoryModels`, `ChatSavedModels`, `OrganizationModels`, `PoCommentModels`, `SavedFilterViewModels`, `StockingLevelModels`, `VendorContactModels`, `VendorModels`, `WoCostSummaryModels`, `SyncModels`; the placeholders `ChatRealtimeClient.kt` and `OutboxRetryCoordinator.kt` |
| Rentals (feature/rentals) | the legacy REST/Room rentals stack: `data/RentalRepository`, `RentalRecords`, `RentalRequests`, `RentalOutbox`, `RentalSync`, `RentalSyncWorker`, `RentalsModule`, `RentalPhotoUploader` (the former core:sync `PhotoUploader`), `PostChargeService`; `domain/ConflictMerge`, `GatewayPayments`, `OutboxPlanner`, `PostCharges`; `ui/PostChargesSection`; the whole `legacy/` package (asset rentals, booking, customers, customer form, invoice, create-rental sheet); marketplace owner screens + `MarketplaceOwnerRepository`; the whole `onboarding/` package; `strings_gateway.xml` / `strings_onboarding.xml` and 72 unused strings in every locale; tests `RentalsContractTest`, `ConflictMergeTest`, `GatewayPaymentsTest`, `OutboxPlannerTest`, `PostChargesTest`, `LegacyRentalTextTest`, `OnboardingTest`, `PostChargesViewModelTest` |
| Rental ops (feature/rentalsops) | `data/RentalOpsDatabase` (private Room outbox), `sync/RentalOpsOutbox`, `OutboxPayloads`, `DrainOutcome`; server notification-preference sync in `RentalOpsPushPrefs`; statement e-mail; 16 unused strings in every locale; tests `CheckInBodyTest`, `DrainOutcomeTest` |
| Room (core:data) | `db/rental/RentalDao`, `RentalEntities` (tables dropped by `MIGRATION_20_21`); `LocalFleetReadinessTest` (readiness now comes from stored facts, `FleetFactsTest`) |

### Dropped features

These have no v2 table or catalog command, so they were removed instead of being shimmed:
- **Work orders:** WO recurrence and PM plans CRUD, WO audit trail, budget, GL-account picker, job
  picker, effort hint.
- **Workers:** tech certifications and hourly rate.
- **Assets:**
  - tool/vehicle detail edits (read-only now), recall remedy actions, QR scanning, asset address;
  - cover photo, remote image placeholders, log photo "set primary", the FRE sample clone.
- **Log:** derived open-defects mode, log audit, local and server inspection drafts, in-form 409
  merge, cost-line GL code.
- **Documents:** the legacy docs list/detail, which were unreachable.
- **Background jobs:** tech location ping (a server no-op), local WO notifications, delta
  push/metrics, photo cache sweeper.
- **Sync and Scout:** the keep-mine/use-server conflict UI, Scout recurrence direct-execute.
- **Rentals:** post charges (tolls/tickets), gateway card charge/capture/release/refund and
  payment links, marketplace owner tools (inbox, blocks, listings, owner settings), the import
  onboarding wizard, readiness photos, contracts/buy-out/equity, sub-rental display, price
  adjustments, the order ledger list, statement e-mail, server-side push preferences (now
  device-only), the class "free units" hint and check-in location accuracy.

## 5. Catalog requests

The catalog has no command or projection for the following, so the feature was dropped or left
read-only instead of being worked around:

1. **Products:** more clearable fields on `stock.products.update` (`notes`, `manufacturer`,
   `manufacturer_part_number`, …). Today only `default_reorder_qty` is clearable.
2. **Inventory extras:** stocking-policy (min/max/reorder) edits and transfer requests (create/approve); stock transfers themselves post as `stock.documents` transfers.
3. **Documents:** a `documents` projection with a download URL, so documents can be listed and
   opened from the store.
4. **PM plans:** `pm_plans`/`pm_triggers` create/update/delete (schedule CRUD, WO recurrence).
5. **Work orders:**
   - more clearable fields;
   - time entries as their own commands;
   - SLA, audit and budget;
   - approve.
6. **`activity_records.update`:** allow changing `work_order_id`.
7. **GL accounts:** a projection plus a GL code on cost lines.
8. **Workers:** certifications and hourly rate.
9. **Assets:**
   - vehicle/tool detail updates;
   - recalls;
   - QR codes;
   - attachments reorder / set-primary;
   - attribute merge (today the whole object is replaced).
10. **Inspections:** server-side drafts.
11. **Sync conflicts:** resolve/dismiss for `sync_conflicts`.
12. **Account admin:** user preferences, permission sets, custom field definitions (REST today).
13. **Rentals, writes:**
    - post charges (create/charge/void);
    - gateway charge, capture, release and refund; payment request create/send/e-mail;
    - `payer_email` on `rental.rental_orders.record_payment`;
    - marketplace owner writes (meetups, blocks, listings, owner settings) and onboarding import;
    - readiness photos (attachments on readiness checks);
    - contracts / buy-out / equity, price adjustments;
    - customer statement e-mail;
    - rental notification preferences;
    - asset meter create from the rental screens.
14. **Rental telemetry:** a command or stream for driver location pings (REST today).
15. **Rentals, projections:** customer documents, contracts, ledger/payments list, fees, tax,
    terms, signature requests, sub-rentals, rental settings, rate cards for an offline estimate
    (re-check against `rental_rate_tiers`), delivery-task assets (`rental_delivery_task_assets` is
    not mobile), a class "free units" hint, and the customer date of birth.
16. **Purchasing:** a reject reason on `stock.purchase_orders.reject`.
17. **Docs:** `notes/p3-14.md` still lists rentals as deferred; P3-14b added them.

## 6. Tests and build

- **Unit tests:** `gradlew testDebugUnitTest -Pv2Default=true --no-daemon --max-workers=3
  "-Dorg.gradle.jvmargs=-Xmx2g"` passed 679 tests with 0 failures across all modules (core:data
  105, feature:rentals 95, feature:rentalsops 58). The count is lower than the earlier 819 because
  the legacy rentals/rentalsops tests were deleted with the code they covered.
- **New P3-12c tests:**

  | Test | Count | Covers |
  |---|---|---|
  | `V2OverlayMergeTest` | 6 | upsert/null clear, create, delete, additive deltas, filter re-check, JSON round trip |
  | `InventoryCommandBuildersTest` | 10 | receipt/issue/transfer/count overlays and validation; product update diff, base version, clear-to-null, `FieldNotClearableException`, no-op edit |
  | `MaintAndPurchasingCommandBuildersTest` | 4 | asset edit clears to null, non-clearable name, attributes replace/no-op, PO line amounts per currency |
  | `V2InventoryRepositoryTest` | 3 | repository + `V2CommandService` + outbox against a scripted server: base read; optimistic issue (5 → 3), then rejection rolls back to 5, the failure is listed with its code and dismiss clears it; cycle-count create and `count_line` queued as catalog commands |
  | `CostReportBuilderTest` (core:reports) | 5 | cost report aggregation |
  | `RentalCommandBuildersTest` | 14 | rental command payloads and optimistic overlays (booking, hand-over with signature dependency, swap, bulk return, payment, readiness, delivery tasks, transfers) |
  | `FleetFactsTest`, `LocalFleetTest` | 5 + 4 | fleet status and readiness from stored facts |
  | rentalsops `AgingAndModelsTest`, `OpsCountsTest`, `DriverRouteTest`, `DeliveryStateMachineTest` | — | display models built from the v2 domain types |

  Existing tests still pass: `V2SyncEngineTest` (13), `V2CommandOutboxTest` (12),
  `V2StoreResetTest` (2), `LegacyStoreResetTest` and `V2FlagTest`.
- **Build:** `gradlew :app:assembleDebug -Pv2Default=true --no-daemon --max-workers=3
  "-Dorg.gradle.jvmargs=-Xmx2g"` succeeds.
- **Lint:** `gradlew :app:lintDebug` → see §7.

## 7. Lint

`gradlew :app:lintDebug` was run on HEAD and on the pre-P3-12c baseline (`4c17671`, in a separate
worktree):

| | Errors | Warnings |
|---|---|---|
| Baseline `4c17671` | 4 | 32 |
| P3-12c HEAD | 4 | 31 |

- **No new issue types.** The only difference is one fewer `UnclosedTrace`, from the removed
  ConfigSeeder trace.
- **The 4 errors pre-date P3-12c** and are in files this task did not touch:
  - 3 × `FullBackupContent` in `app/src/main/res/xml/backup_rules.xml`;
  - 1 × `PermissionImpliesUnsupportedChromeOsHardware` in `AndroidManifest.xml`.
- **Rental modules** (`lintDebug` per module, after the rentals conversion):
  - feature:rentals went from 77 to 30 errors, all pre-existing `MissingTranslation` for the
    `rentals_ib_*` / `rentals_fees_*` strings (the 47 untranslated post-charge strings were deleted
    with the feature), plus 26 warnings;
  - feature:rentalsops has 0 errors and 11 warnings;
  - core:data has 0 errors and 11 warnings.

## 8. Open questions (from P3-12, still open)

1. Chat projections are applied to the chat store in their own transaction, before the business
   page. Plan §12 describes a separate chat stream.
2. `effective_permissions` keys drop the leading user part locally, because the store is per user.
3. Canonical JSON golden vectors from the server (P1-10) are needed to confirm the request hash and
   page checksums byte for byte. Until then a checksum mismatch is only logged.
4. FK carry-over across pages (max 32) is our interpretation of "fail the batch".
5. The v2 device id is a fresh UUIDv7 (`v2_device_id`); iOS reuses the legacy id when it is a UUID.
6. Legacy account and user ids are used as the v2 UUIDs.
