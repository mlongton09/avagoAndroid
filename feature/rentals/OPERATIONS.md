# Rentals field operations (package `com.avago.feature.rentals.ops`)

Phase 2 of the rentals plan (`AvagoWeb/docs/rental-plan.md` §7, `rental-plan-detailed.md` §11.1, §14–16):
driver delivery/pickup tasks, class → unit assignment, branch transfers, driver route sharing, rental push
events, customer statements and AR aging. It used to be the separate `:feature:rentalsops` module with its
own per-account `rentalops.db`; schema v2 (plan 4.1) folded it into `:feature:rentals` (P5-06).

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

## Storage and writes (schema v2)

- **One business store.** Tasks, task units, transfers, orders, lines, allocations and the fleet are typed
  tables of the per-user/account v2 business store (`rental_delivery_tasks`, `rental_delivery_task_assets`,
  `rental_transfers`, `rental_order_lines` …), read through the core/data repositories
  (`RentalDispatchRepository`, `RentalRepository`, `AssetRepository`). `RentalOpsRepository` only maps them
  to screen models.
- **Writes** are rental catalog commands in the v2 command outbox (optimistic overlay, rolled back when the
  server refuses them); refused changes show on the hub and in Failed changes.
- **Proof photos and signatures** are drafts under `accounts/<account>/rentalops_media/<task>` until
  Complete stages them for upload (E12 `upload_jobs`); drafts and unsent staged copies are never evicted.
- **Reports** (statement, AR aging, today's route) are read-only REST answers; the last good answer is kept
  under `accounts/<account>/rentalops_cache` for offline viewing and is never shown after a 403.
- **Route pings** queue in `accounts/<account>/route_pings.json` (a location trace: inside the account
  directory, so it is excluded from backup and device transfer) until `POST routing/pings` accepts them.
- The retired `rentalops.db` (and its WAL/SHM files) is deleted when the account's v2 stores open
  (`V2StorePaths.prepareStoreFiles`).

## REST endpoints still used (read-only or not store-backed)

| Endpoint | Client |
|---|---|
| `GET …/availability/search` | `RentalOpsApi.availability` (assignment candidates) |
| `GET …/routing/my-route`, `POST …/routing/pings` | `myRoute`, `postPings` |
| `GET …/customers/{id}/statement`, `GET …/billing/customers/{id}/statement/pdf` | `statement`, `statementPdf` |
| `POST …/billing/customers/{id}/statement/email` | `statementEmail` (`client_uuid` per intent) |
| `GET …/reports/ar-aging` | `arAging` |
| FCM data push (`category`, `*_loc_key`, `loc_args`) | `RentalPushRouter`, `RentalOpsPushHandler` |

Covered by `RentalOpsContractTest`, which uses the server doc examples verbatim.