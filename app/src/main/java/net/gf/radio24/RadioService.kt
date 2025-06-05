package net.gf.radio24

import android.app.*
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.audio.AudioAttributes
import android.os.Build
import android.util.Log

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "radio_channel",
                "Radio Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "PLAY" -> {
                val stationUrl = intent.getStringExtra("STATION_URL")
                val stationName = intent.getStringExtra("STATION_NAME")
                val iconRes = intent.getIntExtra("ICON_RES", 0)

                if (stationUrl != null) {
                    val notification = createNotification(stationName, iconRes)
                    startForeground(1, notification)

                    if (currentStationUrl == stationUrl && isPlaying) {
                        stopRadio()
                    } else {
                        playRadio(stationUrl)
                        currentStationUrl = stationUrl
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

    private fun createNotification(stationName: String?, iconRes: Int): Notification {
        Log.d("RadioService", "Creating notification for $stationName with icon $iconRes")

        val stopIntent = Intent(this, RadioService::class.java).apply {
            action = "STOP"
        }

        val pendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = try {
            BitmapFactory.decodeResource(resources, iconRes)
        } catch (e: Exception) {
            Log.e("RadioService", "Error loading large icon: ${e.message}")
            null
        }

        return NotificationCompat.Builder(this, "radio_channel")
            .setSmallIcon(R.drawable.radio24_trans)
            .setContentTitle(stationName ?: "Radio24")
            .setContentText("Odtwarzanie na żywo")
            .setLargeIcon(largeIcon)
            .addAction(R.drawable.pause_button, "Stop", pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun onDestroy() {
        exoPlayer?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}