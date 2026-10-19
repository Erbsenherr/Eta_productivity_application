package com.example.eta.data.update

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.content.pm.PackageInfoCompat
import com.example.eta.domain.update.Release
import com.example.eta.domain.update.ReleaseAsset
import com.example.eta.domain.update.apkOf
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Where the releases are published — see `tools/publish-release.ps1`. */
private const val LATEST_RELEASE =
    "https://api.github.com/repos/Erbsenherr/Eta_productivity_application/releases/latest"

private const val APK_MIME = "application/vnd.android.package-archive"

/**
 * Updating Eta from the releases on GitHub, over plain `HttpURLConnection`.
 *
 * The repository is public, so nothing here signs in; the API allows sixty
 * unauthenticated requests an hour, which a button pressed by hand never reaches.
 * The APK goes to the cache and is handed to Android's own installer, which asks
 * the user — an app that is not a store cannot install anything silently, and
 * the installer refuses an APK signed with another key, so a foreign file cannot
 * replace Eta by this route.
 */
class UpdateService(private val context: Context) {

    /** The stamp of the build that is running, its `versionName`. */
    val runningVersion: String
        get() = packageInfo().versionName.orEmpty()

    private val runningCode: Long
        get() = PackageInfoCompat.getLongVersionCode(packageInfo())

    /** The newest published release, or an [IOException] saying why there is none. */
    suspend fun latest(): Release = withContext(Dispatchers.IO) {
        val connection = open(LATEST_RELEASE)
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> Unit
                HttpURLConnection.HTTP_NOT_FOUND -> throw IOException("Es ist noch keine Version veröffentlicht.")
                403, 429 -> throw IOException("GitHub lässt gerade keine Abfrage zu. Später noch einmal versuchen.")
                else -> throw IOException("GitHub antwortet mit ${connection.responseCode}.")
            }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val files = json.optJSONArray("assets")
            val assets = (0 until (files?.length() ?: 0)).map { index ->
                val file = files!!.getJSONObject(index)
                ReleaseAsset(
                    name = file.optString("name"),
                    url = file.optString("browser_download_url"),
                    size = file.optLong("size"),
                )
            }
            val apk = apkOf(assets) ?: throw IOException("Die neueste Version hat keine APK angehängt.")
            Release(
                tag = json.optString("tag_name"),
                notes = json.optString("body"),
                apkUrl = apk.url,
                apkSize = apk.size,
            )
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Downloads [release]'s APK into the cache and checks it is Eta, and newer.
     *
     * [onProgress] hears the share done, 0 to 1. Anything left from an earlier
     * download is removed first; one APK is ten megabytes.
     */
    suspend fun download(release: Release, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val folder = File(context.cacheDir, "updates").apply { mkdirs() }
        folder.listFiles()?.forEach { it.delete() }
        val target = File(folder, "eta-${release.tag}.apk")

        // GitHub answers the download address with a redirect to its storage,
        // which `HttpURLConnection` follows as long as both are https.
        val connection = open(release.apkUrl)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Download fehlgeschlagen (${connection.responseCode}).")
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) onProgress((done.toFloat() / total).coerceAtMost(1f))
                    }
                }
            }
        } catch (failure: IOException) {
            target.delete()
            throw failure
        } finally {
            connection.disconnect()
        }

        // Read before handing over, so a broken download or a stray file is
        // said here in words rather than as the installer's "App nicht installiert".
        val archive = context.packageManager.getPackageArchiveInfo(target.path, 0)
        when {
            archive == null -> {
                target.delete()
                throw IOException("Die heruntergeladene Datei ist keine lesbare APK.")
            }
            archive.packageName != context.packageName -> {
                target.delete()
                throw IOException("Die heruntergeladene APK ist nicht Eta.")
            }
            PackageInfoCompat.getLongVersionCode(archive) <= runningCode -> {
                target.delete()
                throw IOException("Die heruntergeladene Version ist nicht neuer als diese.")
            }
        }
        target
    }

    /**
     * Whether Android lets Eta hand an APK to the installer. From Android 8 on
     * that is a permission per app, granted by hand in the system settings.
     */
    fun mayInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    /**
     * The system page where Eta is allowed to install apps. Only asked for where
     * [mayInstall] said no, which before Android 8 it never does.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun installPermissionIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${context.packageName}".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Android's installer, opened on [apk]. */
    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun packageInfo() = context.packageManager.getPackageInfo(context.packageName, 0)

    private fun open(address: String): HttpURLConnection =
        (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            // GitHub's API turns away requests without one.
            setRequestProperty("User-Agent", "Eta-App")
        }
}
