---
paths:
  - "app/src/**/ui/dashboard/DashboardBoxes.kt"
  - "app/src/**/ui/dashboard/PomodoroDialog.kt"
  - "app/src/**/ui/planner/MergeDialogs.kt"
  - "app/src/**/*WeekScheme*.kt"
  - "app/src/**/ui/lists/ListDialogs.kt"
  - "app/src/**/domain/planning/Pomodoro.kt"
---

### Step 26: five conveniences and a button that was off the screen

No schema change. The first round written after `CLAUDE.md` was split up.

- **"Gruppieren" was there and could not be seen.** `MergePromptDialog` put
  Abbrechen, a weighted spacer, "Nur verschieben" and the confirmation in one
  `Row`; three buttons are wider than a phone, the spacer took what was left, and
  the last button was laid out at no width at all. The answer now stands on a line
  of its own above the other two, in "Tasks gruppieren?" and "Zur Gruppe
  hinzufügen?" alike — the shape "Gruppen zusammenlegen?" always had. **A row of
  three buttons is the trap**: two with a spacer is the most a dialog holds.
- **A tap unfolds a name.** In the "Gerade" / "Als Nächstes" box and on a row of
  "Heute anstehend" a tap lifts the line limit on the name and a second tap puts it
  back — in place, no bubble: both already have room to grow downwards, unlike a
  quarter-hour block in the planner. The long press keeps its meaning in both
  (pomodoro; "Doch einplanen"). In `TaskRow` the detector is keyed by
  `(block.id, canUncancel)`, so the long-press lambda cannot go stale.
- **Pomodoro phases are freely chosen.** Both pickers — `PomodoroDialog` and the
  Extras default — are `precise`: stepped in fives, typed by tapping the value.
  `MIN_POMODORO_PHASE` dropped from 5 minutes to **1**, and what is typed is cut to
  **whole minutes** before it is stored: `pomodoroBoundaries` counts in minutes, and
  a 90-second phase that displayed as such while ringing every 60 would be a lie.
- **The mark in the Wochenschema turns red where it collides.** `WeekSchemeChart`
  takes `clashDays`, which `SchemePreview` reads off the same `recurringOverlaps`
  result the warning below it prints — one answer, drawn and said. The whole mark on
  that weekday, not only the overlapping part: a quarter of an hour is a sliver at
  that scale.
- **"Nächster freier Slot"** sits under the overlap warning in every recurring form
  and names the hour before it is pressed. `nextFreeStart` in
  `domain/recurrence/WeekScheme.kt`: forward from the start the form has, never
  back; the whole container has to fit and end inside the day; free on **every**
  weekday of the form; chained onto the end of what was in the way, unsnapped, like
  a growth task. It is measured against `weekOccupancy` — the picture the scheme
  draws — which is deliberately **stricter** than the warning (a fortnightly task
  books its weekday every week there), so the slot it finds always clears the
  warning and always lands in green. The Listen tab's two editors pass the setup,
  so the night is an obstacle; the concretize card and the Growth-Tasks dialog have
  no setup and search without it. Null says so in a line rather than hiding the
  button.

**Open:** the note's "nächster freier Slot" may also have meant the
Überschneidung box on the dashboard — two *blocks* colliding on a day. Not built:
it needs a decision on which of the two moves.

**Verified by the compiler, the suite (433, 4 new in `WeekSchemeTest`),
`assembleRelease` and `lint`.** Nobody has seen any of it.
