package net.gf.radio24

import android.app.*
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media.session.MediaButtonReceiver
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.metadata.Metadata
import com.google.android.exoplayer2.metadata.icy.IcyHeaders
import com.google.android.exoplayer2.metadata.icy.IcyInfo
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import kotlinx.coroutines.*

class RadioService : Service(), AudioManager.OnAudioFocusChangeListener {

    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSessionCompat? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private var isPlaying = false
    private var isBuffering = false
    private var currentStationUrl: String? = null
    private var currentStationName: String? = null
    private var currentIconRes: Int = 0
    private var hasAudioFocus = false

    private var currentTrackTitle: String? = null
    private var currentTrackArtist: String? = null
    private var currentTrackInfo: String? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var retryJob: Job? = null
    private var retryCount = 0
    private val maxRetries = 3

    private val binder = RadioBinder()

    private lateinit var localBroadcastManager: LocalBroadcastManager

    inner class RadioBinder : Binder() {
        fun getService(): RadioService = this@RadioService
    }

    override fun onCreate() {
        super.onCreate()

        localBroadcastManager = LocalBroadcastManager.getInstance(this)

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        setupMediaSession()
        setupExoPlayer()
        createNotificationChannel()

        Log.d(TAG, "RadioService created with LocalBroadcastManager")
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "RadioService").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    Log.d(TAG, "MediaSession onPlay")
                    if (currentStationUrl != null && !isPlaying) {
                        resumeRadio()
                    } else {
                        currentStationUrl?.let { playRadio(it) }
                    }
                }

                override fun onPause() {
                    Log.d(TAG, "MediaSession onPause")
                    pauseRadio()
                }

                override fun onStop() {
                    Log.d(TAG, "MediaSession onStop")
                    stopRadio()
                }
            })

            isActive = true
        }
    }

    private fun setupExoPlayer() {
        try {
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Radio24/1.0 (Android)")
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000)
                .setAllowCrossProtocolRedirects(true)

            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                    DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
                )
                .setTargetBufferBytes(DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            exoPlayer = ExoPlayer.Builder(this)
                .setMediaSourceFactory(
                    com.google.android.exoplayer2.source.DefaultMediaSourceFactory(httpDataSourceFactory)
                )
                .setLoadControl(loadControl)
                .build().apply {

                    val audioAttributes = AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build()

                    setAudioAttributes(audioAttributes, false)

                    val playerListener = object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            Log.d(TAG, "Playback state changed: $playbackState")
                            when (playbackState) {
                                Player.STATE_BUFFERING -> {
                                    Log.d(TAG, "Player buffering - updating state")
                                    updateState(isPlaying = false, isBuffering = true)
                                }
                                Player.STATE_READY -> {
                                    Log.d(TAG, "Player ready - updating state")
                                    if (hasAudioFocus) {
                                        retryCount = 0
                                        updateState(isPlaying = true, isBuffering = false)
                                    } else {
                                        updateState(isPlaying = false, isBuffering = false)
                                    }
                                }
                                Player.STATE_ENDED -> {
                                    Log.d(TAG, "Player ended")
                                    updateState(isPlaying = false, isBuffering = false)
                                    handlePlaybackError()
                                }
                                Player.STATE_IDLE -> {
                                    Log.d(TAG, "Player idle")
                                    updateState(isPlaying = false, isBuffering = false)
                                }
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            Log.e(TAG, "Player error: ${error.message}", error)
                            updateState(isPlaying = false, isBuffering = false)
                            handlePlaybackError()
                        }

                        override fun onIsPlayingChanged(playing: Boolean) {
                            Log.d(TAG, "Is playing changed: $playing")
                            if (!isBuffering) {
                                updateState(isPlaying = playing, isBuffering = false)
                            }
                        }

                        override fun onMetadata(metadata: Metadata) {
                            Log.d(TAG, "Metadata received: ${metadata.length()} entries")

                            for (i in 0 until metadata.length()) {
                                val entry = metadata.get(i)
                                Log.d(TAG, "Metadata entry: ${entry.javaClass.simpleName}")

                                when (entry) {
                                    is IcyInfo -> {
                                        val title = entry.title
                                        Log.d(TAG, "ICY Info - Title: $title")

                                        if (!title.isNullOrBlank()) {
                                            parseTrackInfo(title)
                                            updateMediaMetadata()
                                            updateNotification()
                                            notifyMainActivityTrackChanged()
                                        }
                                    }
                                    is IcyHeaders -> {
                                        Log.d(TAG, "ICY Headers - Name: ${entry.name}, Genre: ${entry.genre}")
                                    }
                                }
                            }
                        }
                    }

                    addListener(playerListener)
                }

            Log.d(TAG, "ExoPlayer configured successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error setting up ExoPlayer", e)
        }
    }

    private fun updateState(isPlaying: Boolean, isBuffering: Boolean) {
        Log.d(TAG, "Updating state - Playing: $isPlaying, Buffering: $isBuffering")

        this.isPlaying = isPlaying
        this.isBuffering = isBuffering

        updateNotification()
        updatePlaybackState()
        notifyMainActivity(isPlaying)

        Log.d(TAG, "State updated and LOCAL broadcasted")
    }

    private fun parseTrackInfo(rawTitle: String) {
        try {
            currentTrackInfo = rawTitle.trim()

            when {
                rawTitle.contains(" - ") -> {
                    val parts = rawTitle.split(" - ", limit = 2)
                    if (parts.size == 2) {
                        val first = parts[0].trim()
                        val second = parts[1].trim()

                        if (first.length > second.length * 1.5) {
                            currentTrackTitle = first
                            currentTrackArtist = second
                        } else {
                            currentTrackArtist = first
                            currentTrackTitle = second
                        }
                    }
                }
                rawTitle.contains(": ") -> {
                    val parts = rawTitle.split(": ", limit = 2)
                    if (parts.size == 2) {
                        currentTrackArtist = parts[0].trim()
                        currentTrackTitle = parts[1].trim()
                    }
                }
                rawTitle.contains(" by ") -> {
                    val parts = rawTitle.split(" by ", limit = 2)
                    if (parts.size == 2) {
                        currentTrackTitle = parts[0].trim()
                        currentTrackArtist = parts[1].trim()
                    }
                }
                else -> {
                    currentTrackTitle = rawTitle
                    currentTrackArtist = null
                }
            }

            Log.d(TAG, "Parsed track - Artist: '$currentTrackArtist', Title: '$currentTrackTitle'")

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing track info: $rawTitle", e)
            currentTrackInfo = rawTitle
            currentTrackTitle = rawTitle
            currentTrackArtist = null
        }
    }

    private fun getDisplayTrackInfo(): String {
        return when {
            !currentTrackArtist.isNullOrBlank() && !currentTrackTitle.isNullOrBlank() -> {
                "$currentTrackArtist - $currentTrackTitle"
            }
            !currentTrackTitle.isNullOrBlank() -> {
                currentTrackTitle!!
            }
            !currentTrackInfo.isNullOrBlank() -> {
                currentTrackInfo!!
            }
            isBuffering -> "Ładowanie..."
            isPlaying -> "Na żywo"
            else -> "Wstrzymane"
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Radio Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for radio playback"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")

        MediaButtonReceiver.handleIntent(mediaSession, intent)

        when (intent?.action) {
            ACTION_PLAY -> {
                val stationUrl = intent.getStringExtra(EXTRA_STATION_URL)
                val stationName = intent.getStringExtra(EXTRA_STATION_NAME)
                val iconRes = intent.getIntExtra(EXTRA_ICON_RES, 0)

                Log.d(TAG, "Play action - URL: $stationUrl, Name: $stationName")

                if (stationUrl != null) {
                    currentStationUrl = stationUrl
                    currentStationName = stationName
                    currentIconRes = iconRes

                    clearTrackInfo()
                    updateState(isPlaying = false, isBuffering = true)
                    playRadio(stationUrl)

                    serviceScope.launch {
                        delay(1000)
                        Log.d(TAG, "=== TESTING LOCAL BROADCAST AFTER 1 SECOND ===")
                        notifyMainActivity(isPlaying)
                    }
                }
            }
            ACTION_STOP -> {
                Log.d(TAG, "Stop action")
                stopRadio()
            }
            ACTION_PAUSE -> {
                Log.d(TAG, "Pause action")
                pauseRadio()
            }
        }

        return START_STICKY
    }

    private fun clearTrackInfo() {
        currentTrackTitle = null
        currentTrackArtist = null
        currentTrackInfo = null
    }

    private fun resumeRadio() {
        Log.d(TAG, "Resuming radio")

        currentStationUrl?.let { url ->
            if (requestAudioFocus()) {
                exoPlayer?.play()
                updateState(isPlaying = true, isBuffering = false)
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        Log.d(TAG, "Requesting audio focus")

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(this)
                .build()

            audioManager?.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }

        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Log.d(TAG, "Audio focus result: $result, hasAudioFocus: $hasAudioFocus")

        return hasAudioFocus
    }

    private fun abandonAudioFocus() {
        Log.d(TAG, "Abandoning audio focus")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                audioManager?.abandonAudioFocusRequest(it)
                audioFocusRequest = null
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(this)
        }

        hasAudioFocus = false
    }

    override fun onAudioFocusChange(focusChange: Int) {
        Log.d(TAG, "Audio focus change: $focusChange")

        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus gained")
                hasAudioFocus = true
                exoPlayer?.volume = 1.0f

                if (currentStationUrl != null && !isPlaying) {
                    exoPlayer?.play()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(TAG, "Audio focus lost permanently")
                hasAudioFocus = false
                stopRadio()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "Audio focus lost temporarily")
                hasAudioFocus = false
                pauseRadio()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "Audio focus lost - can duck")
                exoPlayer?.volume = 0.3f
            }
        }
    }

    private fun playRadio(url: String) {
        Log.d(TAG, "Playing radio: $url")

        try {
            if (!requestAudioFocus()) {
                Log.w(TAG, "Could not get audio focus")
                updateState(isPlaying = false, isBuffering = false)
                return
            }

            exoPlayer?.apply {
                stop()
                clearMediaItems()

                val mediaItem = MediaItem.Builder()
                    .setUri(url)
                    .setLiveConfiguration(
                        MediaItem.LiveConfiguration.Builder()
                            .setMaxPlaybackSpeed(1.02f)
                            .setMinPlaybackSpeed(0.98f)
                            .build()
                    )
                    .build()

                setMediaItem(mediaItem)
                prepare()
                play()
            }

            updateMediaMetadata()
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)

            Log.d(TAG, "Radio playback started")

        } catch (e: Exception) {
            Log.e(TAG, "Error playing radio", e)
            updateState(isPlaying = false, isBuffering = false)
            handlePlaybackError()
        }
    }

    private fun pauseRadio() {
        Log.d(TAG, "Pausing radio")

        exoPlayer?.pause()
        updateState(isPlaying = false, isBuffering = false)
    }

    private fun stopRadio() {
        Log.d(TAG, "Stopping radio")

        retryJob?.cancel()

        exoPlayer?.apply {
            stop()
            clearMediaItems()
        }

        clearTrackInfo()
        abandonAudioFocus()
        updateState(isPlaying = false, isBuffering = false)

        stopForeground(true)
        stopSelf()
    }

    private fun handlePlaybackError() {
        Log.d(TAG, "Handling playback error, retry count: $retryCount")

        if (retryCount < maxRetries && hasAudioFocus) {
            retryCount++
            Log.d(TAG, "Retrying playback, attempt $retryCount/$maxRetries")

            retryJob?.cancel()
            retryJob = serviceScope.launch {
                delay(2000L * retryCount)
                currentStationUrl?.let {
                    Log.d(TAG, "Retry attempt $retryCount for URL: $it")
                    playRadio(it)
                }
            }
        } else {
            Log.e(TAG, "Max retries reached or no audio focus, stopping service")
            stopRadio()
        }
    }

    private fun updateMediaMetadata() {
        val title = currentTrackTitle ?: currentStationName ?: "Radio24"
        val artist = currentTrackArtist ?: "Na żywo"

        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, currentStationName ?: "Radio24")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1L)
            .build()

        mediaSession?.setMetadata(metadata)
    }

    private fun updatePlaybackState() {
        val state = when {
            isBuffering -> PlaybackStateCompat.STATE_BUFFERING
            isPlaying -> PlaybackStateCompat.STATE_PLAYING
            else -> PlaybackStateCompat.STATE_PAUSED
        }

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_STOP
            )
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            .build()

        mediaSession?.setPlaybackState(playbackState)
    }

    private fun createNotification(): Notification {
        Log.d(TAG, "Creating notification - isPlaying: $isPlaying, isBuffering: $isBuffering")

        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause,
                "Pause",
                createPendingIntent(ACTION_PAUSE, 1)
            ).build()
        } else {
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play,
                "Play",
                createPendingIntent(ACTION_PLAY, 2)
            ).build()
        }

        val stopAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_delete,
            "Stop",
            createPendingIntent(ACTION_STOP, 3)
        ).build()

        val largeIcon = try {
            if (currentIconRes != 0) {
                BitmapFactory.decodeResource(resources, currentIconRes)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error loading large icon", e)
            null
        }

        val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            mainActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayTrackInfo = getDisplayTrackInfo()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.radio24_trans)
            .setContentTitle(currentStationName ?: "Radio24")
            .setContentText(displayTrackInfo)
            .setLargeIcon(largeIcon)
            .setContentIntent(contentIntent)
            .addAction(playPauseAction)
            .addAction(stopAction)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
            .setOngoing(isPlaying)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .build()
    }

    private fun updateNotification() {
        Log.d(TAG, "Updating notification - isPlaying: $isPlaying, isBuffering: $isBuffering")

        try {
            val notification = createNotification()
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "Notification updated successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification", e)
        }
    }

    private fun createPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, RadioService::class.java).apply {
            this.action = action

            if (action == ACTION_PLAY) {
                putExtra(EXTRA_STATION_URL, currentStationUrl)
                putExtra(EXTRA_STATION_NAME, currentStationName)
                putExtra(EXTRA_ICON_RES, currentIconRes)
            }
        }

        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun notifyMainActivity(playing: Boolean) {
        Log.d(TAG, "=== PREPARING LOCAL BROADCAST ===")
        Log.d(TAG, "Sending LOCAL broadcast - Playing: $playing, Buffering: $isBuffering")
        Log.d(TAG, "LOCAL Broadcast action: $BROADCAST_PLAYBACK_STATE")

        val intent = Intent(BROADCAST_PLAYBACK_STATE).apply {
            putExtra(EXTRA_IS_PLAYING, playing)
            putExtra(EXTRA_IS_BUFFERING, isBuffering)
            // Dodaj dodatkowe informacje dla debugowania
            putExtra("EXTRA_STATION_NAME", currentStationName)
            putExtra("EXTRA_TIMESTAMP", System.currentTimeMillis())
        }

        try {
            localBroadcastManager.sendBroadcast(intent)
            Log.d(TAG, "=== LOCAL BROADCAST SENT SUCCESSFULLY ===")
            Log.d(TAG, "Intent extras: Playing=$playing, Buffering=$isBuffering, Station=$currentStationName")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending LOCAL broadcast: ${e.message}", e)
        }
    }

    private fun notifyMainActivityTrackChanged() {
        val intent = Intent(BROADCAST_TRACK_CHANGED).apply {
            putExtra(EXTRA_TRACK_TITLE, currentTrackTitle)
            putExtra(EXTRA_TRACK_ARTIST, currentTrackArtist)
            putExtra(EXTRA_TRACK_INFO, getDisplayTrackInfo())
        }
        localBroadcastManager.sendBroadcast(intent)
        Log.d(TAG, "Track change LOCAL broadcast sent")
    }

    fun isCurrentlyPlaying(): Boolean {
        Log.d(TAG, "isCurrentlyPlaying() called - returning: $isPlaying")
        return isPlaying
    }

    fun isCurrentlyBuffering(): Boolean {
        Log.d(TAG, "isCurrentlyBuffering() called - returning: $isBuffering")
        return isBuffering
    }

    fun getCurrentStation(): String? = currentStationName
    fun getCurrentTrackInfo(): String = getDisplayTrackInfo()

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "Service bound")
        return binder
    }

    override fun onDestroy() {
        Log.d(TAG, "RadioService destroyed")

        serviceScope.cancel()
        retryJob?.cancel()

        exoPlayer?.release()
        exoPlayer = null

        mediaSession?.release()
        mediaSession = null

        abandonAudioFocus()

        super.onDestroy()
    }

    companion object {
        private const val TAG = "RadioService"
        const val CHANNEL_ID = "radio_channel"
        const val NOTIFICATION_ID = 1

        const val ACTION_PLAY = "ACTION_PLAY"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_STOP = "ACTION_STOP"

        const val EXTRA_STATION_URL = "EXTRA_STATION_URL"
        const val EXTRA_STATION_NAME = "EXTRA_STATION_NAME"
        const val EXTRA_ICON_RES = "EXTRA_ICON_RES"
        const val EXTRA_IS_PLAYING = "EXTRA_IS_PLAYING"
        const val EXTRA_IS_BUFFERING = "EXTRA_IS_BUFFERING"

        const val EXTRA_TRACK_TITLE = "EXTRA_TRACK_TITLE"
        const val EXTRA_TRACK_ARTIST = "EXTRA_TRACK_ARTIST"
        const val EXTRA_TRACK_INFO = "EXTRA_TRACK_INFO"

        const val BROADCAST_PLAYBACK_STATE = "net.gf.radio24.PLAYBACK_STATE"
        const val BROADCAST_TRACK_CHANGED = "net.gf.radio24.TRACK_CHANGED"
    }
}
