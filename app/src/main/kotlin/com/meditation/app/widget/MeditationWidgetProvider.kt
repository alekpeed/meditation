package com.meditation.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.meditation.app.MeditationApp
import com.meditation.app.R
import com.meditation.app.ui.MainActivity
import com.meditation.core.Stats
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.TimeZone

/**
 * Home-screen widget (brief §15): a tap-to-open quick-start tile showing the current streak and
 * session count. Reads history via the same repository the app uses — no separate data path to
 * keep in sync. [goAsync] extends the broadcast's lifetime for the async Room read, mirroring
 * [com.meditation.app.alarm.BootReceiver]'s established pattern in this codebase.
 */
class MeditationWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        val container = MeditationApp.from(context).container
        container.scope.launch {
            val text = runCatching {
                val history = container.historyRepository.all.first()
                val now = System.currentTimeMillis()
                val tzOffset = TimeZone.getDefault().getOffset(now).toLong()
                val stats = Stats.compute(history, now, tzOffset)
                if (stats.currentStreakDays > 0) {
                    "${stats.currentStreakDays}-day streak · ${stats.sessionCount} sits"
                } else {
                    "${stats.sessionCount} sits so far"
                }
            }.getOrDefault(context.getString(R.string.widget_loading))

            appWidgetIds.forEach { id -> updateOne(context, appWidgetManager, id, text) }
        }.invokeOnCompletion { pending.finish() }
    }

    private fun updateOne(context: Context, manager: AppWidgetManager, widgetId: Int, statsText: String) {
        val views = RemoteViews(context.packageName, R.layout.widget_meditation)
        views.setTextViewText(R.id.widget_stats, statsText)
        val launchIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
        runCatching { manager.updateAppWidget(widgetId, views) }
    }
}
