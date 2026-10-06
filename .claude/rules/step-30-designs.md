---
paths:
  - "app/src/**/ui/theme/**"
  - "app/src/**/ui/root/LaunchScreen.kt"
  - "app/src/**/MainActivity.kt"
  - "app/src/**/widget/**"
  - "app/src/main/res/values*/themes.xml"
  - "app/src/main/res/values*/colors.xml"
---

### Step 30: designs, and the launch screen

**A design is a set of tokens and nothing else.** `AppDesign` in `ui/theme/` has
three entries; `colorsOf` and `shapesOf` turn one into an `EtaColors` and an `EtaShapes`,
and `EtaTheme(design)` provides them. No screen asks which design is on — a screen
that needs to is missing a token. Another design is an enum entry and two branches.

- **`LEGACY`** is the app as it looked while it was called ERIK, untouched:
  `LegacyLightColors` / `LegacyDarkColors` (the old `Light…`/`Dark…` palettes under
  a new name), the default `EtaShapes()`, following the device's dark mode.
- **`ETA`**, the default, is built from the app icon: white, `EtaBrandRed`
  (`#E5322D`, sampled from the glyph) as the accent, rounder corners
  (`EtaRedShapes`). It stays light on a dark device — the request was a light
  design.
- **`ETA_DARK`** ("Eta Dunkel") is the same idea with the lights out:
  `EtaRedDarkColors`, a warm near-black with the red a shade lighter (`#EB4640`),
  because the icon's own is too dark to read as text there. It stays dark by day.

**The two Eta designs are chosen by hand, not by the device**
(`AppDesign.followsSystemDark` is true for Legacy alone). They were asked for as
two designs, one after the other; folding them into one that follows the device
would take away the light one on a phone that is always in dark mode. If that is
ever wanted, it is a fourth entry, not a change to these two.

Two things in `EtaRedColors` moved out of the red's way, and should not drift back:
**`danger` is a dark wine** (a rose in `EtaRedDarkColors`, where wine would vanish),
because a red warning beside a red accent reads as one more button, and **Fokus keeps a blue of its own**, where the legacy palette lets it
share the accent — a day of Fokus blocks in the accent colour would be a day painted
red, next to the red mark for an overlap. `fixedBlock` is square in every design;
`AppDesignTest` pins that, since the corner carries meaning.

**The choice lives in `SharedPreferences`** (`DesignStore`, on `AppContainer`), not in
`user_setup`: it has to be known before the first frame — a database read would show
one palette and then swap it — and before the questionnaire has produced a row.
Consequences: it applies at once, without "Einrichtung sichern"; **it does not
travel in a backup**; and the debug reset leaves it alone.

**`applyDesignToWindow`** (`ui/theme/DesignWindow.kt`) is what every activity calls
instead of `enableEdgeToEdge()`. It sets the two things Compose does not paint:
the system bar icons — the plain call picks them from the device's mode, which gives
white icons on a white screen for a light design on a dark phone — and the window
background that shows until the first frame. `MainActivity` calls it again when the
design changes. `QuickAddActivity` keeps the plain call: it is translucent over the
home screen, whose bars are not the app's to colour.

**The widget follows by hand.** Its layout carries the legacy colours, which follow
dark mode through `values-night`; for the two Eta designs the provider overrides
background and text colours from `eta_red_widget_*` / `eta_red_dark_widget_*`,
which have no night twin on purpose.
`QuickAddWidgetProvider.refresh` redraws placed widgets when the design is chosen.

### The launch screen

`ui/root/LaunchScreen.kt`: the glyph (`drawable-nodpi/eta_glyph.png`, cut out of
`images/eta_app_icon_256_glyph.png` with the white turned into transparency) and
"Electronic Time Assistent" beneath it in the same red. **White in every design and
in the dark** — Eta Dunkel included — because it is the icon shown large, not a
screen of the app, and because the window it starts in cannot know the design. The
spelling "Assistent" is the user's.

- It lies **over** `EtaApp` for `LAUNCH_SCREEN_MILLIS` and fades, so the first screen
  loads underneath. Cold starts only, and **not when an alarm was answered** — a tap
  on "jetzt planen" should not wait behind a logo.
- **Android 12+ draws its own splash** with the launcher icon and cannot carry text.
  `Theme.Eta.Launch` (`values-v31`) blanks it — white, transparent icon — or the
  glyph would appear twice in a row at two sizes. Only `MainActivity` wears that
  theme; the wake alarm keeps `Theme.Eta`, so it does not flash white at night.

**Unverified, like every screen:** the handover from the system splash to the launch
screen, the status bar icons in each combination of design and device mode, and
whether `ETA`'s red reads well on real cards. White text on the red is about 4.2:1
— a little under the usual 4.5:1 for small text; darken `EtaBrandRed` in `accent`
only (not on the launch screen) if buttons prove hard to read.
