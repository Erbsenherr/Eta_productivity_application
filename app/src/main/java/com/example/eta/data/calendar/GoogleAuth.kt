package com.example.eta.data.calendar

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The scope the app asks for.
 *
 * `calendar.readonly` rather than `calendar.events.readonly`, and the difference
 * matters: the narrower one reads events but cannot list *which calendars exist*,
 * and the whole point of the settings section is that the user picks among them.
 * Read-only either way — Eta never writes to a Google calendar.
 */
const val CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar.readonly"

/** What asking for a token produced. */
sealed interface AuthOutcome {
    data class Ok(val accessToken: String) : AuthOutcome

    /**
     * Google wants to show the user something — an account picker the first time,
     * a consent screen, or a re-consent after access was revoked.
     *
     * A `PendingIntent` rather than an answer, because only an Activity can put it
     * on screen. Everything below the UI hands this upwards untouched.
     */
    data class NeedsConsent(val pendingIntent: PendingIntent) : AuthOutcome

    data class Failed(val reason: String) : AuthOutcome
}

/**
 * Getting an OAuth access token for the Calendar API.
 *
 * Play Services' `AuthorizationClient` rather than an OAuth library of our own:
 * it matches the app by package name and signing certificate against the Android
 * OAuth client in the Cloud project, which means **no client id and no secret
 * live in this repository** — there is nothing here to leak. It also hands back a
 * fresh short-lived token every time it is asked, so there is no refresh token to
 * store either. Ask for one at the start of each sync and throw it away after.
 *
 * The first call raises a consent screen; every call after that returns silently
 * for as long as the grant stands, which is what makes an automatic evening sync
 * possible without ever interrupting the user.
 */
class GoogleAuth(private val context: Context) {

    private fun request(): AuthorizationRequest = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(CALENDAR_SCOPE)))
        .build()

    /**
     * A token, or the screen that has to be shown before there can be one.
     *
     * Never throws: every way this can fail is a state the settings card has to
     * be able to name, and an exception crossing a sync would only turn three
     * different causes into one stack trace.
     */
    suspend fun token(): AuthOutcome = suspendCancellableCoroutine { continuation ->
        Identity.getAuthorizationClient(context)
            .authorize(request())
            .addOnSuccessListener { result ->
                val pending = result.pendingIntent
                val outcome = when {
                    result.hasResolution() && pending != null -> AuthOutcome.NeedsConsent(pending)
                    result.accessToken != null -> AuthOutcome.Ok(result.accessToken!!)
                    else -> AuthOutcome.Failed(
                        "Google hat den Zugriff bestätigt, aber kein Token geliefert.",
                    )
                }
                if (continuation.isActive) continuation.resume(outcome)
            }
            .addOnFailureListener { error ->
                if (continuation.isActive) {
                    continuation.resume(
                        AuthOutcome.Failed(error.message ?: "Google-Anmeldung fehlgeschlagen."),
                    )
                }
            }
    }

    /** The answer the consent screen came back with. */
    fun tokenFrom(data: Intent?): AuthOutcome = runCatching {
        val result = Identity.getAuthorizationClient(context)
            .getAuthorizationResultFromIntent(data)
        result.accessToken
            ?.let { AuthOutcome.Ok(it) }
            ?: AuthOutcome.Failed("Der Zugriff wurde nicht erteilt.")
    }.getOrElse { error ->
        AuthOutcome.Failed(error.message ?: "Der Zugriff wurde nicht erteilt.")
    }
}
