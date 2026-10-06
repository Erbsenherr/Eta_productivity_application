package com.example.eta.data.calendar

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.json.JSONObject

/** One calendar as the connected account sees it. */
data class RemoteCalendar(
    val id: String,
    val displayName: String,
    val accountName: String?,
    val isPrimary: Boolean,
    /** Readable rather than owned: subscribed, or shared in by somebody else. */
    val isForeign: Boolean,
)

/** One occurrence of an event, already expanded out of its recurrence. */
data class RemoteEvent(
    val calendarId: String,
    val eventId: String,
    val title: String,
    val date: LocalDate,
    val start: LocalTime,
    val duration: Duration,
    val allDay: Boolean,
    val updatedAt: Instant?,
)

/** What an all-day event is offered at until the user says otherwise. */
val ALL_DAY_SUGGESTED_START: LocalTime = LocalTime(9, 0)
val ALL_DAY_SUGGESTED_DURATION: Duration = 1.hours

/**
 * The Google Calendar REST API, read-only, over plain `HttpURLConnection`.
 *
 * No Google API client library: two endpoints and a handful of fields do not
 * justify the megabytes and the second annotation processor it brings, and this
 * way every field the app depends on is visible in one file. The token is passed
 * in per call rather than held — it is short-lived, and whoever is syncing has
 * just asked for a fresh one.
 *
 * Everything here throws [IOException] carrying the message Google gave, which
 * the sync turns into a sentence the settings card can print.
 */
class GoogleCalendarApi(private val timeZone: TimeZone = TimeZone.currentSystemDefault()) {

    /**
     * Every calendar the account can see, subscribed and shared ones included.
     *
     * That is "several accounts, sub-calendars, foreign calendars" as far as one
     * grant reaches: one authorization covers one Google account, and this lists
     * everything that account holds — its own calendars and anything anyone has
     * shared into it.
     */
    suspend fun calendars(token: String): List<RemoteCalendar> = withContext(Dispatchers.IO) {
        val result = mutableListOf<RemoteCalendar>()
        var pageToken: String? = null
        do {
            val url = buildString {
                append("https://www.googleapis.com/calendar/v3/users/me/calendarList")
                append("?maxResults=250")
                pageToken?.let { append("&pageToken=").append(encode(it)) }
            }
            val page = get(url, token)
            val items = page.optJSONArray("items")
            for (index in 0 until (items?.length() ?: 0)) {
                val item = items!!.getJSONObject(index)
                if (item.optBoolean("deleted", false)) continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                val accessRole = item.optString("accessRole")
                val primary = item.optBoolean("primary", false)
                val name = item.optString("summaryOverride").ifBlank {
                    item.optString("summary").ifBlank { id }
                }
                result += RemoteCalendar(
                    id = id,
                    displayName = name,
                    // Google names the account only on its own primary calendar,
                    // whose id *is* the address. Enough to label the list by.
                    accountName = if (primary) id else null,
                    isPrimary = primary,
                    isForeign = accessRole != "owner",
                )
            }
            pageToken = page.optString("nextPageToken").ifBlank { null }
        } while (pageToken != null)
        result
    }

    /**
     * Every occurrence between [from] and [to], both days included.
     *
     * `singleEvents=true` is what makes a recurring appointment arrive as one
     * dated occurrence per day rather than as a rule the app would have to expand
     * a second way — and it is what gives each occurrence an id of its own, which
     * is what keeps a re-sync from doubling anything. An occurrence cancelled in
     * Google is dropped here: it is not a decision to put to the user.
     */
    suspend fun events(
        token: String,
        calendarId: String,
        from: LocalDate,
        to: LocalDate,
    ): List<RemoteEvent> = withContext(Dispatchers.IO) {
        val timeMin = from.atStartOfDayIn(timeZone)
        val timeMax = to.plus(DatePeriod(days = 1)).atStartOfDayIn(timeZone)

        val result = mutableListOf<RemoteEvent>()
        var pageToken: String? = null
        do {
            val url = buildString {
                append("https://www.googleapis.com/calendar/v3/calendars/")
                append(encode(calendarId))
                append("/events?singleEvents=true&orderBy=startTime&maxResults=2500")
                append("&timeMin=").append(encode(timeMin.toString()))
                append("&timeMax=").append(encode(timeMax.toString()))
                pageToken?.let { append("&pageToken=").append(encode(it)) }
            }
            val page = get(url, token)
            val items = page.optJSONArray("items")
            for (index in 0 until (items?.length() ?: 0)) {
                val item = items!!.getJSONObject(index)
                if (item.optString("status") == "cancelled") continue
                val event = parseEvent(calendarId, item)
                if (event != null) result += event
            }
            pageToken = page.optString("nextPageToken").ifBlank { null }
        } while (pageToken != null)
        result
    }

    /**
     * One event out of the JSON, or null when it carries no usable time.
     *
     * Skipped rather than guessed at, in the same spirit as `expandRecurring`
     * passing over a definition without a start time: an invented hour would be
     * planned against, and nothing would tell it apart from one the user chose.
     */
    private fun parseEvent(calendarId: String, item: JSONObject): RemoteEvent? {
        val id = item.optString("id")
        if (id.isBlank()) return null
        val start = item.optJSONObject("start") ?: return null
        val title = item.optString("summary").ifBlank { "(ohne Titel)" }
        val updated = item.optString("updated").ifBlank { null }
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }

        val startDateTime = start.optString("dateTime").ifBlank { null }
        if (startDateTime == null) {
            // All-day: a date and nothing else. Offered with a suggestion the user
            // has to confirm, rather than swallowing the whole day and with it the
            // morning routine — which is what `Planungsphase.md` asks for.
            val day = start.optString("date").ifBlank { null }
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: return null
            return RemoteEvent(
                calendarId = calendarId,
                eventId = id,
                title = title,
                date = day,
                start = ALL_DAY_SUGGESTED_START,
                duration = ALL_DAY_SUGGESTED_DURATION,
                allDay = true,
                updatedAt = updated,
            )
        }

        val startInstant = runCatching { Instant.parse(startDateTime) }.getOrNull() ?: return null
        val endInstant = item.optJSONObject("end")?.optString("dateTime")?.ifBlank { null }
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val local = startInstant.toLocalDateTime(timeZone)
        val length = endInstant
            ?.minus(startInstant)
            ?.takeIf { it.isPositive() }
            ?: ALL_DAY_SUGGESTED_DURATION

        return RemoteEvent(
            calendarId = calendarId,
            eventId = id,
            title = title,
            date = local.date,
            start = LocalTime(local.hour, local.minute),
            // Whole seconds, so a calendar's stray millisecond does not become a
            // duration the pickers cannot show.
            duration = length.inWholeSeconds.seconds,
            allDay = false,
            updatedAt = updated,
        )
    }

    private fun get(url: String, token: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            connectTimeout = 15_000
            readTimeout = 20_000
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException(describe(code, body))
            }
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    /**
     * The failure in words the settings card can print.
     *
     * 403 is the one worth naming: on a Cloud project still in testing it means
     * the account is not on the tester list, which is a setting in the console
     * rather than anything the app can do something about.
     */
    private fun describe(code: Int, body: String): String {
        val message = runCatching {
            JSONObject(body).getJSONObject("error").optString("message")
        }.getOrNull().orEmpty()
        return when (code) {
            401 -> "Google hat den Zugriff abgelehnt (401). Bitte neu verbinden."
            403 -> "Google verweigert den Zugriff (403). Ist die Calendar API aktiviert " +
                "und dieses Konto als Testnutzer eingetragen? $message"
            404 -> "Kalender nicht gefunden (404)."
            else -> "Google antwortete mit $code. $message".trim()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
