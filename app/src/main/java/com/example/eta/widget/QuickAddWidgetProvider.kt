package com.example.eta.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
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

        return RemoteViews(context.packageName, R.layout.widget_quick_add).apply {
            // The whole panel is the target. A widget this small has no room for
            // a hit area smaller than itself.
            setOnClickPendingIntent(R.id.widget_root, pending)

            // The layout carries the legacy colours following the phone's dark
            // mode. Every other combination is put on by hand.
            val choice = (context.applicationContext as EtaApplication)
                .container.designStore.choice.value
            lookOf(choice)?.let { look ->
                setInt(R.id.widget_root, "setBackgroundResource", look.panel)
                setInt(R.id.widget_field, "setBackgroundResource", look.field)
                textColor(context, R.id.widget_title, look.title)
                textColor(context, R.id.widget_field, look.muted)
                textColor(context, R.id.widget_hint, look.muted)
            }
        }
    }

    /**
     * A colour by resource where the launcher can resolve it (Android 12+), so a
     * set with a night twin keeps following the phone after this was drawn.
     * Before that it is resolved here and now, and a widget that follows the
     * phone catches up the next time it is redrawn.
     */
    private fun RemoteViews.textColor(context: Context, viewId: Int, colorRes: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setColor(viewId, "setTextColor", colorRes)
        } else {
            setTextColor(viewId, context.getColor(colorRes))
        }
    }

    /** Drawables and colours for [choice]; null where the layout's own are right. */
    private fun lookOf(choice: DesignChoice): WidgetLook? = when (choice.design) {
        AppDesign.ETA -> when (choice.brightness) {
            Brightness.SYSTEM -> WidgetLook(
                panel = R.drawable.widget_background_red_auto,
                field = R.drawable.widget_field_red_auto,
                title = R.color.eta_red_auto_widget_accent,
                muted = R.color.eta_red_auto_widget_muted,
            )
            Brightness.LIGHT -> WidgetLook(
                panel = R.drawable.widget_background_red,
                field = R.drawable.widget_field_red,
                title = R.color.eta_red_widget_accent,
                muted = R.color.eta_red_widget_muted,
            )
            Brightness.DARK -> WidgetLook(
                panel = R.drawable.widget_background_red_dark,
                field = R.drawable.widget_field_red_dark,
                title = R.color.eta_red_dark_widget_accent,
                muted = R.color.eta_red_dark_widget_muted,
            )
        }
        AppDesign.LEGACY -> when (choice.brightness) {
            Brightness.SYSTEM -> null
            Brightness.LIGHT -> WidgetLook(
                panel = R.drawable.widget_background_legacy_light,
                field = R.drawable.widget_field_legacy_light,
                title = R.color.eta_legacy_light_widget_muted,
                muted = R.color.eta_legacy_light_widget_muted,
            )
            Brightness.DARK -> WidgetLook(
                panel = R.drawable.widget_background_legacy_dark,
                field = R.drawable.widget_field_legacy_dark,
                title = R.color.eta_legacy_dark_widget_muted,
                muted = R.color.eta_legacy_dark_widget_muted,
            )
        }
    }

    private class WidgetLook(val panel: Int, val field: Int, val title: Int, val muted: Int)

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
