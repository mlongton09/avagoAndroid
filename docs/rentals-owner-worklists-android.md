# Android rental owner/staff increment — 2026-11-16

## Scope and isolation

- Base: `6d935cae7c909c9c99937686fddbd6a9a6258dc2`, detached worktree
  `C:\Users\marlong\.copilot\session-state\70d7443e-024e-478f-b1fc-3516bcb3db90\files\rental-android-worktree`.
- Seeded baseline tree: `c217dd86ba7ca6befc395491d019a0878eadca16`.
  This includes the original checkout's existing Gradle 9.6.0 wrapper delta, solely
  to use the locally installed toolchain. The delivery patch excludes that delta.
  Only `sdk.dir` was copied to the ignored `local.properties`.
- No commits, deployment, account configuration, credentials, onboarding, live API
  requests or charges. The original checkout and its nested worktree were not edited.
- The cross-platform phased roadmap belongs to the parent in AvagoWeb; this note
  records Android source coverage and the bounded increment, not a second roadmap.

## Existing implementation audited before adding features

Paths below are relative to `feature/rentals/src/main/kotlin/com/avago/feature/rentals/`
unless otherwise specified. These are source capabilities, not live verification.

| Capability | Canonical Android evidence |
|---|---|
| Account-wide orders and order detail | `ui/RentalOrderScreens.kt`; `data/RentalRepository.kt` per-account Room reads; `data/RentalSync.kt` cursor/`has_more` loop |
| Quick quote/hold/reserve, pricing | `ui/QuickBookScreen.kt`, `data/RentalRepository.kt#createBooking`, `data/RentalRequests.kt` |
| Fleet, readiness and free-unit search | `ui/RentalFleetScreen.kt`, `ui/RentalsOverviewViewModel.kt`, `domain/ServerAvailability.kt`, `domain/Availability.kt` |
| Schedule and today's overdue/pickup/return agenda | `ui/RentalScheduleScreen.kt`, `ui/RentalsHomeScreen.kt`, `domain/RentalsToday.kt` |
| Checkout/checkin, inspection, meters, damage, signature | `ui/HandoverScreen.kt`, `ui/HandoverWave2.kt`, `ui/SignAgreementScreen.kt`, `data/RentalRepository.kt#saveHandover` |
| Return readiness and replacement units | `ui/ReadinessScreen.kt`, `ui/SwapScreen.kt`, `domain/LineSwap.kt` |
| Money, invoices and manual payment recording | `ui/RentalOrderScreens.kt`, `ui/PostChargesSection.kt`, `data/PostChargeService.kt`, shared `core/data/.../rental/Money.kt` |
| Online payment status and request/share/email link | `ui/OrderWave2.kt`, `data/RentalsOnline.kt`; `core/network/.../rentals/RentalsApi.kt` G6 methods |
| Durable offline edits, idempotency, version conflicts | `data/RentalOutbox.kt`, `domain/OutboxPlanner.kt`, `domain/ConflictMerge.kt`; existing regression suites |
| Legacy entry points | `nav/RentalsNavGraph.kt` legacy asset/booking/invoice routes; `legacy/AssetRentalsScreen.kt` |

`feature/rentalsops` also has task, driver-route, transfer and delivery screens;
it was not changed or independently verified in this increment. Existing
`docs/rentals-wave2-android.md` describes those broader mobile flows.

Service sources read only: `C:\AvagoSvc\docs\rentals-api.md`,
`rentals-api-stage-b.md`, `rentals-api-wave2-stripe.md`,
`src/domain/rental_status.rs`, `src/routes/rentals_api/orders.rs`,
`handovers.rs`, and the payment gate implementation. In particular, mirrors with
`source == "legacy"` cannot use modern rental mutation endpoints.

## Implemented

- Orders now support exact lifecycle status, overdue-return and balance-due
  worklists, intersected with the existing customer/number search.
- Overdue means on-rent/off-rent-requested with an end strictly before now, not
  an unpaid invoice. Oldest overdue first; the screen recomputes every 30 seconds.
- Shown-order balances are summed separately by currency with integer `Money`
  arithmetic. Drafts, quotes, cancelled orders, credits and absent balances are
  not counted as due. Closed unpaid orders remain discoverable. The summary
  explicitly identifies the local snapshot, pending changes and lack of FX conversion.
- Account switches clear rental reads and online read-model gates before the next
  database emits; the order-list payment sheet is keyed by account. Mutations
  require the order to exist in the active account.
- Legacy mirrors remain searchable/readable, but modern lifecycle, payment,
  invoice, swap, handover, post-charge and bulk-return controls are not offered
  from their order detail. Asset-backed mirrors link to the existing legacy UI.
  Repository guards reject core modern writes on legacy mirrors too.
- Lifecycle affordances use current permissions, allow reserved extensions and
  partial pickups, and check remaining handover lines. Manual refund choices
  require the refund permission. Payment success is shown only after local queueing
  succeeds; failures are surfaced rather than logged with a success notification.
- Fixed a confirmed local handover defect: a single-line checkin previously set
  the entire order to returned and released every allocation. Line status now
  updates first; partial returns preserve on-rent/off-rent-requested, and allocation
  changes are restricted to the selected line. Server confirmation stays authoritative.
- A known disabled/unconfigured payment status is explicit and marked last-known;
  no answer is not misrepresented as missing configuration. Gateway controls stay
  gated. No payment configuration was added or changed.
- New UI strings include en-US/de-DE and all seven other existing translated locales.
- **G6 staff gateway actions (code complete, not configured).** `core/network`
  `RentalsApi` gained `charge-saved-card`, `ledger/{id}/capture|release|refund`.
  `domain/GatewayPayments.kt` parses `card_on_file`, `authorizations`, `refundable`
  and `can{charge,refund}` from `GET /orders/{id}/payments`; everything fails closed
  (hidden unless `online_payments_enabled` and the server `can` flag and the local
  permission `rental.payment_charge` / `rental.refund` all allow it; legacy mirrors
  never). Amounts are strict minor units (currency precision, positive, at most the
  hold/refundable amount and Stripe's 8-digit ceiling). Each confirm button names the
  exact amount. Charge sends `client_uuid` + `Idempotency-Key`; refund/capture/release
  send `Idempotency-Key`. The key is kept (in `SavedStateHandle`, scoped by
  account/action/target) across no-answer/408/425/429/5xx outcomes and cleared only on
  a definitive answer; a different amount for an unresolved attempt is blocked with
  the pending amount prefilled. Money actions are bound to the account the user acted
  in (`RentalsOnline.runFor`: nothing is sent after a switch) and the follow-up refresh
  writes only to that account. `requires_action` shares the returned pay link;
  402 declines show the server message. Full captures omit `amount_minor` so the server
  includes its stored surcharge share. No keys, env, onboarding or provider calls.

## Verification (payment increment, 2026-11-16 15:10–15:35 PST)

- `.\gradlew.bat :feature:rentals:assembleDebug :feature:rentals:testDebugUnitTest --console=plain`
  **BUILD SUCCESSFUL**, exit 0: **216 tests, 32 suites, 0 failures/errors/skipped**
  (209 previous + 7 in `GatewayPaymentsTest`).
- `.\gradlew.bat :core:network:testDebugUnitTest :feature:rentals:lintDebug --continue --console=plain`
  network tests **PASS** (10 tests, 3 suites); lint **FAIL with unchanged counts**
  (83 errors, 30 warnings, 2 hints; same pre-existing `MissingTranslation`/
  `LocalContextGetResourceValueCall` debt). Zero lint issues in changed/new files.

## Verification (worklist increment)

With `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'`, from the
isolated worktree:

- `.\gradlew.bat :feature:rentals:assembleDebug :feature:rentals:testDebugUnitTest --console=plain`
  **PASS**; final confirmation exit 0. **209 tests, 31 suites, 0 failures/errors,
  0 skipped** (196 existing tests plus 13 focused tests in `OrderWorklistTest`).
- `.\gradlew.bat :feature:rentals:assembleDebug :feature:rentals:testDebugUnitTest :feature:rentals:lintDebug --console=plain`
  **FAIL on lint only**: 83 errors, 30 warnings, 2 hints. The 83 errors are
  6 `LocalContextGetResourceValueCall` findings in unchanged `ui/PostChargesSection.kt`
  and 77 `MissingTranslation` findings in unchanged `res/values/strings.xml`.
  Final lint XML contains **zero issues in changed/new source files**.
  No lint rule, baseline or test was weakened. An earlier intermediate lint run
  crashed in the experimental-annotation detector; the final run completed analysis.
- Initial shell lacked `JAVA_HOME`; the offline attempt then lacked a cached Room
  plugin. Using the installed JDK and normal dependency resolution resolved both.

Artifacts relative to this worktree:

- `feature/rentals/build/outputs/aar/rentals-debug.aar`
- `feature/rentals/build/reports/tests/testDebugUnitTest/index.html`
- `feature/rentals/build/test-results/testDebugUnitTest/TEST-com.avago.feature.rentals.domain.OrderWorklistTest.xml`
- `feature/rentals/build/reports/lint-results-debug.html` and `.xml`

No emulator/device, full-app APK, live 488-asset account, hosted API, actual
payment or independent approval was verified. The 488-order regression is a
local bounded fixture, not evidence about the user's production fleet.

## Prioritized remaining work

1. **Gateway on a real test account (blocked on configuration, deliberately not done).**
   Once an owner enables Stripe test mode server-side, exercise charge (4242, 3155 SCA,
   9995 decline), hold capture partial/full, release and partial refund on a device.
   Not yet covered on Android: customer card-setup link, autopay toggle, forget card,
   gateway settings/test/reconcile (web-owned admin), and Connect onboarding.
2. Device QA for this increment: status/search interactions, stale/offline
   snapshot presentation, account switching with an open sheet, German/large text,
   legacy navigation, and multi-line partial pickup/return/reconnect.
3. Address the unchanged module lint debt separately; no clean-lint claim is made.
4. Evaluate broader owner catalog/settings/invoice-management and operational
   scale gaps against the parent's roadmap before adding duplicate mobile flows.

Next integration step: review/apply the session's `rental-android.patch` to the
parent-selected Android checkout, preserving its dirty wrapper/nested worktree;
rerun the checks there. The patch was not applied to the original checkout.
