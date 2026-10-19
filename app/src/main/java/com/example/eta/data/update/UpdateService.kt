package com.example.eta.data.update

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.example.eta.domain.update.Release
import com.example.eta.domain.update.ReleaseAsset
import com.example.eta.domain.update.apkOf
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Where the releases are published — see `tools/publish-release.ps1`. */
private const val LATEST_RELEASE =
    "https://api.github.com/repos/Erbsenherr/Eta_productivity_application/releases/latest"

/**
 * Finding Eta's newest release on GitHub, over plain `HttpURLConnection`.
 *
 * **Eta looks, the browser fetches.** The APK is downloaded and installed by
 * the browser, not by Eta: an app holding `REQUEST_INSTALL_PACKAGES` and
 * downloading APKs is what Play Protect takes for a dropper, and the first build
 * that did so was flagged as harmful on installing. Asking a JSON API and opening
 * a link is not that pattern.
 *
 * The repository is public, so nothing here signs in; the API allows sixty
 * unauthenticated requests an hour, which a button pressed by hand never reaches.
 */
class UpdateService(private val context: Context) {

    /** The stamp of the build that is running, its `versionName`. */
    val runningVersion: String
        get() = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    /** The newest published release, or an [IOException] saying why there is none. */
    suspend fun latest(): Release = withContext(Dispatchers.IO) {
        val connection = (URL(LATEST_RELEASE).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/vnd.github+json")
            // GitHub's API turns away requests without one.
            setRequestProperty("User-Agent", "Eta-App")
        }
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

    /** The browser, opened on [release]'s APK: it downloads it, and offers to install. */
    fun downloadIntent(release: Release): Intent =
        Intent(Intent.ACTION_VIEW, release.apkUrl.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
