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

**A design is a set of tokens and nothing else.** `AppDesign` in `ui/theme/` has two
entries; `colorsOf(design, dark)` and `shapesOf` turn one into an `EtaColors` and an
`EtaShapes`, and `EtaTheme(choice)` provides them. No screen asks which design is on
— a screen that needs to is missing a token. Another design is an enum entry and two
branches.

- **`ETA`**, the default, is built from the app icon: `EtaRedColors` by day — white,
  `EtaBrandRed` (`#E5322D`, sampled from the glyph) as the accent — and
  `EtaRedDarkColors` by night, a warm near-black with the red a shade lighter
  (`#EB4640`), because the icon's own is too dark to read as text there. Rounder
  corners in both (`EtaRedShapes`).
- **`LEGACY`** is the app as it looked while it was called ERIK, untouched:
  `LegacyLightColors` / `LegacyDarkColors` (the old `Light…`/`Dark…` palettes under
  a new name) and the default `EtaShapes()`. The same thing in indigo.

**Day or night is a second answer, `Brightness`**, and applies to whichever design is
on: `SYSTEM` (the default) follows the phone, `LIGHT` and `DARK` hold whatever it
says. `DesignChoice` is the pair, and `choice.colors(systemDark)` the one place the
two meet. In the Design card it is **one button that steps through the three**
(`Brightness.next()`), with the current answer as its label — asked for in that
form, so do not turn it into a three-way choice. For one build "Eta Dunkel" was a
third design instead; `DesignChoice.fromStored` still reads a stored `ETA_DARK` as
Eta, dark.

Two things in the red palettes moved out of the red's way, and should not drift back:
**`danger` is a dark wine** (a rose by night, where wine would vanish), because a red
warning beside a red accent reads as one more button, and **Fokus keeps a blue of its
own**, where the legacy palette lets it share the accent — a day of Fokus blocks in
the accent colour would be a day painted red, next to the red mark for an overlap.
`fixedBlock` is square in every design; `AppDesignTest` pins that, since the corner
carries meaning.

**The choice lives in `SharedPreferences`** (`DesignStore`, on `AppContainer`), not in
`user_setup`: it has to be known before the first frame — a database read would show
one palette and then swap it — and before the questionnaire has produced a row.
Consequences: it applies at once, without "Einrichtung sichern"; **it does not
travel in a backup**; and the debug reset leaves it alone.

**`applyDesignToWindow`** (`ui/theme/DesignWindow.kt`) is what every activity calls
instead of `enableEdgeToEdge()`. It sets the two things Compose does not paint:
the system bar icons — the plain call picks them from the device's mode, which gives
white icons on a white screen once the user fixed the design light on a dark phone — and the window
background that shows until the first frame. `MainActivity` calls it again when the
design changes. `QuickAddActivity` keeps the plain call: it is translucent over the
home screen, whose bars are not the app's to colour.

**The widget follows by hand.** Its layout carries the legacy colours following the
phone through `values-night`, which is right for Legacy on `SYSTEM` and for nothing
else. `lookOf(choice)` in the provider picks drawables and colours for the other
five combinations: `…_red_auto` has a night twin and follows the phone, the rest
(`…_red`, `…_red_dark`, `…_legacy_light`, `…_legacy_dark`) are pinned and have none
on purpose. Text colours go by resource from Android 12 (`RemoteViews.setColor`), so
a following widget keeps following; before that they are resolved when drawn.
`QuickAddWidgetProvider.refresh` redraws placed widgets when either answer changes.

### The launch screen

`ui/root/LaunchScreen.kt`: the glyph (`drawable-nodpi/eta_glyph.png`, cut out of
`images/eta_app_icon_256_glyph.png` with the white turned into transparency) and
"Electronic Time Assistent" beneath it in the same red. **White in every design and
in the dark** — a design fixed to dark included — because it is the icon shown large, not a
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
screen, the status bar icons in each combination of design, brightness and device mode, and
whether the red reads well on real cards, by day and by night. White text on the red is about 4.2:1
— a little under the usual 4.5:1 for small text; darken `EtaBrandRed` in `accent`
only (not on the launch screen) if buttons prove hard to read.
