---
paths:
  - "app/src/**/ui/quickadd/**"
  - "app/src/**/widget/**"
  - "app/src/**/ui/attributes/**"
  - "app/src/**/ui/concretize/**"
  - "app/src/**/ItemExtras.kt"
---

### Quick-Add, and what a bare note becomes

**Quick-Add asks for a name and nothing else**, on both of its pages, because that is
what a quick-add *is*: the thought is caught now and the questions are answered in
the evening. `ui/quickadd/` owns the panel — the pager, the two forms and their view
model — because the widget needs the same thing the dashboard shows, and leaving it
inside `DashboardScreen` would have meant writing it twice.

**The whole box swipes, not only the pager.** The heading and its dots sit *outside*
the pager so they do not slide about, which meant the top half of the card swallowed
the gesture and only the lower half turned the page. A `detectHorizontalDragGestures`
on the `EtaSurface` itself fixes it: Compose offers a gesture to the innermost
handler first, so the pager keeps its own area and the outer detector picks up
everything around it. The dots are tappable too.

**The second page makes a recurring card.** `Item.newQuickRecurring` puts a RECURRING
item in the Sammelliste with no rule, no start time and no duration, beside the bare
ToDos — same one-month clock, same Sperrliste at the end of it, because an unanswered
note is being hoarded whichever kind it is. It occupies no day meanwhile, expansion
skipping any definition missing one of those three.

That made **`Item.isConcretized` a question per type**, which it should always have
been: a ToDo needs category, priority and duration — what the revolver sorts and
bills by — and a recurring definition needs rule, start time and duration, what
expansion refuses to guess. It used to read `type != TODO || …`, true only while the
questionnaire was the sole way to make one.

**The evening's concretizing step therefore has two cards**, picked by `item.type`:
no priority and no target date for a recurring note — a standing task has neither —
and weekdays, start and duration instead. `rulesForWeekdays` in `domain/recurrence/`
is the rule: seven days collapse into `RecurrenceRule.Daily`, fewer become **one
definition per weekday**, following the questionnaire's precedent so that later
moving the Tuesday one does not drag Thursday along. `ItemRepository.concretizeRecurring`
updates the row that exists and adds copies for the rest, then `topUp` runs — or the
task would exist without a single occurrence while the user is looking at the screen
that just said it was done. It moves the row to `Stage.DAY` and clears
`enteredCollectionAt`, so **the entry leaves the Sammelliste**; that is the point
rather than a side effect, and the dialog says so before it happens.

**The widget is a launcher, not a text field.** `RemoteViews` has no editable field
on a home screen, so `QuickAddWidgetProvider` draws a field-shaped tap target and
opens `QuickAddActivity` — translucent, `singleTop`, excluded from recents, holding
the very same `QuickAddPanel`, so the swipe to the recurring form comes for free. It
stays open after an entry, since the point of a quick-add is that several fit in one
sitting, and it shares the one `AppContainer`, so a note is in the Sammelliste before
the sheet is closed. **No `updatePeriodMillis`**: it shows nothing that changes.
Two things it cannot share and therefore duplicates, both marked as such: the
**palette** (`eta_widget_*` in `values/colors.xml` and its night twin — a launcher
inflates `RemoteViews` in its own process, where no Compose tree and no `EtaTheme`
exist) and the **sheet window theme** (`Theme.Eta_QuickAdd`). Since step 30 the
provider recolours the widget for the Eta design — see *Step 30*.

### One attribute form, used everywhere

`ui/attributes/ItemAttributes.kt` holds `TodoAttributes` and `RecurringAttributes` —
the answers themselves — plus the two composables that ask for them, and every
screen that creates, concretizes or edits a card mounts one: both concretize cards,
`ItemEditDialog` on the Listen tab, `AddGoalDialog` in the weekly planning,
`NewTodoDialog` in the planner, `RecurringGroupEditDialog`, the Growth-Tasks dialog,
and `BlockEditDialog` for the block-level fields. Before that, five screens each had
their own idea of which fields exist. `showTargetDate` is what lets
`NewTodoDialog` skip a target date — dropping the card on a slot has answered that —
and `allowNoCategory` what lets the frame of the day keep paying nothing.

Two places are deliberately left out. **Quick-Add**, for the reason above.
**`SpendDialog`**, the second revolver's Custom Earn / Custom Spend / Social, is not
a ToDo at all but a fixed-length points entry whose margins are reachable by long
-pressing the block it produces; a sound at the end of an hour of reward time is the
one extra with an obvious use there, and would need a third `PendingPlacement`.

Two consequences that fall out of the sharing rather than being arranged:

- **A spontaneous ToDo goes through the same placement as a revolver drop**, break
  prompt included. `BreakPrompt` therefore carries a `PendingPlacement` — either the
  card from the revolver or the one about to be created — and answering it re-runs
  exactly the placement that was refused, with the break gone.
- **Saving an edit finishes a card.** The forms cannot express "no category" for a
  ToDo any more, so a Sammelliste entry that was `unvollständig` before the edit is
  `isConcretized` after it. That was its own complaint and needed no code.

`ItemExtras` gathers the newer columns — the pomodoro default, the reminder, the
deadline and the growth answers — into one value, so the five calls that finish a
card gained one parameter rather than seven. The journeys, the break and the end
sound keep their own fields, predating the box and already travelling through every
save path. `ExtrasBox` (in the same package) folds all of it away between "Dauer"
and Sichern, with the header saying how many are on, because a fold that gives no
account of what it hides makes the user open it to find out.

