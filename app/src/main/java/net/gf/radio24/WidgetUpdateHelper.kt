package net.gf.radio24

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

object WidgetUpdateHelper {

    fun updateAllWidgets(context: Context, isPlaying: Boolean, isBuffering: Boolean,
                         stationName: String?, trackTitle: String?, trackArtist: String?, trackInfo: String?,
                         stationUrl: String?, iconRes: Int) {

        val sharedPrefs = context.getSharedPreferences("RadioWidget", Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            putBoolean("isPlaying", isPlaying)
            putBoolean("isBuffering", isBuffering)
            putString("stationName", stationName ?: "PulseFM")
            putString("trackTitle", trackTitle ?: "Aktualny utwór")
            putString("trackArtist", trackArtist ?: "Wykonawca")
            putString("trackInfo", trackInfo ?: "Wstrzymane")
            putString("stationUrl", stationUrl ?: "")
            putInt("iconRes", iconRes)
            apply()
        }

        val appWidgetManager = AppWidgetManager.getInstance(context)

        val widget4x1DarkIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, RadioWidget4x1Dark::class.java)
        )
        for (appWidgetId in widget4x1DarkIds) {
            RadioWidget4x1Dark.updateAppWidget(context, appWidgetManager, appWidgetId)
        }

        val widget4x1LightIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, RadioWidget4x1Light::class.java)
        )
        for (appWidgetId in widget4x1LightIds) {
            RadioWidget4x1Light.updateAppWidget(context, appWidgetManager, appWidgetId)
        }

        val widget4x2DarkIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, RadioWidget4x2Dark::class.java)
        )
        for (appWidgetId in widget4x2DarkIds) {
            RadioWidget4x2Dark.updateAppWidget(context, appWidgetManager, appWidgetId)
        }

        val widget4x2LightIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, RadioWidget4x2Light::class.java)
        )
        for (appWidgetId in widget4x2LightIds) {
            RadioWidget4x2Light.updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }
}
