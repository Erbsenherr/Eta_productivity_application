---
paths:
  - "app/src/**/update/**"
  - "app/src/**/ui/settings/UpdateViewModel.kt"
  - "app/build.gradle.kts"
  - "tools/**"
---

### Step 50: the app updates itself from GitHub releases

Asked for directly, not from an `update.txt`. **No schema change.**

#### The build stamp

**Every build carries the minute it was made**, `2026-10-19-1412`: as its
`versionName`, in the name of its APK in `dist/`, and as the tag of its GitHub
release. `app/build.gradle.kts` reads it from `-PetaStamp=…`, which
`tools/build-apk.ps1` passes; without one it is the current minute, through a
`ValueSource` (`CurrentStamp`) so the configuration cache does not keep the
first build's. `versionCode` is `versionCodeOf(stamp)` — minutes since 2026
plus 1000 — and only exists because Android refuses an update with a lower
code. Every APK before step 50 had `versionCode = 1`, `versionName = "1.0"`.

The stamp sorts as text, so the app compares strings (`isNewer` in
`domain/update/AppUpdate.kt`): a running build without a stamp is older than
every release, a tag without one is never offered.

#### Releases, not APKs in the repository

The APKs go to **GitHub Releases** (`tools/publish-release.ps1 <stamp>`), not
into git: ten megabytes a session would stay in the history for ever. The
release's text is the session's `patchnotes/<stamp>.md`, which the page shows,
stripped of its Markdown (`plainNotes`). The repository is public, so the app
reads `releases/latest` without signing in. Needs `gh`, signed in as the
repository's owner; it lives in `C:\Program Files\GitHub CLI\` and the script
finds it there when it is not on the `PATH`.

#### In the app

`SettingsPage.UPDATE`, "App-Update", in the App section after "Deine Daten";
left out in the tutorial's sandbox. `UpdateViewModel` → `UpdateService`
(`data/update/`, plain `HttpURLConnection` like the calendar).

- **Nothing is fetched until the button is pressed.** No check at start.
- **Eta looks, the browser fetches.** "Herunterladen" opens the APK's
  `browser_download_url` with `ACTION_VIEW`; the browser downloads it, and a tap
  on the finished file brings up Android's installer. The browser needs
  "unbekannte Apps installieren" once, not Eta.
- **Never `REQUEST_INSTALL_PACKAGES`, never a download in Eta.** The first
  build of this step had both — download into the cache, a `FileProvider`, the
  installer opened on it — and Play Protect flagged that APK as harmful on
  installing: an app that downloads APKs and may install them is what a dropper
  looks like. Taken out the same day; do not put it back.
- Failures are sentences: the service's own `IOException`s are written for the
  page; anything the network throws (subclasses, JSON) gets a fallback.

**Verified by the compiler, the suite (729), `assembleRelease` and `lint`.**
Nobody has seen it run. The first APK that has the button has to be installed
by hand once, and the button can only find something from the release after it.
