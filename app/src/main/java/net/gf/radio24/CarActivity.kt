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
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
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
            Log.d("CarActivity", "Service connected - syncing state")
            updatePlayerStateFromService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            radioService = null
            isServiceBound = false
            Log.d("CarActivity", "Service disconnected")
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_car)

        window.setDecorFitsSystemWindows(false)

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
            togglePlayPause(it as ImageView)
        }

//        findViewById<ImageView>(R.id.radio_favorite).setOnClickListener {
//            toggleFavorite()
//        }
//
//        findViewById<ImageView>(R.id.radio_search).setOnClickListener {
//            Toast.makeText(this, "Funkcja wyszukiwania w przygotowaniu", Toast.LENGTH_SHORT).show()
//        }
    }

    private fun setupBroadcastReceiver() {
        Log.d("CarActivity", "Setting up LOCAL broadcast receivers")

        playbackStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d("CarActivity", "LOCAL BroadcastReceiver onReceive called with action: ${intent?.action}")

                if (intent?.action == RadioService.BROADCAST_PLAYBACK_STATE) {
                    val isPlaying = intent.getBooleanExtra(RadioService.EXTRA_IS_PLAYING, false)
                    val isBuffering = intent.getBooleanExtra(RadioService.EXTRA_IS_BUFFERING, false)
                    val stationName = intent.getStringExtra("EXTRA_STATION_NAME")

                    Log.d("CarActivity", "LOCAL BROADCAST RECEIVED - Playing: $isPlaying, Buffering: $isBuffering, Station: $stationName")

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
                    Log.d("CarActivity", "Track change LOCAL broadcast received - Info: $trackInfo")
                    updateTrackInfo(trackInfo ?: "")
                }
            }
        }

        try {
            localBroadcastManager.registerReceiver(playbackStateReceiver, IntentFilter(RadioService.BROADCAST_PLAYBACK_STATE))
            localBroadcastManager.registerReceiver(trackChangeReceiver!!, IntentFilter(RadioService.BROADCAST_TRACK_CHANGED))
            Log.d("CarActivity", "LOCAL broadcast receivers registered successfully")
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
                Log.d("CarActivity", "Track info updated to: $trackInfo")
            } else {
                cityInfoView.text = currentPlayerState.city.takeIf { it.isNotEmpty() } ?: "Brak informacji"
            }
        } catch (e: Exception) {
            Log.e("updateTrackInfo", "Error updating track info: ${e.message}")
        }
    }

    private fun bindToRadioService() {
        Log.d("CarActivity", "Binding to RadioService")
        val intent = Intent(this, RadioService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun updatePlayerStateFromService() {
        radioService?.let { service ->
            val serviceIsPlaying = service.isCurrentlyPlaying()
            val serviceIsBuffering = service.isCurrentlyBuffering()
            val trackInfo = service.getCurrentTrackInfo()

            Log.d("CarActivity", "Syncing with service - Service Playing: $serviceIsPlaying, Service Buffering: $serviceIsBuffering")

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

            Log.d("CarActivity", "Player state loaded - Playing: ${currentPlayerState.isPlaying}, Station: ${currentPlayerState.stationName}")
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
            Log.d("CarActivity", "Player state saved - Playing: ${currentPlayerState.isPlaying}")
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

            updateFavoriteButton()

            Log.d("CarActivity", "UI updated - Playing: ${currentPlayerState.isPlaying}, Buffering: ${currentPlayerState.isBuffering}")

        } catch (e: Exception) {
            Log.e("updateUI", "Error updating UI: ${e.message}")
        }
    }

    private fun updateFavoriteButton() {
        try {
            val favoriteButton: ImageView = findViewById(R.id.radio_favorite)
            val favorites = getFavorites()

            val isFavorite = favorites.contains(currentPlayerState.stationName)
            favoriteButton.setBackgroundResource(
                if (isFavorite) R.drawable.heart_active else R.drawable.heart
            )
        } catch (e: Exception) {
            Log.e("updateFavoriteButton", "Error updating favorite button: ${e.message}")
        }
    }

    private fun toggleFavorite() {
        if (currentPlayerState.stationName.isEmpty()) {
            Toast.makeText(this, "Brak wybranej stacji!", Toast.LENGTH_SHORT).show()
            return
        }

        val favorites = getFavorites()
        val wasFavorite = favorites.contains(currentPlayerState.stationName)

        if (wasFavorite) {
            favorites.remove(currentPlayerState.stationName)
            Toast.makeText(this, "Usunięto z ulubionych", Toast.LENGTH_SHORT).show()
        } else {
            favorites.add(currentPlayerState.stationName)
            Toast.makeText(this, "Dodano do ulubionych", Toast.LENGTH_SHORT).show()
        }

        saveFavorites(favorites)
        updateFavoriteButton()
    }

    private fun getFavorites(): MutableSet<String> {
        val file = File(File(filesDir, DATABASE_DIR), "favorites.json")
        if (!file.exists()) return mutableSetOf()

        return try {
            val json = file.readText()
            if (json.isNotEmpty()) {
                val jsonObject = JSONObject(json)
                val favoritesArray = jsonObject.optJSONArray("favorites")
                val favorites = mutableSetOf<String>()

                favoritesArray?.let {
                    for (i in 0 until it.length()) {
                        favorites.add(it.getString(i))
                    }
                }
                favorites
            } else {
                mutableSetOf()
            }
        } catch (e: Exception) {
            Log.e("getFavorites", "Error reading favorites: ${e.message}")
            mutableSetOf()
        }
    }

    private fun saveFavorites(favorites: MutableSet<String>) {
        try {
            val file = File(File(filesDir, DATABASE_DIR), "favorites.json")
            val jsonObject = JSONObject().apply {
                put("favorites", org.json.JSONArray(favorites.toList()))
            }
            file.writeText(jsonObject.toString())
        } catch (e: Exception) {
            Log.e("saveFavorites", "Error saving favorites: ${e.message}")
        }
    }

    private fun togglePlayPause(viewPlayer: ImageView) {
        if (currentPlayerState.url.isEmpty()) {
            Toast.makeText(this, "Brak danych o stacji radiowej!", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("CarActivity", "Toggle play/pause - Current state: Playing=${currentPlayerState.isPlaying}, Buffering=${currentPlayerState.isBuffering}")

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
                Log.d("CarActivity", "Sending PAUSE action")
            }
            currentPlayerState.isBuffering -> {
                intent.action = RadioService.ACTION_STOP
                Log.d("CarActivity", "Sending STOP action (interrupting buffering)")
            }
            else -> {
                intent.action = RadioService.ACTION_PLAY
                currentPlayerState = currentPlayerState.copy(isBuffering = true, isPlaying = false)
                updateUI()
                Log.d("CarActivity", "Sending PLAY action")
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
        Log.d("CarActivity", "onResume - loading state and updating UI")

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
        Log.d("CarActivity", "onDestroy called")

        exoPlayer?.release()
        exoPlayer = null

        try {
            localBroadcastManager.unregisterReceiver(playbackStateReceiver)
            trackChangeReceiver?.let { localBroadcastManager.unregisterReceiver(it) }
            Log.d("CarActivity", "LOCAL broadcast receivers unregistered")
        } catch (e: Exception) {
            Log.e("onDestroy", "Error unregistering LOCAL receiver: ${e.message}")
        }

        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
            Log.d("CarActivity", "Service unbound")
        }

        savePlayerState()
    }
}