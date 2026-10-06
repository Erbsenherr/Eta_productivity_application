---
paths:
  - "app/src/**/*WeekScheme*.kt"
  - "app/src/**/ui/lists/SmartListsScreen.kt"
---

### Step 25: the Wochenschema

A minimal picture of the standing week — **no labels**, as asked: seven columns,
Monday left, midnight at the top, free time green (`colors.success`) over booked grey
(`colors.border`). `WeekSchemeChart` in `ui/lists/`, one `Canvas`, nothing tappable.

- **What books time** is `weekOccupancy` in `domain/recurrence/WeekScheme.kt`: the
  setup's night (`sleepStretches` — sleep has no item) plus the **container** of every
  live, concretized standing task, the same span the overlap warning uses. A
  fortnightly or monthly task books its weekday **every** week: a slot taken every
  other week is not one to plan something weekly into. It reads the *definitions*,
  not the blocks, so a dragged ToDo or a cancelled occurrence does not show — it is a
  scheme of the standing week, which is what recurring tasks are placed against.
- **Where it lives:** in the Wiederkehrend box on the Listen tab, above the tasks,
  and above it the **incomplete recurring notes**, which left the Sammelliste list on
  this screen (`SmartListsUiState.incompleteRecurring`). The evening's concretize
  step and the weekly planning are unchanged.
- **Also in the two editors** (`ItemEditDialog` for a recurring note,
  `RecurringGroupEditDialog`), via `SchemePreview`: a dialog covers the list, and
  consulting the scheme *while editing* was the point. There it draws the week
  without the task being edited, and the form's current answer over it in the accent
  colour (`slotOccupancy`), live. The Growth-Tasks tab mounts the group editor
  without a `setup`, so its preview draws no night.

