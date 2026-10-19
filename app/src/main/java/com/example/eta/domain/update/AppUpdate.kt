package com.example.eta.domain.update

/**
 * The stamp every build carries as its `versionName`, and every release on
 * GitHub as its tag: `2026-10-19-1412`, the minute it was built.
 *
 * Written so it sorts as text, which is all the comparison needs. The arithmetic
 * that turns it into a `versionCode` lives in `app/build.gradle.kts` and is only
 * there for Android, which refuses to install an update with a lower code.
 */
val BUILD_STAMP = Regex("""\d{4}-\d{2}-\d{2}-\d{4}""")

/** One release as GitHub describes it, reduced to what the update needs. */
data class Release(
    /** The tag, which is the stamp of the build attached to it. */
    val tag: String,
    /** The patch notes, as written for the testers. */
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
)

/** One file attached to a release. */
data class ReleaseAsset(val name: String, val url: String, val size: Long)

/**
 * Whether [release] is newer than the build that is running.
 *
 * A running build without a stamp — the "1.0" of every APK before the updates
 * existed, or a build made without one — is older than any release, so it is
 * always offered the latest. A tag without one is never offered: it is not a
 * build this mechanism made.
 */
fun isNewer(release: String, running: String): Boolean {
    if (!BUILD_STAMP.matches(release)) return false
    if (!BUILD_STAMP.matches(running)) return true
    return release > running
}

/** The APK among a release's files, or null if it carries none. */
fun apkOf(assets: List<ReleaseAsset>): ReleaseAsset? =
    assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }

/**
 * The patch notes as plain text: the Markdown they are written in, without its
 * marks. The settings page has no Markdown renderer, and a list of `**` and `#`
 * reads worse than none.
 */
fun plainNotes(markdown: String): String =
    markdown.lines()
        .map { line ->
            line.trimEnd()
                .replace(Regex("""^#+\s*"""), "")
                .replace(Regex("""^(\s*)[-*]\s+"""), "$1• ")
                .replace("**", "")
                .replace("`", "")
        }
        .joinToString("\n")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()

/** A size in bytes as the page says it: "9,8 MB". */
fun formatMegabytes(bytes: Long): String {
    val tenths = (bytes * 10 + 512 * 1024) / (1024 * 1024)
    return "${tenths / 10},${tenths % 10} MB"
}
