package app.lawnchair.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Build
import android.widget.RemoteViews
import com.android.launcher3.R

class BeachClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_beach_clock)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val accentColor = context.resources.getColor(
                android.R.color.system_accent1_200,
                context.theme,
            )
            views.setTextColor(R.id.beach_clock_time, accentColor)
            views.setTextColor(R.id.beach_clock_date, (accentColor and 0x00FFFFFF) or 0xB0000000.toInt())
        }
        ids.forEach { manager.updateAppWidget(it, views) }
    }
}
