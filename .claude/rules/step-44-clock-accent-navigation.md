---
paths:
  - "app/src/**/ui/theme/**"
  - "app/src/**/ui/components/EtaTabBar.kt"
  - "app/src/**/ui/components/EtaIcons.kt"
  - "app/src/**/alarm/ClockAppAlarms.kt"
  - "app/src/**/alarm/EtaSound.kt"
  - "app/src/**/domain/setup/ClockAlarms.kt"
  - "app/src/**/ui/settings/**"
  - "app/src/**/ui/root/EtaApp.kt"
---

### Step 44: silence, scroll positions, the clock app, an accent, icons

Three bugs, one change and two features from `update.txt`. **No schema change**
— the database stays at version 31; everything new is in `SharedPreferences`.

#### Three bugs

- **`WAKE_SNOOZE` is ten minutes**, not nine.
- **A phone on silent or vibrate silences Eta.** `EtaSound.isSilenced` is what
  `play` and `EtaSpeech.speak` ask, and it now reads the ringer as well
  (`isRingerMuted`). This **overrules "only Do-Not-Disturb silences Eta"** in
  `alarms.md`: the sounds still ride the alarm stream, so the notification
  *volume* stays irrelevant, but the mute switch counts. While DND is on, the
  "Trotzdem spielen" switch alone decides — most phones report the ringer as
  silent under DND, and reading both would make that switch do nothing. With
  DND off and the ringer muted there is no way round. **The wake alarm asks
  neither**; it is an alarm clock.
- **A screen comes back where it was left.** Two causes, two fixes:
  - `SettingsScreen` keyed its one scroll state by the page, so the menu's went
    with it. `menuScroll` is held outside `key(page)`; a page still opens at
    its top.
  - `MainScaffold` wraps the tab's content in
    `rememberSaveableStateHolder().SaveableStateProvider(tab.name)`.
    `rememberSaveable` survives a rotation by itself but **not being taken out
    of the composition**, which is what a tab switch and every flow do. It now
    keeps a tab's scroll, and whatever else it saves: the settings page that
    was open, the dashboard's own tab, a fold.

#### The morning alarm in the phone's clock app

`alarm/ClockAppAlarms.kt`, `domain/setup/ClockAlarms.kt`, the box under the
Weckruf on its settings page.

- **`AlarmClock.ACTION_SET_ALARM` can create an alarm and nothing else.** There
  is no way to change or delete one, nor to ask which exist. So this is **a
  button, not a setting kept in step**: after the hours change, the old alarms
  are deleted in the clock app by hand and set again — and the box says so. A
  night changed for one date (step 40) is not carried over; a repeating alarm
  knows weekdays.
- **`UserSetup.clockAlarms()`** — as many as the *way* the nights were answered:
  one (`SAME`), two (`WEEKEND`), seven (`PER_DAY`), also where hours coincide. A
  day belongs to the night that ends on it.
- **`ClockApp`**: `SYSTEM` (no package — the phone's own, which on a Samsung or
  Xiaomi is the one their battery saving leaves alone), `GOOGLE`
  (`com.google.android.deskclock`), `ANDROID` (`com.android.deskclock`). Offered
  only where it resolves; the choice is in `SharedPreferences` ("clock").
- **One `startActivities` for all of them**, with `EXTRA_SKIP_UI`: the first
  start puts the clock app in front, and an app no longer in front may not
  start another.
- The manifest has `com.android.alarm.permission.SET_ALARM` and a `<queries>`
  entry for the action, or from Android 11 no clock app is seen.
- **Eta's own wake alarm is not switched off by it.** The box warns while it is
  on. It writes to the draft and needs "Sichern" to re-aim the alarm, and a
  button that changed a stored answer on the side would be the wrong shape.
- The button is grey while the page has unsaved answers: what would be handed
  over is the draft.

#### An accent of one's own

`DesignChoice.accent` (ARGB, null for the design's), a third answer beside
design and brightness, in the same `DesignStore`.

- **`EtaColors.withAccent`** moves three tokens together: `accent`, `accentSoft`
  (the accent thinned onto the palette's own `surface`, so it is right by day
  and by night) and `onAccent` (near-black or white, by luminance). Nothing
  else: category colours, warnings, the launch screen and **the widget's six
  layouts** keep theirs.
- `AccentField` in the Design card: the design's own colour as the first
  swatch (which takes the choice back), `ACCENT_PRESETS`, and a hue bar —
  `accentOfHue`, at the presets' saturation and value. The bar's gestures read
  the callback through `rememberUpdatedState`.

#### Navigation: names, icons, or both

`TabStyle` on `DesignChoice` — a look, known before the first frame.
`SettingsPage.NAVIGATION` chooses it and lists the icons.

- **The icons are the app's choice, one per tab** (`EtaIcon`,
  `ui/components/EtaIcons.kt`), drawn on a `Canvas` on a 24-unit grid in one
  stroke weight: house, list, signed page, bell, target, present, bar chart,
  folded sheet, sliders. This ends "labels, not icons" in `ui-foundation.md` as
  a rule; names are still the default.
- `EtaTab` carries its icon; a tab without one would keep its name whatever
  the style. An icon alone is 56dp wide instead of 72 and says its name to a
  screen reader (`semantics { contentDescription = spoken }` — the value is a
  local named otherwise, see the trap in `CLAUDE.md`).

**Verified by the compiler, the suite (684), `assembleRelease` and `lint`.**
Nobody has seen any of it — and whether a clock app honours `EXTRA_DAYS` and
`EXTRA_SKIP_UI` for several alarms at once can only be seen on the phone.
