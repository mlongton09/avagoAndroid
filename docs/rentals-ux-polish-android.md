# Rentals UX polish (Android)

Branch `android-rental-ux`, which is now on `master`. This is a UI-layer refactor of every rental screen in
`feature/rentals` and `feature/rentalsops`, plus a shared component kit in `core/ui`.
ViewModels, repositories, sync/outbox, permissions and navigation routes are **unchanged**.

## Design decisions

| Topic | Decision |
| --- | --- |
| Theme | Everything renders inside the existing `AvagoTheme`. Dynamic colour stays **off**, as it is for the rest of the app, so brand and status colours are the same on all devices. Light and dark themes use `MaterialTheme.colorScheme` roles only, with no hard-coded colours. The one exception is the white backdrop behind signature thumbnails, so a black ink stroke stays visible in dark mode. |
| Spacing | `AvagoSpacing` (2/4/8/12/16/20/24 dp). Screen gutters are 16 dp, gaps inside cards are 8–12 dp and gaps between sections are 8–16 dp. |
| Surfaces | `SectionCard` (tonal `surfaceContainerLow`, 16 dp shape) groups related content. List rows are `ElevatedCard` or `ListItem` on `surface`. There is no nested elevation. |
| Status | Use `StatusPill(text, tone, icon)` and `LeadingBadge(icon, tone)`. A status always has an **icon and words**, never colour alone. The six tones (Neutral, Info, Success, Warning, Danger, Accent) map onto container/on-container colour pairs from the scheme, which keeps contrast in both themes. |
| Lists | `LazyColumn` items use stable `key` and `contentType`, plus `animateItem()` where reordering happens. Day groups use `stickyHeader` with `StickyDayHeader` (label and count). Lists support pull-to-refresh (`PullToRefreshBox`) and show `SkeletonList` with shimmer on first load. Empty states use `EmptyStateView` (icon, title, message, optional action). `ErrorBanner` offers retry and states when cached data is shown. |
| Dashboards | `KpiGrid` / `KpiTile` show on rent, available, overdue and due today on the rentals home, and out, due today and overdue on ops home. Tiles are clickable and open the filtered list. Quick book and Scan are `ExtendedFloatingActionButton`s in the thumb zone; they collapse to an icon while scrolling. |
| Detail screens | Each detail screen has a summary header card, sectioned cards and a `BottomActionBar` for the next step (Check out / Check in / Collect payment / Start / Arrive / Complete). Order history uses a `TimelineEntry` timeline. |
| Forms | Forms use `ModalBottomSheet` for pickers and sheets, `imePadding()`, IME actions (Next/Done/Search), inline `supportingText` validation, Material date/time pickers and currency formatting through the existing `MoneyFormatter`/`ChartFormat`. Haptics (`rememberHaptics().success()/error()`) fire on confirmations and failures, and snackbars confirm actions. |
| Handover | `StepIndicator` stepper: Units → Condition → Notes/Sign → Review. Touch targets are at least 48 dp. A review summary is shown before Confirm. Delivery proof (rentalsops) uses a photo grid with 96 dp thumbnails and a 48 dp remove target. |
| Adaptive | On screens ≥ 600 dp (`isWide()`), the schedule switches to a wide layout. KPI tiles and action/photo rows reflow with `FlowRow`. The other screens are single-column and full-width; their cards keep readable line lengths through padding. |
| Accessibility | Headings use `semantics { heading() }` and rows use `mergeDescendants`. Toggles use `toggleable(role = Switch)`. Expandable rows announce Expanded/Collapsed in `stateDescription`. Charts carry a text `contentDescription`. Text is not clipped by fixed heights, and previews include a 200 % font-scale variant. |
| Strings | Every new string is in `values/strings_ux.xml` (English) and `values-de/strings_ux.xml` (formal "Sie"). Other locales fall back to English: the English file has `tools:ignore="MissingTranslation"`. |

### Not restyled on purpose

* `feature/rentals/.../legacy/*`: compatibility screens that are kept only for older deep links.
* The rentals handover has **no in-app photo capture**. Photos come in through the inspection log. The photo grid exists only on rentalsops delivery proof, where capture is already supported.
* `rentalops_counts_a11y` is no longer referenced (KPI tiles provide their own accessibility labels). Lint may report it as unused; it is kept for translators.

## Shared kit (`core/ui/components`)

* `Surfaces.kt`: `SectionCard`, `CardDivider`, `KpiTile`/`KpiGrid`, `BottomActionBar`, `StickyDayHeader`.
* `States.kt`: `EmptyStateView`, `ErrorBanner`, `shimmer`, `SkeletonList`, `rememberHaptics`.
* `StatusPill.kt`: `StatusTone`, `toneColors`, `StatusPill`.
* `Rows.kt`: `LeadingBadge`, `cardListItemColors`, plus previews.
* `Progress.kt`: `StepIndicator`, `TimelineEntry`.

## Screens changed

**Rentals:** Home/dashboard, Fleet, Schedule/availability, Orders list and order detail (payments, history), Asset detail, Quick book, Checkout/check-in handover, Customers, Scan, Readiness, Swap.

**Rentals ops:** Ops home, Driver task list, Delivery task (proof), Transfers and create transfer, Assign list and assign line, Statement, AR aging, Notification settings, plus the ops entry cards in the nav graph.

## Manual QA checklist (device)

- [ ] Light and dark theme on every screen: check pill and banner contrast, and that signature thumbnails are readable.
- [ ] Font scale 200 %: KPI values, amounts, status pills and bottom-bar buttons wrap and are not clipped.
- [ ] TalkBack:
  - [ ] List rows read as one item (title, status, amount).
  - [ ] Pills read their text.
  - [ ] The aging chart reads its bucket values.
  - [ ] Settings switches read as "Switch, on/off".
  - [ ] Expandable aging rows announce Expanded/Collapsed.
- [ ] Pull-to-refresh on fleet, orders, schedule and tasks triggers sync and the indicator dismisses. Turn on airplane mode and check that the error banner shows with Retry.
- [ ] Swipe-to-action on the rentals home today list (the only swipe surface). Check that the confirmation snackbar appears.
- [ ] Sticky day headers stay pinned while scrolling: today, tomorrow, dated groups, unscheduled.
- [ ] Quick book: date/time pickers work, inline validation shows, the IME Next/Done chain works, the sheet is not covered by the keyboard, success haptic fires and the snackbar shows.
- [ ] Handover:
  - [ ] Stepper Back/Next keeps the entered state.
  - [ ] Review summary values match the inputs.
  - [ ] The signature is captured.
  - [ ] Confirm haptic fires.
- [ ] Delivery proof:
  - [ ] Camera photo appears in the grid.
  - [ ] Removing a photo works.
  - [ ] The signature thumbnail shows.
  - [ ] Complete and Fail flows work.
  - [ ] Navigate and Call intents open.
- [ ] Scan: camera, NFC and manual entry all work. Check the permission-denied state.
- [ ] Transfers: the scan frame shows, manual entry with IME Search works, and Create shows its spinner and a snackbar.
- [ ] Statement:
  - [ ] Customer search works.
  - [ ] Balance and credit pill are shown.
  - [ ] PDF share and e-mail work, and the button disables while sending.
  - [ ] Offline shows a stale banner with Retry.
  - [ ] A 403 shows a banner without Retry.
- [ ] AR aging: refresh icon and spinner, skeleton while loading, chart, table toggle, and customer expand with bucket pills.
- [ ] Notification settings:
  - [ ] Banner when system notifications are off, with Open settings.
  - [ ] Offline banner with Retry.
  - [ ] Device-only banner.
  - [ ] Per-toggle spinner while saving.
- [ ] Tablet and foldable (≥ 600 dp): wide schedule layout, KPI reflow, rows on the other screens are not uncomfortably stretched, and rotation and fold/unfold keep state.
- [ ] German locale: formal "Sie" wording, and long words wrap in pills and buttons.
