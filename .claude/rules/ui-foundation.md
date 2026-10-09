---
paths:
  - "app/src/**/ui/components/**"
  - "app/src/**/ui/theme/**"
  - "app/src/**/ui/root/**"
---

### UI: Foundation only

There is no Material dependency. `EtaTheme` provides four token sets through static
CompositionLocals — `colors`, `typography`, `shapes`, `spacing` — read as
`EtaTheme.colors.accent`, the same shape as `MaterialTheme`. Which values fill the
tokens is the chosen **design** — see *Step 30*.

Base components in `ui/components/` stand in for the Material ones: `EtaText` (on
`BasicText`), `EtaTextField` (on `BasicTextField`), `EtaSurface`, `EtaButton`,
and `EtaScreen` in place of `Scaffold`. Input without Material pickers is covered
by `EtaStepper`, `EtaTimePicker`, `EtaDurationPicker`, `EtaChoice`,
`EtaWeekdayPicker` and the `EtaField` label wrapper; `EtaTabBar` carries the
tabs, `EtaExpander` the heading-count-chevron folds, and `EtaTrashButton` is a
wastebasket drawn on a `Canvas` in six strokes — there is no icon set, and one file
of vector assets for one glyph would be worse than drawing it. Build new UI from
these rather than reaching for raw `Box`/`BasicText` per screen.

`EtaTimePicker` is a hand-built clock dial in `androidx.compose.ui.window.Dialog`
(compose-ui, not Material): tap the field, pick the hour, then the minute, the way
Android's own picker works. It is one `Canvas` — numbers, hand and knob are all
drawn — so hit testing and drawing share one coordinate system. In hour mode it
carries two rings, 1–12 outside and 13–24 inside, chosen by distance from the
centre; minutes snap to five. `EtaTimePickerDialog` was extracted from it so a list
row can offer a clock without a full picker field swamping the list. Durations keep
the `EtaStepper`, since a length is not a point on a clock face. There is **no date
picker**, which is why a date is always a day stepper with ±1-week buttons.

Four tokens carry meaning, not taste: `shapes.draggedBlock` (rounded) versus
`shapes.fixedBlock` (square) is how the planner shows whether a block was placed by
hand or came from a recurrence or the calendar; `colorOf(category)` maps
Fokus/Nebenbei/Achtsam so no screen hand-rolls that mapping; `colors.calendar` —
ochre — is worn by imported appointments in place of a category colour, because what
it says is not "this is Fokus" but "this came from outside and is not yours to
move", and it is warm where all three category colours are cool; and the soft pair
`colors.dangerSoft` / `successSoft` exist as tokens rather than `danger`/`success`
with an alpha, so the dark theme picks its own wash instead of getting a washed-out
red or green over near-black.

Press feedback is a scale-and-fade driven by an `interactionSource` with
`indication = null`; ripple lives in the Material artifacts this project omits.

### Navigation: tabs and flows

`ui/root/EtaApp.kt` decides what the app opens on: the questionnaire until
`user_setup` has a row, the dashboard afterwards. `RootDestination.Loading` is a
state of its own so the questionnaire does not flash on every cold start. Past the
setup, `MainScaffold` splits everything into **tabs** and **flows**, and that split
is the point:

- A **tab** is a place you can always get back to — Heute, Listen, Verträge,
  Erinnerungen, Growth-Tasks, Belohn-o-mat, Einstellungen, in that order since step 35 — so `EtaTabBar` stays put beneath it.
- A **flow** is something you are in the middle of: a planning phase, the
  concretizing step, the holiday editor. It covers the bar and leaves by finishing
  or by going back.

Mixing them is what made the earlier flat `AppScreen` enum awkward: it made the
evening reevaluation a peer of the dashboard, which it is not. Back leaves a flow
first, then returns to the first tab. Still no navigation library — two pieces of
state, `tab` and `flow`, do the whole job, and both are `rememberSaveable` by enum
name so a rotation does not throw the screen away. Reach for a library when deep
links or a real back stack arrive, not before.

**The bar scrolls sideways and every label is drawn whole.** Six labels (seven now) sharing one
phone width cut "Erinnerungen" and "Einstellungen" off, and a tab whose name is cut
off is a tab the user has to guess at; each tab is now as wide as its own word
inside a `horizontalScroll`, which is also the only shape that survives a seventh
destination. The selected tab is measured and scrolled into view. Labels, **not
icons** — a Sperrliste or a self-contract has no icon that would not have to be
invented and then explained. Tab screens pass `bottomInset = false` to `EtaScreen`:
the bar already sits over the navigation bar, and insetting twice leaves a gap.

