# feature:rentalsops — Rentals Phase 2 (field operations)

Phase 2 of the rentals plan (`AvagoWeb/docs/rental-plan.md` §7, `rental-plan-detailed.md` §11.1, §14–16).
Kept in its own module so it can land independently of the Phase 1 `feature:rentals` module.

## Screens (routes in `push/RentalPushRouter.kt` → `RentalOpsRoutes`)

| Route | Screen | Permission |
|---|---|---|
| `rentalops/home` | Hub: out / due today / overdue counts, my tasks, pending sync | any rentals permission |
| `rentalops/tasks` | Delivery/pickup task list (open/done, mine/all) | `rentals.drive` / `rentals.dispatch` |
| `rentalops/task/{id}` | `RentalDeliveryScreen`: navigate (Google Maps intent, `geo:` fallback), call, start → arrive → complete/fail, proof photos (CameraX intent + FileProvider), Canvas signature, signer name | `rentals.drive` |
| `rentalops/assign`, `rentalops/assign/{lineId}` | Class bookings that still need a unit; candidate list and assignment | `rentals.reserve` |
| `rentalops/transfers` | Branch transfers: scan unit (CameraX + ML Kit) or type a code, pick destination/ETA, ship/receive | `rentals.transfer` |
| `rentalops/statement` | Customer statement (read-only) | `rentals.reports` |
| `rentalops/aging` | AR aging chart + table + customer drill-down (read-only) | `rentals.reports` |
| `rentalops/settings` | Per-category rental push preferences | any |

There is also a Glance home-screen widget (`widget/RentalOpsWidget.kt`). It shows out / due today / overdue counts
from the local store only, so it never makes a network call.

## Offline-first design

- **Storage.** The module keeps its own Room database, `rentalops.db` v1 (`data/RentalOpsDatabase.kt`).
  - P1 adds rental tables to the app database with a new version number. Sharing that database would mean
    two branches competing for the same version and migration.
  - A separate database avoids any renumbering on rebase, and its cache can be dropped independently.
  - Cached data: tasks, fleet, order lines, locations, transfers, media metadata, and feed cursors.
- **Writes.** Every write is queued in the shared `sync_queue` with a `client_uuid`
  (types `rentalops_task_event`, `rentalops_transfer`, `rentalops_transfer_step`, `rentalops_assign`).
  - The queue is drained through `core/sync` `ExternalOutbox`, which is the same hook P1 uses, and run by
    `SyncEngine` and WorkManager.
  - Queued task events are folded over the server status by `DeliveryStateMachine`, so the UI shows the
    queued state while the cache keeps what the server last confirmed.
- **Drain outcomes** (`sync/DrainOutcome.kt`):
  - Transport errors, 401, 404/405/501 (the endpoint is not deployed yet), 408/425/429 and 5xx are retried,
    up to 10 attempts.
  - Other 4xx responses are kept as a conflict and shown to the user, who can retry or discard.
  - A replayed 409 `invalid_transition` whose `details.from` shows the step already happened is treated as done.
- **Proof photos** use the presigned upload flow (`entity_type = rental_delivery_task`). The upload and finalize
  steps run before the complete action, and the resulting `file_upload_id`s are sent as `photo_file_ids` and
  `signature_file_id`.

## Server contract used (AvagoSvc)

- **Stage A/B:**
  - Feeds: `sync/assets`, `sync/order-lines`, `sync/allocations`, and `GET /locations`.
  - `PATCH` order line.
  - `GET /rentals/customers/{id}/statement` and `GET /rentals/reports/ar-aging`.
- **Stage C** (handlers in `src/routes/rentals_api/{dispatch,transfers,class_booking}.rs`):
  - `GET /rentals/dispatch/my-tasks?cursor=`
  - `POST /rentals/dispatch/tasks/{id}/{start|arrive|complete|fail}` with
    `{at, lat, lng, reason, photo_file_ids, signature_file_id, signed_name, notes}`.
  - `POST /rentals/transfers` with `{client_uuid, asset_id, to_location_id, ship_at, arrive_by, reason}`,
    then `POST /rentals/transfers/{id}/{ship|receive|cancel}`.
  - `POST /rentals/orders/{o}/lines/{l}/assign-unit` with `{asset_id}`.
- **Fallback.** If a stage C transfer endpoint answers 404/405/501, the transfer falls back to the stage A
  asset location move.

## TODOs

- **Stage C (server)**
  - Mount the dispatch, transfers and class_booking routers in `rentals_api/mod.rs`.
  - Publish `docs/rentals-api-stage-c.md` and re-check the field names against it.
- **Stage C (push)**
  - Finalise the FCM payload. `RentalPushRouter` currently accepts a `type`/`event` of the form `rental.*` or
    `rental_*`, plus `task_id`, `order_id`, `transfer_id`, `line_id`, `title` and `body`.
  - Mirror the device notification preferences to `rental_notification_prefs`.
- **Stage C (feed)**
  - Add denormalised order number and customer name to the dispatch feed.
- **Client**
  - Clear `rentalops.db` on sign-out or account switch.
  - Add a periodic full resync to catch server-side deletions.
  - Show the pending badge for transfers whose server id differs from the local `client_uuid`.

## Reconcile after P1 (`feature:rentals`) merges

- **Models.** Duplicated in `domain/Models.kt` (fleet asset, order line, location) and `domain/Aging.kt`
  (`MinorMoney`). Switch to P1's models where they match.
- **API.** `data/RentalOpsApi.kt` duplicates P1's sync-feed, availability and line-PATCH calls.
- **Fleet cache.** P1 caches fleet and lines in the app database. Read from there and keep only the
  task, transfer and media tables here.
- **Widget.** P1's `RentalFleetWidget` overlaps; keep one widget.
- **Core sync hook.** `core/sync` `ExternalOutbox`, `ExternalOutboxModule` and the `SyncEngine` hooks were
  copied byte-for-byte from P1. Expect a trivial rebase conflict and take P1's version.
