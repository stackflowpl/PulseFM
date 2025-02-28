package net.gf.radio24

import android.app.*
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.audio.AudioAttributes

class RadioService : Service() {

    private var exoPlayer: ExoPlayer? = null
    private var isPlaying = false
    private var currentStationUrl: String? = null

    override fun onCreate() {
        super.onCreate()

        exoPlayer = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    stopSelf()
                }
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "PLAY" -> {
                val stationUrl = intent.getStringExtra("STATION_URL")
                val stationName = intent.getStringExtra("STATION_NAME")
                val iconRes = intent.getIntExtra("ICON_RES", 0)

                if (stationUrl != null) {
                    if (currentStationUrl == stationUrl && isPlaying) {
                        stopRadio()
                    } else {
                        playRadio(stationUrl)
                        currentStationUrl = stationUrl
                        createNotification(stationName, iconRes)
                    }
                }
            }
            "STOP" -> stopRadio()
        }
        return START_STICKY
    }

    private fun playRadio(url: String) {
        exoPlayer?.apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            play()
        }
        isPlaying = true
    }

    private fun stopRadio() {
        exoPlayer?.stop()
        isPlaying = false

        val stopIntent = Intent("RADIO_STOPPED")
        sendBroadcast(stopIntent)

        stopForeground(true)
        stopSelf()
    }

    private fun createNotification(stationName: String?, iconRes: Int) {
        val playIntent = PendingIntent.getService(
            this, 0, Intent(this, RadioService::class.java).apply { action = "PLAY" },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, RadioService::class.java).apply { action = "STOP" },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = BitmapFactory.decodeResource(resources, iconRes)

        val notification = NotificationCompat.Builder(this, "radio_channel")
            .setSmallIcon(iconRes)
            .setContentTitle(stationName ?: "Radio24")
            .setContentText("Odtwarzanie na żywo")
            .setLargeIcon(largeIcon)
            .addAction(R.drawable.pause_button, "Stop", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .build()

        startForeground(1, notification)
    }

    override fun onDestroy() {
        exoPlayer?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}


