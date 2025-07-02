package net.gf.radio24

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import org.json.JSONObject
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var consentInformation: ConsentInformation
    private var nightMode: Boolean = false
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var playbackStateReceiver: BroadcastReceiver
    private var trackChangeReceiver: BroadcastReceiver? = null
    private var exoPlayer: ExoPlayer? = null

    private var radioService: RadioService? = null
    private var isServiceBound = false

    private lateinit var localBroadcastManager: LocalBroadcastManager

    data class Swiatowe(val country: String, val icon: String, val stations: List<RadioSwiatowe>)
    data class RadioSwiatowe(val name: String, val city: String, val url: String, val icon: String)
    data class Wojewodztwo(val woj: String, val icon: String, val stations: List<RadioStationOkolica>)
    data class RadioStationOkolica(val name: String, val city: String, val url: String, val icon: String)
    data class RadioStation(val name: String, val city: String, val url: String, val icon: String)
    data class Top10popStation(val name: String, val city: String, val url: String, val icon: String)

    data class PlayerState(
        val isPlaying: Boolean = false,
        val isBuffering: Boolean = false,
        val url: String = "",
        val city: String = "",
        val stationName: String = "",
        val icon: String = ""
    )

    private var currentPlayerState = PlayerState()

    private var radioStationsCache: List<RadioStation>? = null
    private var radioOkolicaCache: List<Wojewodztwo>? = null
    private var radioSwiatoweCache: List<Swiatowe>? = null
    private var radioTop10popCache: List<Top10popStation>? = null

    private val DATABASE_DIR = "database"
    private val PLAYER_FILE = "player.json"
    private val FAVORITES_FILE = "favorites.json"
    private val STATIONS_FILE = "stations.json"
    private val OKOLICE_FILE = "okolice.json"
    private val SWIAT_FILE = "swiat.json"
    private val TOP10POP_FILE = "top10pop.json"

    private fun getPlayerFile(): File = File(File(filesDir, DATABASE_DIR), PLAYER_FILE)
    private fun getFavoritesFile(): File = File(File(filesDir, DATABASE_DIR), FAVORITES_FILE)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as RadioService.RadioBinder
            radioService = binder.getService()
            isServiceBound = true

            Log.d("MainActivity", "Service connected - forcing immediate sync")
            updatePlayerUIFromService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            radioService = null
            isServiceBound = false
            Log.d("MainActivity", "Service disconnected")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        exoPlayer = ExoPlayer.Builder(application).build()
        sharedPreferences = getSharedPreferences("MODE", MODE_PRIVATE)
        nightMode = sharedPreferences.getBoolean("nightMode", false)

        localBroadcastManager = LocalBroadcastManager.getInstance(this)

        initializeDatabase()
        initializeConsent()
        initializeAds()
        FirebaseFirestore.setLoggingEnabled(true)
        createNotificationChannel()
        setupBroadcastReceiver()

        val container: LinearLayout = findViewById(R.id.container)
        loadLayout(R.layout.radio_krajowe, container)
        loadDataFromAPI()

        findViewById<ImageView>(R.id.radio_player).setOnClickListener {
            togglePlayPause(it as ImageView)
        }

        bindToRadioService()
    }

    override fun onResume() {
        super.onResume()

        Log.d("MainActivity", "onResume - loading state and updating UI")

        loadPlayerState()
        updatePlayerUI()

        if (!isServiceBound) {
            bindToRadioService()
        } else {
            updatePlayerUIFromService()
        }
    }

    override fun onStart() {
        super.onStart()
        AppCompatDelegate.setDefaultNightMode(
            if (nightMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        Log.d("MainActivity", "onStart - forcing sync if service bound")
        if (isServiceBound) {
            forceSyncWithService()
        }
    }

    private fun bindToRadioService() {
        Log.d("MainActivity", "Binding to RadioService")
        val intent = Intent(this, RadioService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupBroadcastReceiver() {
        Log.d("MainActivity", "Setting up LOCAL broadcast receivers")

        playbackStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d("MainActivity", "LOCAL BroadcastReceiver onReceive called with action: ${intent?.action}")

                if (intent?.action == RadioService.BROADCAST_PLAYBACK_STATE) {
                    val isPlaying = intent.getBooleanExtra(RadioService.EXTRA_IS_PLAYING, false)
                    val isBuffering = intent.getBooleanExtra(RadioService.EXTRA_IS_BUFFERING, false)
                    val stationName = intent.getStringExtra("EXTRA_STATION_NAME")
                    val timestamp = intent.getLongExtra("EXTRA_TIMESTAMP", 0)

                    Log.d("MainActivity", "LOCAL BROADCAST RECEIVED - Playing: $isPlaying, Buffering: $isBuffering, Station: $stationName, Time: $timestamp")

                    currentPlayerState = currentPlayerState.copy(
                        isPlaying = isPlaying,
                        isBuffering = isBuffering
                    )

                    savePlayerState()
                    updatePlayerUI()

                    Log.d("MainActivity", "UI updated after LOCAL broadcast")
                }
            }
        }

        trackChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == RadioService.BROADCAST_TRACK_CHANGED) {
                    val trackTitle = intent.getStringExtra(RadioService.EXTRA_TRACK_TITLE)
                    val trackArtist = intent.getStringExtra(RadioService.EXTRA_TRACK_ARTIST)
                    val trackInfo = intent.getStringExtra(RadioService.EXTRA_TRACK_INFO)

                    Log.d("MainActivity", "Track change LOCAL broadcast received - Info: $trackInfo")

                    updateTrackInfo(trackInfo ?: "")
                }
            }
        }

        try {
            localBroadcastManager.registerReceiver(playbackStateReceiver, IntentFilter(RadioService.BROADCAST_PLAYBACK_STATE))
            localBroadcastManager.registerReceiver(trackChangeReceiver!!, IntentFilter(RadioService.BROADCAST_TRACK_CHANGED))
            Log.d("MainActivity", "LOCAL broadcast receivers registered successfully")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error registering LOCAL broadcast receivers: ${e.message}")
        }
    }

    private fun updateTrackInfo(trackInfo: String) {
        try {
            val cityViewPlayer: TextView = findViewById(R.id.radio_container_city)
            if (trackInfo.isNotEmpty() && trackInfo != "Na żywo" && trackInfo != "Wstrzymane") {
                cityViewPlayer.text = trackInfo
                Log.d("MainActivity", "Track info updated to: $trackInfo")
            }
        } catch (e: Exception) {
            Log.e("updateTrackInfo", "Error updating track info: ${e.message}")
        }
    }

    private fun updatePlayerUIFromService() {
        radioService?.let { service ->
            val serviceIsPlaying = service.isCurrentlyPlaying()
            val serviceIsBuffering = service.isCurrentlyBuffering()

            Log.d("MainActivity", "Syncing with service - Service Playing: $serviceIsPlaying, Service Buffering: $serviceIsBuffering")

            currentPlayerState = currentPlayerState.copy(
                isPlaying = serviceIsPlaying,
                isBuffering = serviceIsBuffering
            )

            val trackInfo = service.getCurrentTrackInfo()
            if (trackInfo.isNotEmpty()) {
                updateTrackInfo(trackInfo)
            }

            savePlayerState()
            updatePlayerUI()

            Log.d("MainActivity", "Service sync completed")
        } ?: Log.w("MainActivity", "Cannot sync - service is null")
    }

    private fun forceSyncWithService() {
        Log.d("MainActivity", "Forcing sync with service")
        if (isServiceBound) {
            updatePlayerUIFromService()
        } else {
            Log.w("MainActivity", "Cannot force sync - service not bound")
        }
    }

    private fun initializeDatabase() {
        try {
            val databaseDir = File(filesDir, DATABASE_DIR)
            if (!databaseDir.exists()) {
                databaseDir.mkdirs()
                Log.d("Database", "Created database directory")
            }

            val playerFile = getPlayerFile()
            if (!playerFile.exists()) {
                savePlayerState()
                Log.d("Database", "Created player.json with default data")
            }

            val favoritesFile = getFavoritesFile()
            if (!favoritesFile.exists()) {
                saveFavorites(this, mutableSetOf())
                Log.d("Database", "Created favorites.json with empty array")
            }
        } catch (e: Exception) {
            Log.e("Database", "Error initializing database: ${e.message}")
            Toast.makeText(this, "Błąd podczas inicjalizacji bazy danych", Toast.LENGTH_SHORT).show()
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
            }
            getPlayerFile().writeText(jsonObject.toString())
            Log.d("MainActivity", "Player state saved - Playing: ${currentPlayerState.isPlaying}, Buffering: ${currentPlayerState.isBuffering}")
        } catch (e: Exception) {
            Log.e("savePlayerState", "Error saving player state: ${e.message}")
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
                icon = jsonObject.optString("icon", "")
            )

            Log.d("MainActivity", "Player state loaded - Playing: ${currentPlayerState.isPlaying}, Buffering: ${currentPlayerState.isBuffering}")
        } catch (e: Exception) {
            Log.e("loadPlayerState", "Error loading player state: ${e.message}")
        }
    }

    private fun updatePlayerUI() {
        try {
            val iconViewPlayer: ImageView = findViewById(R.id.radio_icon_player)
            val nameViewPlayer: TextView = findViewById(R.id.radio_name_player)
            val cityViewPlayer: TextView = findViewById(R.id.radio_container_city)
            val playButton: ImageView = findViewById(R.id.radio_player)

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

            cityViewPlayer.text = when {
                currentPlayerState.isBuffering -> "Ładowanie..."
                currentPlayerState.city.isNotEmpty() -> currentPlayerState.city
                else -> "Brak informacji"
            }

            val buttonResource = when {
                currentPlayerState.isBuffering -> R.drawable.loading_button
                currentPlayerState.isPlaying -> R.drawable.pause_button
                else -> R.drawable.play_button
            }

            playButton.setImageResource(buttonResource)
            playButton.isEnabled = true

            Log.d("MainActivity", "UI updated - Playing: ${currentPlayerState.isPlaying}, Buffering: ${currentPlayerState.isBuffering}, Button: $buttonResource")

        } catch (e: Exception) {
            Log.e("updatePlayerUI", "Error updating UI: ${e.message}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun loadDataFromAPI() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val stationsDeferred = launch { loadStationsFromFile() }
                val okolicaDeferred = launch { loadOkolicaFromFile() }
                val swiatoweDeferred = launch { loadSwiatoweFromFile() }
                val top10popDeferred = launch { loadTop10popFromFile() }

                stationsDeferred.join()
                okolicaDeferred.join()
                swiatoweDeferred.join()
                top10popDeferred.join()

                withContext(Dispatchers.Main) {
                    setupUIAfterDataLoad()
                }
            } catch (e: Exception) {
                Log.e("File", "Error loading data from files: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Błąd podczas ładowania danych z pamięci", Toast.LENGTH_LONG).show()
                    radioStationsCache = emptyList()
                    radioOkolicaCache = emptyList()
                    radioSwiatoweCache = emptyList()
                    radioTop10popCache = emptyList()
                    setupUIAfterDataLoad()
                }
            }
        }
    }

    private suspend fun loadStationsFromFile() {
        try {
            val file = File(File(filesDir, DATABASE_DIR), STATIONS_FILE)
            if (file.exists()) {
                val json = file.readText()
                if (json.isNotEmpty()) {
                    radioStationsCache = Gson().fromJson(json, object : TypeToken<List<RadioStation>>() {}.type)
                    Log.d("File", "Loaded ${radioStationsCache?.size} stations from file")
                } else {
                    radioStationsCache = emptyList()
                }
            } else {
                radioStationsCache = emptyList()
            }
        } catch (e: Exception) {
            Log.e("File", "Error loading stations from file: ${e.message}")
            radioStationsCache = emptyList()
        }
    }

    private suspend fun loadTop10popFromFile() {
        try {
            val file = File(File(filesDir, DATABASE_DIR), TOP10POP_FILE)
            if (file.exists()) {
                val json = file.readText()
                if (json.isNotEmpty()) {
                    radioTop10popCache = Gson().fromJson(json, object : TypeToken<List<Top10popStation>>() {}.type)
                    Log.d("File", "Loaded ${radioTop10popCache?.size} top10pop stations from file")
                } else {
                    radioTop10popCache = emptyList()
                }
            } else {
                radioTop10popCache = emptyList()
            }
        } catch (e: Exception) {
            Log.e("File", "Error loading top10pop from file: ${e.message}")
            radioTop10popCache = emptyList()
        }
    }

    private suspend fun loadOkolicaFromFile() {
        try {
            val file = File(File(filesDir, DATABASE_DIR), OKOLICE_FILE)
            if (file.exists()) {
                val json = file.readText()
                if (json.isNotEmpty()) {
                    radioOkolicaCache = Gson().fromJson(json, object : TypeToken<List<Wojewodztwo>>() {}.type)
                    Log.d("File", "Loaded ${radioOkolicaCache?.size} regions from file")
                } else {
                    radioOkolicaCache = emptyList()
                }
            } else {
                radioOkolicaCache = emptyList()
            }
        } catch (e: Exception) {
            Log.e("File", "Error loading okolice from file: ${e.message}")
            radioOkolicaCache = emptyList()
        }
    }

    private suspend fun loadSwiatoweFromFile() {
        try {
            val file = File(File(filesDir, DATABASE_DIR), SWIAT_FILE)
            if (file.exists()) {
                val json = file.readText()
                if (json.isNotEmpty()) {
                    radioSwiatoweCache = Gson().fromJson(json, object : TypeToken<List<Swiatowe>>() {}.type)
                    Log.d("File", "Loaded ${radioSwiatoweCache?.size} countries from file")
                } else {
                    radioSwiatoweCache = emptyList()
                }
            } else {
                radioSwiatoweCache = emptyList()
            }
        } catch (e: Exception) {
            Log.e("File", "Error loading swiatowe from file: ${e.message}")
            radioSwiatoweCache = emptyList()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupUIAfterDataLoad() {
        val container: LinearLayout = findViewById(R.id.container)
        setupNavigation(
            container,
            radioStationsCache ?: emptyList(),
            radioOkolicaCache ?: emptyList(),
            radioSwiatoweCache ?: emptyList(),
            radioTop10popCache ?: emptyList()
        )
        displayRadioStations(radioStationsCache ?: emptyList())
        displayTop10popStations(radioTop10popCache ?: emptyList())
    }

    private fun initializeConsent() {
        val params = ConsentRequestParameters.Builder().build()
        consentInformation = UserMessagingPlatform.getConsentInformation(this)
        consentInformation.requestConsentInfoUpdate(this, params, {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { formError ->
                formError?.let { Log.e("Consent", "Form error: ${it.message}") }
                if (consentInformation.canRequestAds()) initializeAds()
            }
        }, { requestError ->
            Log.e("Consent", "Request error: ${requestError.message}")
        })
    }

    private fun loadLayout(layoutResId: Int, container: LinearLayout) {
        container.removeAllViews()
        LayoutInflater.from(this).inflate(layoutResId, container, true)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupNavigation(
        container: LinearLayout,
        radioStations: List<RadioStation>,
        radioOkolicaStations: List<Wojewodztwo>,
        radioSwiatowe: List<Swiatowe>,
        radioTop10pop: List<Top10popStation>
    ) {
        findViewById<LinearLayout>(R.id.krajowe).setOnClickListener {
            clearAllBackgrounds()
            it.setBackgroundResource(R.drawable.corner_box_4)
            switchLayout(container, R.layout.radio_krajowe) {
                displayRadioStations(radioStations)
                displayTop10popStations(radioTop10pop)
            }
        }

        findViewById<LinearLayout>(R.id.w_okolicy).setOnClickListener {
            clearAllBackgrounds()
            it.setBackgroundResource(R.drawable.corner_box_4)
            switchLayout(container, R.layout.radio_okolice) {
                displayRadioOkolicaStations(radioOkolicaStations)
            }
        }

        findViewById<LinearLayout>(R.id.swiatowe).setOnClickListener {
            clearAllBackgrounds()
            it.setBackgroundResource(R.drawable.corner_box_4)
            switchLayout(container, R.layout.radio_odkrywaj) {
                displayRadioSwiatoweStations(radioSwiatowe)
            }
        }

        findViewById<View>(R.id.game_mode).setOnClickListener {
            clearAllBackgrounds()
            switchLayout(container, R.layout.game_container)

            container.findViewById<LinearLayout>(R.id.snake)?.setOnClickListener {
                val intent = Intent(this, SnakeGame::class.java)
                startActivity(intent)
                finish()
                overridePendingTransition(0, 0)
            }
        }

        findViewById<LinearLayout>(R.id.biblioteka).setOnClickListener {
            clearAllBackgrounds()
            it.setBackgroundResource(R.drawable.corner_box_4)
            switchLayout(container, R.layout.library_container) {
                displayRadioLibrarySwiatowe(
                    radioSwiatowe.flatMap { it.stations },
                    radioOkolicaStations.flatMap { it.stations }
                )
            }
        }

        findViewById<View>(R.id.settings).setOnClickListener {
            clearAllBackgrounds()
            switchLayout(container, R.layout.settings) {
                setupSettingsInteractions()
            }
            updateThemeCheckboxes()
        }
    }

    private fun updateThemeCheckboxes() {
        val checkBoxLight = findViewById<View>(R.id.check_box_light_theme)
        val checkBoxDark = findViewById<View>(R.id.check_box_dark_theme)

        if (nightMode) {
            checkBoxDark?.setBackgroundResource(R.drawable.dot_circle)
            checkBoxLight?.setBackgroundResource(R.drawable.circle)
        } else {
            checkBoxLight?.setBackgroundResource(R.drawable.dot_circle)
            checkBoxDark?.setBackgroundResource(R.drawable.circle)
        }
    }

    private fun clearAllBackgrounds() {
        findViewById<LinearLayout>(R.id.krajowe)?.background = null
        findViewById<LinearLayout>(R.id.biblioteka)?.background = null
        findViewById<LinearLayout>(R.id.w_okolicy)?.background = null
        findViewById<LinearLayout>(R.id.swiatowe)?.background = null
    }

    private fun switchLayout(container: LinearLayout, layoutResId: Int, setup: (() -> Unit)? = null) {
        container.removeAllViews()
        LayoutInflater.from(this).inflate(layoutResId, container, true)
        setup?.invoke()
    }

    private fun setupSettingsInteractions() {
        findViewById<LinearLayout>(R.id.discord)?.setOnClickListener {
            openWebsite("https://discord.gg/MtPs7WXyJu")
        }

        findViewById<LinearLayout>(R.id.github)?.setOnClickListener {
            openWebsite("https://github.com/gofluxpl/Radio24")
        }

        findViewById<LinearLayout>(R.id.theme_light)?.setOnClickListener {
            changeTheme(false)
        }

        findViewById<LinearLayout>(R.id.theme_dark)?.setOnClickListener {
            changeTheme(true)
        }

        findViewById<LinearLayout>(R.id.tworcy)?.setOnClickListener {
            val container: LinearLayout = findViewById(R.id.container)
            switchLayout(container, R.layout.creators)
        }
    }

    private fun changeTheme(isDark: Boolean) {
        val editor = sharedPreferences.edit()
        editor.putBoolean("nightMode", isDark)
        editor.apply()

        nightMode = isDark

        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        updateThemeCheckboxes()

        savePlayerState()
    }

    private fun openWebsite(url: String) {
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).also { startActivity(it) }
    }

    private fun initializeAds() {
        if (consentInformation.isConsentFormAvailable) {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { formError ->
                formError?.let { Log.e("Ads", "Form error: ${it.message}") }
                if (consentInformation.canRequestAds()) {
                    loadAds()
                }
            }
        } else {
            loadAds()
        }
    }

    private fun loadAds() {
        CoroutineScope(Dispatchers.IO).launch {
            MobileAds.initialize(this@MainActivity) {}
            runOnUiThread {
                findViewById<AdView>(R.id.adView)?.loadAd(AdRequest.Builder().build())
            }
        }
    }

    private fun <T> onRadioStationSelected(station: T, viewPlayer: ImageView) where T : Any {
        val (name, city, url, icon) = when (station) {
            is RadioStation -> listOf(station.name, station.city, station.url, station.icon)
            is RadioStationOkolica -> listOf(station.name, station.city, station.url, station.icon)
            is RadioSwiatowe -> listOf(station.name, station.city, station.url, station.icon)
            is Top10popStation -> listOf(station.name, station.city, station.url, station.icon)
            else -> return
        }

        Log.d("MainActivity", "Station selected: $name")

        currentPlayerState = PlayerState(
            isPlaying = false,
            isBuffering = true,
            url = url,
            city = city,
            stationName = name,
            icon = icon
        )

        savePlayerState()
        updatePlayerUI()

        val iconResId = resources.getIdentifier(
            icon.replace("@drawable/", ""), "drawable", packageName
        )

        val intent = Intent(this, RadioService::class.java).apply {
            action = RadioService.ACTION_PLAY
            putExtra(RadioService.EXTRA_STATION_URL, url)
            putExtra(RadioService.EXTRA_STATION_NAME, name)
            putExtra(RadioService.EXTRA_ICON_RES, iconResId)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        Log.d("MainActivity", "Service started for station: $name")
    }

    private fun <T> createRadioView(
        station: T,
        layoutId: Int,
        container: LinearLayout,
        favorites: MutableSet<String>
    ): View where T : Any {
        val (name, city, url, icon) = when (station) {
            is RadioStation -> listOf(station.name, station.city, station.url, station.icon)
            is RadioStationOkolica -> listOf(station.name, station.city, station.url, station.icon)
            is RadioSwiatowe -> listOf(station.name, station.city, station.url, station.icon)
            is Top10popStation -> listOf(station.name, station.city, station.url, station.icon) // DODAJ TĘ LINIĘ
            else -> return View(this)
        }

        val radioView = LayoutInflater.from(this).inflate(layoutId, null)
        val iconView: ImageView = radioView.findViewById(R.id.radio_icon)
        val nameView: TextView = radioView.findViewById(R.id.radio_name)
        val cityView: TextView = radioView.findViewById(R.id.radio_city)
        val starView: ImageView = radioView.findViewById(R.id.radio_favorite)

        val iconResId = resources.getIdentifier(icon.replace("@drawable/", ""), "drawable", packageName)
        if (iconResId != 0) {
            iconView.setImageResource(iconResId)
        }

        nameView.text = name
        cityView.text = city

        starView.setBackgroundResource(
            if (favorites.contains(name)) R.drawable.heart_active
            else R.drawable.heart
        )

        val viewPlayer = findViewById<ImageView>(R.id.radio_player)
        radioView.setOnClickListener {
            onRadioStationSelected(station, viewPlayer)
        }

        starView.setOnClickListener {
            toggleFavorite(this, name, starView)
        }

        return radioView
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun displayRadioStations(radioStations: List<RadioStation>) {
        val radioContainer = findViewById<LinearLayout>(R.id.radio_container_krajowe) ?: return
        val favorites = getFavorites(this)

        radioStations.forEach { station ->
            val radioView = createRadioView(station, R.layout.radio_item, radioContainer, favorites)
            radioContainer.addView(radioView)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun displayTop10popStations(radioStations: List<Top10popStation>) {
        val radioContainer = findViewById<LinearLayout>(R.id.radio_container_krajowe_pop) ?: return
        val favorites = getFavorites(this)

        radioStations.forEach { station ->
            val radioView = createRadioView(station, R.layout.radio_item, radioContainer, favorites)
            radioContainer.addView(radioView)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun displayRadioStationsOkolica(radioStations: List<RadioStationOkolica>) {
        val radioContainer = findViewById<LinearLayout>(R.id.radio_okolice_container) ?: return
        val favorites = getFavorites(this)

        radioStations.forEach { station ->
            val radioView = createRadioView(station, R.layout.okolice_item, radioContainer, favorites)
            radioContainer.addView(radioView)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun displayRadioOkolicaStations(radioStations: List<Wojewodztwo>) {
        val radioContainer = findViewById<LinearLayout>(R.id.radio_container_okolice) ?: return

        radioStations.forEach { wojewodztwo ->
            val wojView = LayoutInflater.from(this).inflate(R.layout.wojewodztwo_item, null)
            val nameView: TextView = wojView.findViewById(R.id.radio_name)
            val countView: TextView = wojView.findViewById(R.id.radio_count_okolice)
            val iconView: ImageView = wojView.findViewById(R.id.woj_icon)

            val iconResId = resources.getIdentifier(wojewodztwo.icon.replace("@drawable/", ""), "drawable", packageName)
            if (iconResId != 0) {
                iconView.setImageResource(iconResId)
            }

            nameView.text = wojewodztwo.woj.replaceFirstChar { it.uppercase() }
            countView.text = "Liczba stacji: ${wojewodztwo.stations.size}"

            wojView.setOnClickListener {
                val container: LinearLayout = findViewById(R.id.container)
                switchLayout(container, R.layout.radio_okolica_container) {
                    displayRadioStationsOkolica(wojewodztwo.stations)
                }
            }

            radioContainer.addView(wojView)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun displayRadioLibrarySwiatowe(radioSwiatowe: List<RadioSwiatowe>, radioStations: List<RadioStationOkolica>) {
        val radioContainer = findViewById<LinearLayout>(R.id.radio_library_container) ?: return
        val favorites = getFavorites(this)
        val addedStations = mutableSetOf<String>()

        radioStations.forEach { station ->
            if (favorites.contains(station.name) && addedStations.add(station.name)) {
                val radioView = createRadioView(station, R.layout.library_item, radioContainer, favorites)
                radioContainer.addView(radioView)
            }
        }

        radioSwiatowe.forEach { station ->
            if (favorites.contains(station.name) && addedStations.add(station.name)) {
                val radioView = createRadioView(station, R.layout.library_item, radioContainer, favorites)
                radioContainer.addView(radioView)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun displayRadioSwiatoweStations(radioSwiatowe: List<Swiatowe>) {
        val radioContainer = findViewById<LinearLayout>(R.id.radio_swiat) ?: return

        radioSwiatowe.forEach { swiatowe ->
            val worldView = LayoutInflater.from(this).inflate(R.layout.world_item, null)
            val iconView: ImageView = worldView.findViewById(R.id.world_icon)
            val nameView: TextView = worldView.findViewById(R.id.radio_name)
            val countView: TextView = worldView.findViewById(R.id.radio_count_okolice)

            val iconResId = resources.getIdentifier(swiatowe.icon.replace("@drawable/", ""), "drawable", packageName)
            if (iconResId != 0) {
                iconView.setImageResource(iconResId)
            }

            nameView.text = swiatowe.country.replaceFirstChar { it.uppercase() }
            countView.text = "Liczba stacji: ${swiatowe.stations.size}"

            worldView.setOnClickListener {
                val container: LinearLayout = findViewById(R.id.container)
                switchLayout(container, R.layout.world_container) {
                    displayRadioStationsSwiatowe(swiatowe.stations)
                }
            }

            radioContainer.addView(worldView)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun displayRadioStationsSwiatowe(radioSwiatowe: List<RadioSwiatowe>) {
        val radioContainer = findViewById<LinearLayout>(R.id.radio_swiat_container) ?: return
        val favorites = getFavorites(this)

        radioSwiatowe.forEach { station ->
            val radioView = createRadioView(station, R.layout.okolice_item, radioContainer, favorites)
            radioContainer.addView(radioView)
        }
    }

    private fun getFavorites(context: Context): MutableSet<String> {
        val file = getFavoritesFile()
        if (!file.exists()) return mutableSetOf()

        return try {
            val type = object : TypeToken<MutableSet<String>>() {}.type
            val json = file.readText()
            if (json.isNotEmpty()) {
                Gson().fromJson(json, type) ?: mutableSetOf()
            } else {
                mutableSetOf()
            }
        } catch (e: Exception) {
            Log.e("getFavorites", "Error reading favorites: ${e.message}")
            mutableSetOf()
        }
    }

    private fun saveFavorites(context: Context, favorites: MutableSet<String>) {
        try {
            getFavoritesFile().writeText(Gson().toJson(favorites))
        } catch (e: Exception) {
            Log.e("saveFavorites", "Error saving favorites: ${e.message}")
        }
    }

    private fun toggleFavorite(context: Context, stationName: String, starView: ImageView) {
        val favorites = getFavorites(context)
        val wasFavorite = favorites.contains(stationName)

        if (wasFavorite) {
            favorites.remove(stationName)
            starView.setBackgroundResource(R.drawable.heart)
        } else {
            favorites.add(stationName)
            starView.setBackgroundResource(R.drawable.heart_active)
        }

        saveFavorites(context, favorites)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "radio_channel",
                "Radio Player",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun togglePlayPause(viewPlayer: ImageView) {
        if (currentPlayerState.url.isEmpty()) {
            Toast.makeText(this, "Brak danych o stacji radiowej!", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("MainActivity", "Toggle play/pause - Current state: Playing=${currentPlayerState.isPlaying}, Buffering=${currentPlayerState.isBuffering}")

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
                Log.d("MainActivity", "Sending PAUSE action")
            }
            currentPlayerState.isBuffering -> {
                intent.action = RadioService.ACTION_STOP
                Log.d("MainActivity", "Sending STOP action (interrupting buffering)")
            }
            else -> {
                intent.action = RadioService.ACTION_PLAY
                currentPlayerState = currentPlayerState.copy(isBuffering = true, isPlaying = false)
                updatePlayerUI()
                Log.d("MainActivity", "Sending PLAY action")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        Log.d("MainActivity", "onDestroy called")

        exoPlayer?.release()
        exoPlayer = null

        try {
            localBroadcastManager.unregisterReceiver(playbackStateReceiver)
            trackChangeReceiver?.let { localBroadcastManager.unregisterReceiver(it) }
            Log.d("MainActivity", "LOCAL broadcast receivers unregistered")
        } catch (e: Exception) {
            Log.e("onDestroy", "Error unregistering LOCAL receiver: ${e.message}")
        }

        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
            Log.d("MainActivity", "Service unbound")
        }

        currentPlayerState = currentPlayerState.copy(isPlaying = false, isBuffering = false)
        savePlayerState()
    }
}