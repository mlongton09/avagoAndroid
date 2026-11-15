# Rentals central marketplace — Android

This doc covers what the Android app implements from §10 "Mobile scope" of the AvagoSvc contract
`docs/rentals-api-marketplace.md` (origin/master).

- **Web reference:** AvagoWeb `src/app/(site)/marketplace/**`, `/rentals/meetups` and
  `/app/settings/rentals/marketplace`.
- **iOS counterpart:** `docs/rentals-marketplace-ios.md` in the iOS repo.

UI follows `rentals-ux-polish-android.md` and `rentals-ux-audit-android-pass2.md`:

- the shared component kit;
- one primary action per screen or card;
- a refresh-failed banner over cached data;
- touch targets are at least 48 dp;
- previews in light, dark, 200 % font and German.

All new text is in English and formal German ("Sie"):

- `feature/rentals/src/main/res/values{,-de}/strings_mkt.xml`;
- small additions to `feature/auth` strings and `feature/rentalsops` `strings_w2.xml`.

## Where things live

| Area | Location |
|---|---|
| HTTP API | `core/network/.../rentals/MarketplaceApi.kt`. Public, renter-session and owner endpoints. The renter bearer is sent only on renter calls. |
| Domain | `feature/rentals/.../marketplace/domain/`. `MktModels.kt` holds the tolerant JSON parsers. `MktLogic.kt` holds radius/unit, sort, dates, slot validation, query building and distance formatting. `MktBodies.kt` holds the request bodies. |
| Data | `marketplace/data/`. `MarketplaceRepository` covers renter calls, the encrypted renter session and the place lookup. `MarketplaceOwnerRepository` covers owner settings, listings, the meet-up inbox and blocks. `MarketplaceModule` provides DI and the device location provider. |
| Renter UI | `marketplace/renter/`. Search, listing detail, Reserve, Propose meet-up, email sign-in, My requests, Profile. |
| Owner UI | `marketplace/owner/`. Marketplace settings, listed items, meet-up inbox, meet-up detail, blocked renters. |
| Nav | `marketplace/nav/`. `MarketplaceRoutes` (all routes start `rentals/mkt/`), `MarketplaceNavGraph` and the drawer items. |
| Push | `feature/rentalsops/.../push/RentalPushRouter.kt`, `RentalOpsNotifier.kt`, `RentalOpsPushHandler.kt` |
| App wiring | `app/.../MainActivity.kt` (deep links), `nav/MainScaffold.kt` (guest chrome, drawer), `nav/AvagoNavHost.kt` (graph and sign-out redirect exemption), `AndroidManifest.xml` |

## Entry points

- **Staff sign-in screen:** a "Find rentals near you" text button opens the marketplace without a
  staff account.
- **Side menu:** "Find rentals" is always shown, for staff and guests alike.
- **Owner items in the side menu:**
  - "Marketplace meet-ups", with a badge for pending requests;
  - "Marketplace settings".

  They are shown only when rentals are enabled and the user has `rental.meetup_respond` or
  `rental.marketplace_manage`.
- **Deep links:** `https://avagomate.com` or `https://www.avagomate.com`, with these paths:

  | Path | Opens |
  |---|---|
  | `/marketplace` | Search |
  | `/marketplace/{id}` | Listing detail |
  | `/marketplace/requests` | My requests |
  | `/marketplace/auth?token=…&next=…` | Magic-link sign-in. The token is redeemed, then the app goes to the sanitized `next` route. |

  The intent filter uses `autoVerify`, but AvagoWeb serves no `/.well-known/assetlinks.json`
  yet. Until it does, Android shows the app chooser instead of opening the app directly.
- **Push:** `rental.marketplace_meetup` (category `RENTAL_MEETUP_REQUEST`, data `meetup_id` or
  `entity_type=rental_meetup` with `entity_id`) opens the owner meet-up detail. If the id is
  missing or unsafe, it opens the inbox.
  - The notification uses its own channel ("Marketplace meet-up requests", high importance).
  - The event can be toggled in Rental ops → notification settings.
  - Renters get no push. The contract notifies them by email.

## Renter features

- **Location:**
  - "Use my location" uses the Fused Location Provider for a single current fix. A rationale
    dialog appears before the runtime permission.
    - Renter search asks for coarse location only, at balanced accuracy.
    - Owner "use current location" asks for fine location, at high accuracy.
    - No third-party geocoding and no Maps SDK are used.
  - Places are looked up through the server endpoint, typing a town or postal code. The result is
    a list of matches with the approximate area.
- **Search filters:**
  - Radius chips 10/25/50/100/200, in km, or in miles for US/LR/MM regions.
  - Keyword, category, price range, dates, fulfilment (pickup, delivery, meet-up).
  - Sort: distance, price, soonest, relevance. Relevance applies only when a keyword is set.
- **Search results:**
  - Cursor paging.
  - Result cards show the photo, price, approximate distance and next available date.
  - Empty results offer "Search a wider area".
- **Listing detail:** photos, description, pricing, approximate area (never the exact address),
  an availability calendar by month, policies (deposit, cancellation, ID, minimum age) and the
  owner card.
- **Reserve** (hold or quote mode, per the owner's settings):
  - The date range and quantity are validated.
  - Retries are idempotent.
  - Online-payment wording appears only when `online_payments` is true. Otherwise an in-person
    payment note is shown.
- **Propose meet-up:**
  - 1–3 slots inside the owner's meet-up windows, plus a message.
  - If the server returns `slot_unavailable`, the windows are reloaded and stale selections are
    dropped.
  - A sent request can't be sent twice.
- **Sign-in:** email magic link per the contract.
  - The renter session token is stored in `EncryptedSharedPreferences`
    (`avago_marketplace_session.xml`).
  - That file is excluded from cloud backup and device transfer.
- **My requests:** status of reservations and meet-ups. Renters can accept or decline an owner's
  counter-proposal.
- **Profile:** name and phone; sign out.

### Guest mode

Users who are not signed in to a staff account can use every public `rentals/mkt/*` route.

- On those routes the app hides the drawer, bottom bar, FAB, sync spinner and sync banner. The
  sync gate never opens without a staff account, so leaving them would show an endless spinner.
- The sign-out redirect skips public marketplace routes.

## Owner features

| Feature | Permission | Notes |
|---|---|---|
| Marketplace settings | `rental.marketplace_manage` | Opt-in toggle; location from current position or manual lat/lng; public area label; fulfilment modes; weekly meet-up windows and blackout dates; reserve mode. Form validation is in `MktSettingsForm`. |
| Listed items | `rental.marketplace_manage` | Per-item "listed on marketplace" flags. |
| Meet-up inbox and detail | `rental.meetup_respond` | Filters: open, accepted and all. Actions are accept a slot, counter with 1–3 slots, decline with a reason, or block the renter. |
| Blocked renters | `rental.meetup_respond` | Unblock. |

Owner meet-up times are shown in the device time zone, because the owner payload has no time zone.

## Persistence

There is no Room schema change.

- The owner inbox and settings use the existing `RentalsOnline` last-known-good cache, so a
  refresh-failed banner can show over cached data.
- Renter data is fetched live.

## Tests

Tests are in `feature/rentals/src/test/.../marketplace/`.

| Suite | Covers |
|---|---|
| `MktLogicTest` | Radius units by region, clamping and widening; distance formatting; query building (dates as local midnight to midnight after the last day, as the contract specifies); slot validation |
| `MktModelsTest` | Tolerant parsing of search results, listings, meet-ups and errors |
| `MktViewModelsTest` | Meet-up VM: load, pick, `slot_unavailable` reload, no resend, dates required. Sign-in VM: email validation, `next` sanitizing, token success/network/invalid, existing session. Route mapping and parity with rentalsops literals. Settings form round-trip. |

`feature/rentalsops` `RentalPushRouterTest` adds meet-up routing and rejects unsafe ids.

The owner ViewModels have no unit tests. `PermissionsManager` is a final class backed by a
`Context`, and mocking is not available in the module.

## Device QA checklist

1. Signed out, tap "Find rentals near you" on sign-in. You get search with no drawer or bottom
   bar, and Back returns to sign-in.
2. Tap "Use my location":
   - the rationale shows first;
   - denying keeps manual place entry working;
   - allowing fills "Near you".
3. Type "100 Mile House" or a postal code and pick a match. Results are sorted by distance.
4. Change the radius chip. Check km on a CA device and miles with the US region.
5. Apply keyword, category, price, dates and fulfilment filters. The filter count badge updates.
   Scroll to load the next page.
6. Open a listing:
   - the photos page;
   - the area is approximate;
   - the calendar month changes;
   - the policies and owner card show.
7. Reserve while signed out. You are sent to email sign-in, and the magic link opens the app and
   returns to Reserve.
8. Reserve on a listing with `online_payments=false`. No online-payment text appears.
9. Propose a meet-up:
   - no more than 3 slots can be picked;
   - slots stay inside the owner windows;
   - the message is sent;
   - the request shows in My requests.
10. Owner counters the meet-up. As the renter, accept or decline it in My requests.
11. Staff owner with permissions:
    - turn the marketplace on;
    - set the location from the current position;
    - list items.
12. Owner device receives a push for a new request. Check the channel, then tap the notification.
    The meet-up detail opens.
13. Owner accepts, counters, declines and blocks. The renter appears under Blocked renters and
    can be unblocked.
14. Repeat in German, dark mode, 200 % font and TalkBack.
15. Open `https://www.avagomate.com/marketplace/{id}` from another app. The chooser lists Avago,
    and the listing opens.
16. Airplane mode on the owner inbox. The refresh-failed banner shows over cached data.

## Deviations and risks

- **No `assetlinks.json` on AvagoWeb:** App Links do not auto-verify yet.
- **No `avago://marketplace/...` scheme:** iOS has it. Android uses only https links.
- **Locale-prefixed web paths:** these don't exist on the web (`(site)/marketplace` has no
  `[lang]` segment), so Android doesn't handle them.
- **Location needs Google Play services:** the Fused Location Provider is not available on
  devices without them. Manual place entry is always available.
- **Owner meet-up times** use the device time zone.
