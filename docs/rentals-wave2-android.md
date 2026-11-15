# Rentals wave 2 — Android mobile scope

This doc covers what the Android app implements from the "Mobile scope" sections of the AvagoSvc
wave-2 contracts. The contracts are in `docs/` on AvagoSvc `origin/master`:

| Contract | Doc |
|---|---|
| G1 | `rentals-api-wave2-esign-scan-meter.md` |
| G2 | `rentals-api-wave2-credit-collections-bank.md` |
| G3 | `rentals-api-wave2-kits-subrent-longterm.md` |
| G4 | `rentals-api-wave2-pricing-reports.md` |
| G5 | `rentals-api-wave2-routing-api-webhooks.md` |
| G6 | `rentals-api-wave2-stripe.md` |

UI follows `rentals-ux-polish-android.md` and `rentals-ux-audit-android-pass2.md`:

- one primary action per card;
- the shared status pills and section cards;
- a refresh-failed banner over cached data;
- empty sections are hidden;
- touch targets are at least 48 dp;
- previews in light, dark, 200 % font and German.

All new text is in `strings_w2.xml` in English and formal German ("Sie"). The `es`, `fr`, `it`,
`nl`, `no`, `pt` and `sv` locales fall back to English, like `strings_ux.xml`.

## Persistence

There is no Room schema change. Online read models (signatures, meter, credit check, payments, KPI,
buyout) are cached as `RentalEntity.META` rows through `RentalsOnline`, with the same per-account
database and the same last-known-good behaviour.

Offline writes go through the existing outboxes:

- **feature:rentals:** the new `ESIGN` and `BULK_RETURN` kinds.
- **feature:rentalsops:** the task-event queue, which now drives G5 check-in.

## What was implemented

### G1 — e-sign, scan, meter (feature:rentals)

#### In-person signature

- The order's **Agreement** card shows the signature request status.
- Signers marked as "in person" can sign on the device in `SignAgreementScreen`. The flow is: consent, then drawing the signature, then the typed name.
- Captures are queued offline under their `client_uuid` (`ESIGN` outbox) and replay with the same id.
- A capture is dropped only when the server answers 409 or 410, and a notice is shown when that happens.
- A signer that is signed locally but still pending does not block checkout.

#### Checkout gates

Checkout can return these blocks:

- `409 signature_required`;
- the meter gates;
- the G2 credit and document blocks.

Each block appears in a blocking sheet with the server message. When the user has the permission,
the sheet also offers an override with a reason.

Overrides are **accumulated** (`HandoverGates.patch`). A retry after a second gate still carries
every earlier override together:

- `override_signature`
- `meter_override`
- `override_credit`
- `override_docs`
- `override_reason`

#### Meter entry

- Meter entry appears at checkout and check-in when the class tracks a meter.
- The screen shows the last reading and source and validates the input.
- A check-in reading below the checkout reading needs an override and a reason.
- A checkout reading below the last one is only a warning.
- An optional meter photo is uploaded as `meter_photo_file_id`.

#### Scan

- Label short codes (`U…` for units, `P…` for bulk parts) are resolved through `/scan/resolve`.
- A unit opens the unit or its current order. A bulk part is shown as a part result.
- When offline, the screen shows a hint and falls back to the local lookup.

### G2 — credit, collections, documents

#### At checkout (feature:rentals)

The **Credit** card on the order calls `credit-check?stage=checkout` and shows:

- the account status and hold reason;
- the blocks, with server messages;
- the exposure (available credit, and the balance after this order);
- required documents that are missing or expired.

Block codes map to localized labels, and the server message is used as a fallback.

#### Document photos

- ID / licence / COI photo capture uses `DocumentPhotoRow`. It is available on the customer screen and from the Credit card.
- Photos are uploaded as customer documents of the chosen kind.

#### Push and overdue alerts (feature:rentalsops)

New channels:

- **RENTAL_CREDIT:** `rental.doc_expiring` and `rental.credit_application`.
- **RENTAL_COLLECTIONS:** `rental.dunning_hold`.

Each event has a settings toggle. Customer pushes open the P1 customer screen.

Overdue alerts use the existing `rental.overdue` push.

### G3 — kits, sub-rent, long-term (feature:rentals)

- **Kits:** on the order, kit components are grouped under their kit header, and orphan components are grouped without a header. Kit headers are never handed over themselves.
- **Bulk lines:** check-in records returned / damaged / lost counts against the outstanding quantity. The client checks mirror the server's 400 codes. Returns are queued through `BULK_RETURN`.
- **Sub-rented units:** marked with a "Sub-rented" badge. A `rental.subrental_return_due` push goes to the RENTAL_SUBRENTAL_DUE channel.
- **Long-term / rent-to-own contracts:** shown read-only (rate, billed through). Rent-to-own contracts also show the buyout quote and the equity statement PDF.

### G4 — pricing and KPI (feature:rentals)

- Pricing-rule adjustments are listed under each line from `pricing.result.rules.applied[]`.
- The 30-day KPI card on rentals home shows utilization, revenue, checkouts, units on rent, open or expiring quotes and AR overdue. Each value shows its trend against the previous period.
- The KPI card is hidden when it has no data.

### G5 — driver route (feature:rentalsops)

#### My route screen

- **Data and entry points:** the screen reads `routing/my-route`. Drivers reach it from the ops home and the task list.
- **Stop order:** stops are listed in `route_seq` order, with:
  - ETA, or the planned time;
  - the time window;
  - the address;
  - status and pending pills;
  - a "Next stop" highlight.
- **Actions per stop:**
  - One primary step: **On my way**, **Arrived** or **Complete**. Complete opens the stop for proof.
  - **Navigate** uses `geo_uri`, then `maps_url`. It is hidden when the stop has no coordinates.
  - **Call**.
- **Offline fallback:** if the route can't be loaded, the screen uses today's cached route first. If there is none, it rebuilds the route from the device's own tasks and shows a banner. A cache from an earlier day is never shown as today.

#### Check-in

- Driver check-ins post `POST …/check-in` with:
  - `client_uuid` (the outbox row id);
  - `status`;
  - `at`;
  - `lat` and `lng`, sent only as a pair;
  - `accuracy_m`;
  - proof photos and signature.
- Older servers, which answer with an uncoded 404 or a 405, fall back to the stage C task action.

### G6 — Stripe online payments (feature:rentals)

- All online-payment UI is hidden unless `/payments/status` reports `online_payments_enabled: true`. That UI covers:
  - the order payments card;
  - pay links;
  - "Request payment".
- Stripe is never required.
- When enabled, the card lists payment requests and their status (open, paid, requires action, expired). Open links can be shared, and users with permission can create and send a balance request.
- Payment pushes go to the invoice channel.

## Deviations and deferred items

- **G5 location pings and the foreground service (§7) are not implemented.** The app does not send background location pings while `tracking` is true. That needs:
  - a location foreground service;
  - `ACCESS_BACKGROUND_LOCATION` and the Play policy declaration;
  - battery testing.

  The route view and check-ins work without pings. `ping_interval_seconds` and `tracking` are parsed for a follow-up.
- **Check-in conflicts are not dropped silently.** The doc says to drop queued `client_uuid_reused` and `invalid_transition` items. The app keeps them as visible conflict rows with Retry and Discard, which is the established outbox rule. An `invalid_transition` that is already applied (`details.from`) is still resolved automatically.
- **Push bodies for the new G2/G3 events** use the server's text. Only titles are localized.
- **Field names are not verified against a live server.** The KPI, meter and payment-request field names follow the contract docs.

## Device QA checklist

Test on a phone in light and dark mode, at 200 % font size, and in German.

### E-sign

- [ ] Create an order with an in-person signer and open **Agreement → Sign on this device**. Complete consent, draw the signature, type the name, and submit.
- [ ] Repeat the signing in airplane mode. The capture should be queued, checkout should not be blocked by that signer, and the capture should sync after you reconnect.

### Checkout gates and meter

- [ ] Check out without a signature. A `signature_required` sheet should appear.
  - With the override permission, enter a reason and override.
  - Then trigger a credit block on the same retry. The final request should contain both overrides.
- [ ] Do a meter check-out and check-in.
  - Enter a reading below the last one: you should only get a warning.
  - At check-in, enter a reading below the checkout reading: an override and a reason should be required.
  - Attach a meter photo.

### Scan

- [ ] Scan a `U…` label. It should open the unit or its order.
- [ ] Scan a `P…` label. It should show a part result.
- [ ] Scan offline. You should see the offline hint and a local lookup.

### Credit and documents

- [ ] Use a customer on hold or over the limit. The Credit card should show the block messages, and checkout should be blocked.
- [ ] Use a customer with an expired or missing document. Capture an ID photo from the card, then refresh: the document should show as present.

### Kits, bulk and contracts

- [ ] Open a kit order. Components should be grouped under the kit.
- [ ] On a bulk line, check in returned, damaged and lost counts. A count above the outstanding quantity should be rejected inline.
- [ ] Open a sub-rented unit. It should show the badge.
- [ ] Open a rent-to-own contract. It should show the buyout quote and open the equity PDF.

### Pricing and KPI

- [ ] Line adjustments should be listed on the order.
- [ ] The KPI card should show on rentals home. Values should be empty while the server is backfilling, and the card should be hidden when there is no data.

### Online payments

- [ ] With `online_payments_enabled=false`, the app should show no payment card, pay links or request button anywhere.
- [ ] With it enabled, create a request and share the link. The status should update after payment.

### Driver route

- [ ] As a driver, open **My route**. Stops should be in order, and the next stop should be highlighted.
- [ ] Tap **On my way** and then **Arrived** while offline. Both should be queued with pending pills, and the route should refresh after sync.
- [ ] A stop without coordinates should show no Navigate button. Navigate on a stop with coordinates should open the maps app.
- [ ] Turn on airplane mode before the first load of the day. The device fallback banner should appear.

### Push

- [ ] Send a test `rental.doc_expiring`, `rental.dunning_hold` and `rental.subrental_return_due`. Each should arrive on its own channel. A customer push should open the customer, and the settings toggles should mute each event.
