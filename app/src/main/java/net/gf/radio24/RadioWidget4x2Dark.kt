package net.gf.radio24

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.widget.RemoteViews
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class RadioWidget4x2Dark : AppWidgetProvider() {

    private var broadcastReceiver: BroadcastReceiver? = null

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        registerBroadcastReceiver(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        unregisterBroadcastReceiver(context)
    }

    private fun registerBroadcastReceiver(context: Context) {
        if (broadcastReceiver == null) {
            broadcastReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    when (intent.action) {
                        RadioService.BROADCAST_PLAYBACK_STATE,
                        RadioService.BROADCAST_TRACK_CHANGED -> {
                            updateAllWidgets(context)
                        }
                    }
                }
            }

            val filter = IntentFilter().apply {
                addAction(RadioService.BROADCAST_PLAYBACK_STATE)
                addAction(RadioService.BROADCAST_TRACK_CHANGED)
            }

            LocalBroadcastManager.getInstance(context)
                .registerReceiver(broadcastReceiver!!, filter)
        }
    }

    private fun unregisterBroadcastReceiver(context: Context) {
        broadcastReceiver?.let {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(it)
            broadcastReceiver = null
        }
    }

    private fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, RadioWidget4x2Dark::class.java)
        )
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_4x2_dark)

            val sharedPrefs = context.getSharedPreferences("RadioWidget", Context.MODE_PRIVATE)
            val isPlaying = sharedPrefs.getBoolean("isPlaying", false)
            val isBuffering = sharedPrefs.getBoolean("isBuffering", false)
            val stationName = sharedPrefs.getString("stationName", "PulseFM") ?: "PulseFM"
            val trackTitle = sharedPrefs.getString("trackTitle", "Aktualny utwór") ?: "Aktualny utwór"
            val trackArtist = sharedPrefs.getString("trackArtist", "Wykonawca") ?: "Wykonawca"
            val stationUrl = sharedPrefs.getString("stationUrl", "") ?: ""
            val iconRes = sharedPrefs.getInt("iconRes", R.drawable.radio24_trans)

            views.setTextViewText(R.id.widget_station_name, stationName)
            views.setTextViewText(R.id.widget_track_title, trackTitle)
            views.setTextViewText(R.id.widget_track_artist, trackArtist)
            views.setImageViewResource(R.id.widget_station_icon, iconRes)

            val status = when {
                isBuffering -> "Ładowanie..."
                isPlaying -> "Na żywo"
                else -> "Wstrzymane"
            }
            views.setTextViewText(R.id.widget_status, status)

            val playPauseIcon = if (isPlaying) R.drawable.ic_pause_white else R.drawable.ic_play_arrow_white
            views.setImageViewResource(R.id.widget_play_pause, playPauseIcon)

            val playPauseIntent = Intent(context, RadioService::class.java).apply {
                action = if (isPlaying) RadioService.ACTION_PAUSE else RadioService.ACTION_PLAY
                putExtra(RadioService.EXTRA_STATION_URL, stationUrl)
                putExtra(RadioService.EXTRA_STATION_NAME, stationName)
                putExtra(RadioService.EXTRA_ICON_RES, iconRes)
            }

            val playPausePendingIntent = PendingIntent.getService(
                context, 0, playPauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val stopIntent = Intent(context, RadioService::class.java).apply {
                action = RadioService.ACTION_STOP
            }

            val stopPendingIntent = PendingIntent.getService(
                context, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            views.setOnClickPendingIntent(R.id.widget_play_pause, playPausePendingIntent)
            views.setOnClickPendingIntent(R.id.widget_stop, stopPendingIntent)

            val mainActivityIntent = Intent(context, MainActivity::class.java)
            val mainActivityPendingIntent = PendingIntent.getActivity(
                context, 0, mainActivityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_station_icon, mainActivityPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
