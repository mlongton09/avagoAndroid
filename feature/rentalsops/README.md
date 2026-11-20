# feature:rentalsops: Rentals Phase 2 (field operations)

Phase 2 of the rentals plan (`AvagoWeb/docs/rental-plan.md` §7, `rental-plan-detailed.md` §11.1, §14–16), built on
top of P1 (`feature:rentals`, merged in 06fff34). Server contract: AvagoSvc `docs/rentals-api-stage-c.md`
(origin/master 6ab0092), plus the stage A/B docs.

## Screens (routes: `push/RentalPushRouter.kt` → `RentalOpsRoutes`)

| Route | Screen | Permission (UI gating; the server enforces) |
|---|---|---|
| `rentalops/home` | Hub: out / due today / overdue counts (P1 `LocalFleet`), my tasks, pending sync | any of the below |
| `rentalops/tasks` | Delivery/pickup task list: open/done, mine/all, order number, customer, job site | `rental.drive` / `rental.dispatch` / `rental.view` |
| `rentalops/task/{id}` | `RentalDeliveryScreen`: navigate (Google Maps intent, `geo:` fallback), call (contact, else customer phone), start → arrive → complete/fail, proof photos (camera + FileProvider), Canvas signature, signer name | `rental.drive` (own tasks) / `rental.dispatch` |
| `rentalops/assign`, `rentalops/assign/{lineId}` | Assignment work queue (`unassigned-lines`) with free class units and hold expiry; candidate units from P1's fleet/allocation store | `rental.view` + `rental.reserve` |
| `rentalops/transfers` | Branch transfers: scan a unit (CameraX + ML Kit) or type a code, pick destination/ETA, ship/receive | `rental.transfer` |
| `rentalops/statement` | Customer statement (read-only), with **Share PDF** and **Email** | `rental.view`; PDF/email `rental.reports` |
| `rentalops/aging` | AR aging chart (core/design chart theme) + table + customer drill-down | `rental.reports` |
| `rentalops/settings` | Per-event push switches backed by server `notification-prefs` | any |

The home-screen widget is P1's `RentalFleetWidget`; P2's Glance widget was dropped during reconciliation.

## Offline-first design

- **Storage.** P1's app-database tables are the source of truth for fleet, allocations, customers and orders
  (`LocalFleet`, `rental_fleet`, `rental_customer`). This module keeps a small per-account Room database,
  `rentalops.db` v1, for P2-only data: task and transfer feed rows, the `unassigned-lines` snapshot,
  proof-media metadata, feed cursors, and the cached statement/aging reports. Keeping it separate means P2
  needs no app-database migration.
- **Writes.** Every write is queued in the shared `sync_queue` with a `client_uuid`. The queue types are
  `rentalops_task_event`, `rentalops_transfer`, `rentalops_transfer_step` and `rentalops_assign`. They drain
  through P1's `core/sync` `ExternalOutbox` hook, run by `SyncEngine` and WorkManager. Queued task events are
  folded over the server status by `DeliveryStateMachine`.
- **Drain outcomes** (`sync/DrainOutcome.kt`):
  - Transport errors, 401, 408/425/429 and 5xx are retried, up to 10 attempts.
  - 403 `not_assigned` is **never retried**. The task's queued events and draft proof media are dropped, the
    driver gets a "task reassigned" notification, and `my-tasks` is re-pulled (it arrives there as a
    tombstone).
  - Any other 4xx (including 404/405, now that stage C is deployed) is kept as a conflict and shown to the
    user, who can retry or discard it.
  - A replayed 409 `invalid_transition` whose `details.from` shows the step already happened counts as done.
- **Proof photos.** Each photo or signature goes through `POST …/dispatch/tasks/{id}/proof-uploads` with
  `client_uuid` = the local media id, then `PUT upload_url`, then `POST /attachments/{id}/finalize`. A replay
  with `upload_url: null` (already uploaded) skips both the PUT and the finalize. The resulting ids are sent as
  `photo_file_ids` and `signature_file_id` on `complete`.
- **Assign.** `assign-unit` sends `version` = the cached line's `order_version`. On success the line is dropped
  from the snapshot and the order's other cached lines take the new order `version`.

## Endpoint status (stage C final contract, 6ab0092)

| Endpoint | Client | Status |
|---|---|---|
| `GET …/dispatch/my-tasks?cursor=` | `RentalOpsApi.myTasksFeed` | ✅ wrapper `{entity, items, next_cursor, has_more}`; tombstones kept |
| `GET …/sync/rental_delivery_task`, `…/sync/rental_transfer` | P1 `RentalsApi.syncFeed` (delegated) | ✅ the account-wide feed; on 403 it switches to `my-tasks` |
| `POST …/dispatch/tasks/{id}/{start\|arrive\|complete\|fail}` | `taskAction` | ✅ `{at, version, lat, lng, reason, photo_file_ids, signature_file_id, signed_name, notes}`; 403 `not_assigned` → reassigned |
| `POST …/dispatch/tasks/{id}/proof-uploads` | `proofUpload` | ✅ `client_uuid` idempotent; the null-`upload_url` replay is handled |
| E12 upload (register → PUT → finalize) | `Files` / `V2ApiUploader` | ✅ the legacy `attachments/{id}/finalize` call was removed |
| `POST …/transfers`, `POST …/transfers/{id}/{ship\|receive\|cancel}` | `createTransfer`, `transferAction` | ✅ the stage A asset-move fallback was removed |
| `GET …/unassigned-lines?location_id&limit` | `unassignedLines` | ✅ new; replaces the derive-from-order-lines workaround |
| `POST …/orders/{o}/lines/{l}/assign-unit` | `assignUnit` | ✅ `{asset_id, version}` |
| `GET …/customers/{id}/statement` (stage B) | `statement` | ✅ `rental.view` |
| `GET …/billing/customers/{id}/statement/pdf` | `statementPdf` | ✅ new; shared via FileProvider |
| `POST …/billing/customers/{id}/statement/email` | `statementEmail` | ✅ new; `client_uuid` per intent; 400 `invalid_value` → "no recipient" |
| `GET …/reports/ar-aging` (stage B) | `arAging` | ✅ `rental.reports` |
| `GET/PUT …/notification-prefs` | `notificationPrefs`, `putNotificationPref` | ✅ new; server-enforced, with a device mirror for offline and 403 |
| `GET …/availability` | P1 `RentalsApi.availability` (delegated) | ✅ |
| FCM data push (`category`, `*_loc_key`, `loc_args`) | `RentalPushRouter`, `RentalOpsPushHandler` | ✅ 7 channels; 26 loc keys in 9 locales; checks `account_id` |
| `GET …/billing/ar-aging` (stage C buckets `d1_30`…) | — | ⏸ not used; the stage B report suffices for the read-only view |
| `POST …/orders/{o}/assign-units` (bulk) | — | ⏸ not used on mobile |

Covered by `RentalOpsContractTest`, which uses the doc examples verbatim.

## Reconciliation with P1 (done)

- **Models.** `FleetCard`, `OrderLine`, `OrderSummary` and `allocationFrom` were removed. Assignment candidates
  come from P1's `LocalFleet.unitOf`/`allocOf` through small adapters (`toAssignmentUnit`,
  `toAssignmentAllocation`).
- **API.** The sync feeds and availability go through P1's `RentalsApi`. The line-PATCH and asset-move calls
  were removed.
- **Fleet cache.** Dropped. Counts come from `LocalFleet.compute`, asset names from `rental_fleet`, and
  customers from `rental_customer`.
- **Widget.** P1's `RentalFleetWidget` was kept. P2's Glance widget, its `WidgetCounts` and the glance
  dependency were removed.
- **Sync hook.** P1's `ExternalOutbox` is used as is. The queue view uses `rentalDao().observeQueueItemsOfTypes`.

## Removed workarounds

- 404/405/501 "not deployed yet" retries and the 6 h re-probe (`NOT_DEPLOYED`, `Pull.UNSUPPORTED`).
- The transfer fallback to the stage A asset move.
- Deriving the assignment queue from synced order lines. `unassigned-lines` replaces it.
- Treating 403 on a task action as a generic rejection. It is now `not_assigned` handling.

## TODOs

- Clear `rentalops.db` on sign-out (the database is already per account).
- Use the stage C `billing/ar-aging` buckets (`d1_30`…, `currency` filter) when multi-currency aging is needed.
- Server: visible (notification) FCM messages for non-chat pushes are a stage C follow-up. Until then the app
  builds the banner from the data fields.
- Server: the PDF renderer uses WinAnsi Helvetica, so non-Latin-1 names print as `?` (stage C known limitation).