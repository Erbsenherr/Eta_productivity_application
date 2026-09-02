package com.example.erik_iteration_2.data.backup

import android.content.Context
import android.net.Uri
import com.example.erik_iteration_2.data.local.ErikDatabase
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** What a backup or a restore did, in words the screen can show. */
sealed interface BackupResult {
    data class Ok(val bytes: Long) : BackupResult
    data class Failed(val reason: String) : BackupResult
}

/** Suggested file name; the picker lets the user change it. */
const val BACKUP_FILE_NAME = "erik-daten.zip"

/**
 * Copying the user's data out to a place they chose, and back in.
 *
 * **What this is not:** the live database does not move. Scoped storage means an
 * app cannot keep a working SQLite file in an arbitrary folder — SAF hands out
 * document URIs, not paths, and SQLite needs a path it can lock. So the database
 * stays in app-private storage and this writes copies where the user points.
 *
 * All three SQLite files travel together in one zip. Copying only `erik.db` while
 * write-ahead logging is on would hand back a database missing its most recent
 * writes — the ones the user just made.
 */
class BackupService(private val context: Context) {

    private val databaseFile: File get() = context.getDatabasePath(ErikDatabase.NAME)

    private val parts: List<File>
        get() = listOf(
            databaseFile,
            File(databaseFile.path + "-wal"),
            File(databaseFile.path + "-shm"),
        )

    fun export(target: Uri): BackupResult = runCatching {
        var written = 0L
        context.contentResolver.openOutputStream(target, "wt").use { out ->
            requireNotNull(out) { "Ziel nicht beschreibbar" }
            ZipOutputStream(out).use { zip ->
                parts.filter { it.exists() }.forEach { part ->
                    zip.putNextEntry(ZipEntry(part.name))
                    written += part.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        BackupResult.Ok(written)
    }.getOrElse { BackupResult.Failed(it.message ?: "Sicherung fehlgeschlagen") }

    /**
     * Writes a backup back over the database files.
     *
     * The app has to be restarted afterwards: Room is holding the old files open,
     * and everything already read from them is stale. The screen says so rather
     * than pretending the swap took effect.
     */
    fun import(source: Uri): BackupResult = runCatching {
        val allowed = parts.associateBy { it.name }
        var restored = 0L

        context.contentResolver.openInputStream(source).use { input ->
            requireNotNull(input) { "Datei nicht lesbar" }
            ZipInputStream(input).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    // Only the files this app owns; a zip entry naming a path
                    // elsewhere must never be followed.
                    val target = allowed[File(entry.name).name]
                    if (target != null) {
                        target.outputStream().use { restored += zip.copyTo(it) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        if (restored == 0L) {
            BackupResult.Failed("Keine ERIK-Daten in der Datei.")
        } else {
            BackupResult.Ok(restored)
        }
    }.getOrElse { BackupResult.Failed(it.message ?: "Wiederherstellen fehlgeschlagen") }
}
