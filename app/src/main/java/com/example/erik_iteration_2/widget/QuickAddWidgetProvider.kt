package com.example.erik_iteration_2.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.erik_iteration_2.R

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
        }
    }
}
