package net.gf.radio24

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.exoplayer2.ExoPlayer
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject
import java.io.File

class CarActivity : AppCompatActivity() {
    private var exoPlayer: ExoPlayer? = null
    private var radioService: RadioService? = null
    private var isServiceBound = false
    private lateinit var localBroadcastManager: LocalBroadcastManager
    private lateinit var playbackStateReceiver: BroadcastReceiver
    private var trackChangeReceiver: BroadcastReceiver? = null

    data class PlayerState(
        val isPlaying: Boolean = false,
        val isBuffering: Boolean = false,
        val url: String = "",
        val city: String = "",
        val stationName: String = "",
        val icon: String = "",
        val trackInfo: String = ""
    )

    private var currentPlayerState = PlayerState()
    private val DATABASE_DIR = "database"
    private val PLAYER_FILE = "player.json"

    private fun getPlayerFile(): File = File(File(filesDir, DATABASE_DIR), PLAYER_FILE)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as RadioService.RadioBinder
            radioService = binder.getService()
            isServiceBound = true
            updatePlayerStateFromService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            radioService = null
            isServiceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_car)

        setupWindowInsets()

        exoPlayer = ExoPlayer.Builder(application).build()
        localBroadcastManager = LocalBroadcastManager.getInstance(this)
        setupBroadcastReceiver()
        loadPlayerState()
        updateUI()
        bindToRadioService()
        FirebaseFirestore.setLoggingEnabled(true)

        findViewById<ImageView>(R.id.radio_exit).setOnClickListener {
            navigateToMainActivity()
        }

        findViewById<ImageView>(R.id.radio_player).setOnClickListener {
            togglePlayPause()
        }
    }

    private fun setupWindowInsets() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    )
        }
    }

    private fun setupBroadcastReceiver() {
        playbackStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == RadioService.BROADCAST_PLAYBACK_STATE) {
                    val isPlaying = intent.getBooleanExtra(RadioService.EXTRA_IS_PLAYING, false)
                    val isBuffering = intent.getBooleanExtra(RadioService.EXTRA_IS_BUFFERING, false)
                    currentPlayerState = currentPlayerState.copy(
                        isPlaying = isPlaying,
                        isBuffering = isBuffering
                    )
                    savePlayerState()
                    updateUI()
                }
            }
        }

        trackChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == RadioService.BROADCAST_TRACK_CHANGED) {
                    val trackInfo = intent.getStringExtra(RadioService.EXTRA_TRACK_INFO)
                    updateTrackInfo(trackInfo ?: "")
                }
            }
        }

        try {
            localBroadcastManager.registerReceiver(playbackStateReceiver, IntentFilter(RadioService.BROADCAST_PLAYBACK_STATE))
            localBroadcastManager.registerReceiver(trackChangeReceiver!!, IntentFilter(RadioService.BROADCAST_TRACK_CHANGED))
        } catch (e: Exception) {
            Log.e("CarActivity", "Error registering LOCAL broadcast receivers: ${e.message}")
        }
    }

    private fun updateTrackInfo(trackInfo: String) {
        try {
            val trackInfoView: TextView = findViewById(R.id.radio_track_info)
            val cityInfoView: TextView = findViewById(R.id.radio_city_info)
            if (trackInfo.isNotEmpty() && trackInfo != "Na żywo" && trackInfo != "Wstrzymane") {
                trackInfoView.text = trackInfo
                currentPlayerState = currentPlayerState.copy(trackInfo = trackInfo)
                savePlayerState()
            } else {
                cityInfoView.text = currentPlayerState.city.takeIf { it.isNotEmpty() } ?: "Brak informacji"
            }
        } catch (e: Exception) {
            Log.e("updateTrackInfo", "Error updating track info: ${e.message}")
        }
    }

    private fun bindToRadioService() {
        val intent = Intent(this, RadioService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun updatePlayerStateFromService() {
        radioService?.let { service ->
            val serviceIsPlaying = service.isCurrentlyPlaying()
            val serviceIsBuffering = service.isCurrentlyBuffering()
            val trackInfo = service.getCurrentTrackInfo()
            currentPlayerState = currentPlayerState.copy(
                isPlaying = serviceIsPlaying,
                isBuffering = serviceIsBuffering,
                trackInfo = trackInfo
            )
            if (trackInfo.isNotEmpty()) {
                updateTrackInfo(trackInfo)
            }
            savePlayerState()
            updateUI()
        }
    }

    private fun loadPlayerState() {
        val file = getPlayerFile()
        if (!file.exists()) return
        try {
            val jsonString = file.readText().takeIf { it.isNotEmpty() } ?: return
            val jsonObject = JSONObject(jsonString)
            currentPlayerState = PlayerState(
                isPlaying = jsonObject.optBoolean("isPlaying", false),
                isBuffering = jsonObject.optBoolean("isBuffering", false),
                url = jsonObject.optString("url", ""),
                city = jsonObject.optString("city", ""),
                stationName = jsonObject.optString("stationName", ""),
                icon = jsonObject.optString("icon", ""),
                trackInfo = jsonObject.optString("trackInfo", "")
            )
        } catch (e: Exception) {
            Log.e("loadPlayerState", "Error loading player state: ${e.message}")
        }
    }

    private fun savePlayerState() {
        try {
            val jsonObject = JSONObject().apply {
                put("isPlaying", currentPlayerState.isPlaying)
                put("isBuffering", currentPlayerState.isBuffering)
                put("url", currentPlayerState.url)
                put("city", currentPlayerState.city)
                put("stationName", currentPlayerState.stationName)
                put("icon", currentPlayerState.icon)
                put("trackInfo", currentPlayerState.trackInfo)
            }
            getPlayerFile().writeText(jsonObject.toString())
        } catch (e: Exception) {
            Log.e("savePlayerState", "Error saving player state: ${e.message}")
        }
    }

    private fun updateUI() {
        try {
            val iconViewPlayer: ImageView = findViewById(R.id.radio_icon_player)
            val nameViewPlayer: TextView = findViewById(R.id.radio_name_player)
            val playButton: ImageView = findViewById(R.id.radio_player)
            val textPlayStatus: TextView = findViewById(R.id.text_car_play)
            val trackInfoView: TextView = findViewById(R.id.radio_track_info)
            val cityInfoView: TextView = findViewById(R.id.radio_city_info)

            if (currentPlayerState.icon.isNotEmpty()) {
                val iconResId = resources.getIdentifier(
                    currentPlayerState.icon.replace("@drawable/", ""),
                    "drawable",
                    packageName
                )
                if (iconResId != 0) {
                    iconViewPlayer.setImageResource(iconResId)
                }
            }

            nameViewPlayer.text = currentPlayerState.stationName.takeIf { it.isNotEmpty() }
                ?: "Nie wybrano radia"

            if (currentPlayerState.trackInfo.isNotEmpty() &&
                currentPlayerState.trackInfo != "Na żywo" &&
                currentPlayerState.trackInfo != "Wstrzymane") {
                trackInfoView.text = currentPlayerState.trackInfo
                cityInfoView.text = currentPlayerState.city.takeIf { it.isNotEmpty() } ?: ""
            } else {
                trackInfoView.text = when {
                    currentPlayerState.isBuffering -> "Ładowanie..."
                    currentPlayerState.isPlaying -> "Na żywo"
                    else -> "Wstrzymane"
                }
                cityInfoView.text = currentPlayerState.city.takeIf { it.isNotEmpty() } ?: "Brak informacji"
            }

            val (buttonResource, statusText) = when {
                currentPlayerState.isBuffering -> R.drawable.loading_button to "Ładowanie..."
                currentPlayerState.isPlaying -> R.drawable.pause_button to "Pause"
                else -> R.drawable.play_button to "Play"
            }

            playButton.setImageResource(buttonResource)
            textPlayStatus.text = statusText
        } catch (e: Exception) {
            Log.e("updateUI", "Error updating UI: ${e.message}")
        }
    }

    private fun togglePlayPause() {
        if (currentPlayerState.url.isEmpty()) {
            Toast.makeText(this, "Brak danych o stacji radiowej!", Toast.LENGTH_SHORT).show()
            return
        }

        val iconResId = resources.getIdentifier(
            currentPlayerState.icon.replace("@drawable/", ""), "drawable", packageName
        )

        val intent = Intent(this, RadioService::class.java).apply {
            putExtra(RadioService.EXTRA_STATION_URL, currentPlayerState.url)
            putExtra(RadioService.EXTRA_STATION_NAME, currentPlayerState.stationName)
            putExtra(RadioService.EXTRA_ICON_RES, iconResId)
        }

        when {
            currentPlayerState.isPlaying -> {
                intent.action = RadioService.ACTION_PAUSE
            }
            currentPlayerState.isBuffering -> {
                intent.action = RadioService.ACTION_STOP
            }
            else -> {
                intent.action = RadioService.ACTION_PLAY
                currentPlayerState = currentPlayerState.copy(isBuffering = true, isPlaying = false)
                updateUI()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onResume() {
        super.onResume()
        loadPlayerState()
        updateUI()
        if (!isServiceBound) {
            bindToRadioService()
        } else {
            updatePlayerStateFromService()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
        try {
            localBroadcastManager.unregisterReceiver(playbackStateReceiver)
            trackChangeReceiver?.let { localBroadcastManager.unregisterReceiver(it) }
        } catch (e: Exception) {
            Log.e("onDestroy", "Error unregistering LOCAL receiver: ${e.message}")
        }
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
        savePlayerState()
    }
}