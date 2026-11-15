# Rentals UX audit — Android, pass 2

Scope: every rentals screen in `feature/rentals` (core flows, not `legacy/*`) and
`feature/rentalsops`, reviewed as a demanding product designer against Material 3
(expressive) guidance and Google's own apps (Calendar, Contacts, Files, Wallet) as
the bar. Pass 1 (`docs/rentals-ux-polish-android.md`) made the screens consistent
and accessible. Pass 2 makes them *calm*: one obvious next step per screen,
no duplicated numbers, empty space instead of empty boxes, photos instead of icons,
and honest feedback when a refresh fails.

IDs mirror the iOS pass-2 audit (`C:\avago\docs\rentals-ux-audit-ios-pass2.md`)
where the same issue exists, so the two platforms can be checked side by side.
Status: ✅ fixed in this pass · ➖ deliberately kept (reason given) · 🔎 device QA.

## Cross-cutting (X)

| ID | Issue | Fix |
| --- | --- | --- |
| X1 | Pull-to-refresh / "Sync now" silently gives up after 8 s; the user can't tell a failed refresh from "nothing new". | `SyncRefresh.failed` → `ErrorBanner` "Couldn't refresh. Showing saved data." with **Retry** on home, fleet, schedule, orders, customers. ✅ |
| X2 | Rental units are shown as generic tonal icons although most assets already have photos in the shared photo pipeline. | `UnitThumbnail(assetId)` (Coil, primary photo → lowest sort order, falls back to the status badge) on home agenda, schedule, fleet, order lines, asset hero, handover. ✅ |
| X3 | Rare and destructive actions sit next to the primary action (bottom bars of 3 buttons, "Disable rentable" as a plain menu item). | Shared `OverflowMenu` with a divider-separated destructive group in error colour; destructive items always confirm. ✅ |
| X4 | On tablets / landscape (≥ 600 dp) cards and forms stretch edge to edge (line length > 120 chars). | `readableWidth()` modifier: content centred at max 720 dp on every list/detail screen. ✅ |
| X5 | Long histories render in full (customer orders, asset bookings), pushing primary content off-screen. | "Show 3, then **Show all (N)**" row (`ShowAllRow`), state saved across rotation. ✅ |
| X6 | Hard-coded spacings (`16.dp`, `56.dp` divider insets, `144.dp` FAB clearance) and sizes scattered through screens. | New tokens `AvagoSpacing.listInset` (divider inset next to a 40 dp leading element), `fabClearance`, `thumbnail`, `thumbnailLg`, `readableMaxWidth`. ✅ |
| X7 | Section headers use `labelLarge`/onSurfaceVariant while card titles use `titleSmall` — two competing heading levels on the same screen. | ➖ Kept: the shared `SectionCard` title is the single in-card heading; standalone `SectionHeader` is only used for date groups. Verified consistent. |
| X8 | German strings ~30 % longer: two-button bars at 200 % font truncate ("Zahlung erfassen", "Rückgabe annehmen"). | Bars now have at most one weighted primary + one secondary; labels wrap to 2 lines (`maxLines = 2`), no ellipsis on primary verbs. ✅ 🔎 |
| X9 | Swipe actions exist only on home agenda rows; nowhere else, and not consistently mirrored for TalkBack. | Swipe + TalkBack custom actions on customers (Book / Call / Email) and orders (Record payment, permission-gated). ✅ |

## Home (H)

| ID | Issue | Fix |
| --- | --- | --- |
| H1 | Three competing entry points for the same job: small scan FAB, "Scan check-out" tonal button, "Check in" outlined button, plus the Quick book FAB. | Single primary = **Quick book** extended FAB. One "Scan" row of two equal tonal tasks (Check out / Check in); scan FAB removed. ✅ |
| H2 | KPI grid followed by a fleet status chip breakdown repeats the same counts (On rent / Available / Overdue twice). | Breakdown removed from home (still used by the asset-list header card). ✅ |
| H3 | Overdue / Going out / Due back always render as three cards, each with "Nothing …" text — on a quiet day the screen is a wall of empty boxes. | Empty groups are hidden; when all are empty one **All clear** card ("Nothing due today") is shown. ✅ |
| H4 | Agenda rows lead with a type icon; the unit is not recognisable at a glance. | Unit photo thumbnail with the type icon as fallback. ✅ |
| H5 | 144 dp bottom padding hard-coded for FAB clearance. | `AvagoSpacing.fabClearance`. ✅ |
| H6 | Refresh failure invisible (see X1). | ✅ |
| H7 | Section header "Today" + per-card headings "Overdue · 2" read as two heading levels with a count glued to the title. | Count moved into the card's trailing pill; "Today" header kept. ✅ |

## Orders list & order detail (O)

| ID | Issue | Fix |
| --- | --- | --- |
| O1 | **Bug:** the bottom bar uses `actions.first()` as the primary, so an on-rent order's primary is *Extend* instead of *Check in*. | Explicit priority: check out › check in › close › reserve › hold › quote. ✅ |
| O2 | Up to four lifecycle buttons + Collect payment compete in the bar; Cancel / No-show sit beside the primary. | Bar = one filled next step + (if balance due) tonal **Collect payment**. Everything else in the top-bar overflow; Cancel / No-show grouped at the bottom in red with the reason dialog. ✅ |
| O3 | Totals appear twice (summary card *and* payments card), and "Record payment" appears both in the bar and as a section-header button. | Single money hero (Total · Paid · Balance + deposit). Payments header button removed. ✅ |
| O4 | Empty Payments and Invoices cards render with "No payments yet" placeholders. | Hidden when empty; Create invoice moves to the overflow. ✅ |
| O5 | Line rows show a generic icon. | Unit thumbnail. ✅ |
| O6 | Orders list: no shortcut to take a payment on an order with a balance. | Leading swipe **Record payment** (+ TalkBack action) when balance > 0 and the user holds `rental.payment_record`. ✅ |

## Fleet (F)

| ID | Issue | Fix |
| --- | --- | --- |
| F1 | Each card carries badge, name, class, pill, timing, reasons, a divider and up to four buttons — the list reads as a wall of buttons. | Card = thumbnail · name · class · timing · status pill, **one** contextual action (Hand over › Book › Readiness), the rest in a per-card overflow. ✅ |
| F2 | Name in `titleMedium` bold competes with the status pill. | `titleSmall` name, `bodyMedium` supporting line. ✅ |
| F3 | Refresh failure invisible. | X1. ✅ |

## Schedule (S)

| ID | Issue | Fix |
| --- | --- | --- |
| S1 | Rows lead with time/type overline and unit headline; the order·customer line repeats on every row. | Title = unit (or "Unassigned"), subtitle = time · kind · order · customer, unit thumbnail. Same row on home. ✅ |
| S2 | Refresh failure invisible. | X1. ✅ |
| S3 | Gantt chart colours are hard-coded. | ➖ Kept: chart palette is defined by the chart style guide and checked for both themes. |

## Customers (C)

| ID | Issue | Fix |
| --- | --- | --- |
| C1 | No quick way to book, call or e-mail a customer from the list. | Swipe right = **Book** (hidden when *do not rent*), swipe left = **Call** / **Email** (when present); same as TalkBack custom actions. Book opens Quick book with the customer pre-selected. ✅ |
| C2 | Customer detail lists every past order. | Show 3, then **Show all (N)**. ✅ |
| C3 | Refresh failure invisible. | X1. ✅ |

## Asset rental detail (A)

| ID | Issue | Fix |
| --- | --- | --- |
| A1 | Header is a plain status card; no photo. | Hero with 64 dp unit photo, name, class, status pill. ✅ |
| A2 | Bottom bar holds up to three buttons. | One primary (Hand over › Readiness › Book); the rest in the overflow. ✅ |
| A3 | "Disable rentable" is a plain overflow item, not red, no confirmation. | "Stop renting this unit" in the red destructive group with a confirm dialog. ✅ |
| A4 | All bookings listed. | Show 3, then Show all (N). ✅ |

## Handover (HO)

| ID | Issue | Fix |
| --- | --- | --- |
| HO1 | Condition step shows a selected inspection by name only; photos taken are invisible until the review step / server. | Condition photo strip (horizontal, 72 dp tiles) with an **Add photo** tile (camera; starts an inspection first if none is selected). ✅ |
| HO2 | Unit rows in the units step and review show generic icons. | Unit thumbnails. ✅ |
| HO3 | Multi-step Back. | ➖ Already correct (system Back + Back button step back; Cancel on step 1). |

## Quick book (Q)

| ID | Issue | Fix |
| --- | --- | --- |
| Q1 | No way to arrive with a customer selected (from the customer list / detail). | Route arg `customerId` pre-selects the customer. ✅ |
| Q2 | Bottom bar: overflow + Hold + Reserve = three targets. | ➖ Kept: Reserve is the single filled primary, Hold is the one secondary, Quote/Draft in overflow — matches the iOS rule. |
| Q3 | Date/time buttons render as `Label: date` + time, wrapping awkwardly at 200 %. | 🔎 Uses FlowRow-free Row; verify on device at 200 % — acceptable in previews. |

## Readiness, Scan, Swap

| ID | Issue | Fix |
| --- | --- | --- |
| R1 | Readiness: three decision buttons (Pass / Fail / Waive) share the bar equally. | ➖ Kept: these are mutually exclusive outcomes, not primary/secondary; Waive only appears with permission. |
| SC1 | Scan: camera framing and manual entry already pass 48 dp and IME checks. | ➖ No change. |
| SW1 | Swap: two-step flow with real Back. | ➖ No change. |

## Rentals operations (OP)

| ID | Issue | Fix |
| --- | --- | --- |
| OP1 | Ops home: hard-coded `56.dp` divider inset; refresh spinner is a fixed 1.2 s fake. | Token inset. Spinner kept (ops sync is fire-and-forget, no completion signal) ➖. ✅ |
| OP2 | Ops lists stretch on tablets. | `readableWidth()` on ops home. ✅ |
| OP3 | Task / delivery / transfers / finance screens. | ➖ Reviewed: single primary per task step, rejected-action card with Retry/Discard, 48 dp targets — no pass-2 change needed. |

## Accessibility & resilience checklist (verified per screen)

- Touch targets ≥ 48 dp (all new controls use `MinTouch` / M3 defaults).
- Every icon-only control has a content description; thumbnails are decorative
  (`contentDescription = null`) because the row text names the unit.
- Destructive actions: red text + icon + confirmation dialog (colour is never the only cue).
- Swipe actions duplicated as TalkBack custom actions.
- Dark mode: tone colours from `toneColors()`; thumbnails have a tonal placeholder.
- 200 % font / German: previews at `fontScale = 2f` and `locale = "de"`; labels wrap.
- Tablet: `readableWidth()` centres content at ≤ 720 dp.
