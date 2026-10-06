package com.example.eta.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.eta.EtaApplication
import com.example.eta.R
import com.example.eta.ui.theme.AppDesign
import com.example.eta.ui.theme.Brightness
import com.example.eta.ui.theme.DesignChoice

/**
 * The Quick-Add box on the home screen.
 *
 * It is a **launcher, not a text field**: `RemoteViews` has no editable view, so
 * a widget cannot take typing itself. What it can do is look like the field it
 * opens and get out of the way in one tap — which lands in [QuickAddActivity],
 * holding the very same panel the dashboard shows, swipe to the recurring form
 * included.
 *
 * There is no `updatePeriodMillis`: the widget shows nothing that changes, so
 * waking it on a timer would spend battery redrawing the same picture.
 */
class QuickAddWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, buildViews(context))
        }
    }

    private fun buildViews(context: Context): RemoteViews {
        val intent = Intent(context, QuickAddActivity::class.java).apply {
            // The activity is `singleTop`; a second tap while it is up should
            // bring that one forward rather than stack another on it.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val choice = (context.applicationContext as EtaApplication)
            .container.designStore.choice.value

        return RemoteViews(context.packageName, layoutOf(choice)).apply {
            // The whole panel is the target. A widget this small has no room for
            // a hit area smaller than itself.
            setOnClickPendingIntent(R.id.widget_root, pending)
        }
    }

    /**
     * One layout per look, rather than one layout recoloured after the fact.
     *
     * Recolouring — `setBackgroundResource` and text colours set from here — is
     * what this did at first, and on the user's phone the panel kept the layout's
     * own background while the field took the new one: a dark field in a light
     * box. A layout names its drawables and colours itself, so there is nothing
     * for a launcher to apply half of, and the sets with a night twin follow the
     * phone on every Android version without being redrawn.
     */
    private fun layoutOf(choice: DesignChoice): Int = when (choice.design) {
        AppDesign.ETA -> when (choice.brightness) {
            Brightness.SYSTEM -> R.layout.widget_quick_add_red_auto
            Brightness.LIGHT -> R.layout.widget_quick_add_red
            Brightness.DARK -> R.layout.widget_quick_add_red_dark
        }
        AppDesign.LEGACY -> when (choice.brightness) {
            Brightness.SYSTEM -> R.layout.widget_quick_add
            Brightness.LIGHT -> R.layout.widget_quick_add_legacy_light
            Brightness.DARK -> R.layout.widget_quick_add_legacy_dark
        }
    }

    companion object {
        /** Redraws every placed widget — the design is the one thing it shows that changes. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, QuickAddWidgetProvider::class.java),
            )
            if (ids.isNotEmpty()) QuickAddWidgetProvider().onUpdate(context, manager, ids)
        }
    }
}
