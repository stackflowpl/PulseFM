package net.gf.radio24

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import java.io.FileReader
import java.io.FileWriter

class MainActivity : AppCompatActivity() {

    private lateinit var consentInformation: ConsentInformation

    private var nightMode: Boolean = false
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    private lateinit var radioStopReceiver: BroadcastReceiver

    private var exoPlayer: ExoPlayer? = null

    data class Swiatowe(val country: String, val icon: String, val stations: List<RadioSwiatowe>)
    data class RadioSwiatowe(val name: String, val city: String, val url: String, val icon: String)

    data class Wojewodztwo(val woj: String, val icon: String, val stations: List<RadioStationOkolica>)
    data class RadioStationOkolica(val name: String, val city: String, val url: String, val icon: String)
    data class RadioStation(val name: String, val city: String, val url: String, val icon: String)

    private var radioStationsCache: List<RadioStation>? = null
    private var radioOkolicaCache: List<Wojewodztwo>? = null
    private var radioSwiatoweCache: List<Swiatowe>? = null

    var isPlaying: Boolean? = false
    var url = ""
    var city = ""
    var stationName = ""
    var icon = ""

    private val DATABASE_DIR = "database"
    private val PLAYER_FILE = "player.json"
    private val FAVORITES_FILE = "favorites.json"
    private val STATIONS_FILE = "stations.json"
    private val OKOLICE_FILE = "okolice.json"
    private val SWIAT_FILE = "swiat.json"

    private fun getPlayerFile(): File {
        return File(File(filesDir, DATABASE_DIR), PLAYER_FILE)
    }

    private fun getFavoritesFile(): File {
        return File(File(filesDir, DATABASE_DIR), FAVORITES_FILE)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        exoPlayer = ExoPlayer.Builder(application).build()

        sharedPreferences = getSharedPreferences("MODE", MODE_PRIVATE)
        nightMode = sharedPreferences.getBoolean("nightMode", false)

        initializeDatabase()
        loadPlayerState()
        initializeConsent()
        initializeAds()
        FirebaseFirestore.setLoggingEnabled(true)

        createNotificationChannel()

        radioStopReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "RADIO_STOPPED") {
                    val file = getPlayerFile()

                    if (file.exists()) {
                        try {
                            val jsonString = file.readText()
                            if (jsonString.isNotEmpty()) {
                                val jsonObject = JSONObject(jsonString)
                                isPlaying = jsonObject.optBoolean("isPlaying", false)
                                jsonObject.put("isPlaying", false)
                                file.writeText(jsonObject.toString())
                            }
                        } catch (e: Exception) {
                            Log.e("radioStopReceiver", "Error updating JSON: ${e.message}")
                            Toast.makeText(context, "Wystąpił problem z plikiem danych!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Brak pliku player.json!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            RECEIVER_NOT_EXPORTED
        } else {
            0
        }
        registerReceiver(radioStopReceiver, IntentFilter("RADIO_STOPPED"), flag)

        val container: LinearLayout = findViewById(R.id.container)
        loadLayout(R.layout.radio_krajowe, container)

        loadDataFromAPI()

        findViewById<View>(R.id.car_mode).setOnClickListener {
            val file = getPlayerFile()

            if (!file.exists() || file.readText().isBlank()) {
                Toast.makeText(this, "Brak zapisanej stacji radiowej", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            try {
                val jsonString = file.readText()
                val jsonObject = JSONObject(jsonString)

                val isPlaying = jsonObject.optBoolean("isPlaying", false)
                val url = jsonObject.optString("url", "")
                val city = jsonObject.optString("city", "")
                val stationName = jsonObject.optString("stationName", "")
                val icon = jsonObject.optString("icon", "")

                if (!isPlaying || url.isBlank() || stationName.isBlank()) {
                    Toast.makeText(this, "Brak poprawnie zapisanej stacji", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val intent = Intent(this, CarActivity::class.java)
                startActivity(intent)
                finish()
                overridePendingTransition(0, 0)

                val jsonObjectToSave = JSONObject().apply {
                    put("change_theme", true)
                    put("isPlaying", true)
                    put("url", url)
                    put("city", city)
                    put("stationName", stationName)
                    put("icon", icon)
                }
                file.writeText(jsonObjectToSave.toString())

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Błąd odczytu danych stacji", Toast.LENGTH_SHORT).show()
            }
        }


        findViewById<ImageView>(R.id.radio_player).setOnClickListener {
            togglePlayPause(it as ImageView)
        }
    }

    private fun initializeDatabase() {
        try {
            val databaseDir = File(filesDir, "database")
            if (!databaseDir.exists()) {
                databaseDir.mkdirs()
                Log.d("Database", "Created database directory")
            }

            val playerFile = File(databaseDir, "player.json")
            if (!playerFile.exists()) {
                val defaultPlayerData = JSONObject().apply {
                    put("change_theme", false)
                    put("isPlaying", false)
                    put("url", "")
                    put("city", "")
                    put("stationName", "")
                    put("icon", "")
                }

                playerFile.writeText(defaultPlayerData.toString())
                Log.d("Database", "Created player.json with default data")
            }

            val favoritesFile = File(databaseDir, "favorites.json")
            if (!favoritesFile.exists()) {
                val defaultFavorites = mutableSetOf<String>()
                favoritesFile.writeText(Gson().toJson(defaultFavorites))
                Log.d("Database", "Created favorites.json with empty array")
            }

        } catch (e: Exception) {
            Log.e("Database", "Error initializing database: ${e.message}")
            Toast.makeText(this, "Błąd podczas inicjalizacji bazy danych", Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun loadDataFromAPI() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val stationsDeferred = launch { loadStationsFromFile() }
                val okolicaDeferred = launch { loadOkolicaFromFile() }
                val swiatoweDeferred = launch { loadSwiatoweFromFile() }

                stationsDeferred.join()
                okolicaDeferred.join()
                swiatoweDeferred.join()

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
                    Log.w("File", "Stations file is empty")
                    radioStationsCache = emptyList()
                }
            } else {
                Log.w("File", "Stations file does not exist")
                radioStationsCache = emptyList()
            }
        } catch (e: Exception) {
            Log.e("File", "Error loading stations from file: ${e.message}")
            radioStationsCache = emptyList()
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
                    Log.w("File", "Okolice file is empty")
                    radioOkolicaCache = emptyList()
                }
            } else {
                Log.w("File", "Okolice file does not exist")
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
                    Log.w("File", "Swiatowe file is empty")
                    radioSwiatoweCache = emptyList()
                }
            } else {
                Log.w("File", "Swiatowe file does not exist")
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

        setupNavigation(container, radioStationsCache ?: emptyList(), radioOkolicaCache ?: emptyList(), radioSwiatoweCache ?: emptyList())

        displayRadioStations(radioStationsCache ?: emptyList())
    }

    private fun loadPlayerState() {
        val file = getPlayerFile()
        if (!file.exists()) return

        try {
            val jsonString = file.readText().takeIf { it.isNotEmpty() } ?: return
            val jsonObject = JSONObject(jsonString)

            isPlaying = jsonObject.optBoolean("isPlaying", false)
            url = jsonObject.optString("url", "")
            city = jsonObject.optString("city", "")
            stationName = jsonObject.optString("stationName", "")
            icon = jsonObject.optString("icon", "")

            val iconViewPlayer: ImageView = findViewById(R.id.radio_icon_player)
            val nameViewPlayer: TextView = findViewById(R.id.radio_name_player)
            val cityViewPlayer: TextView = findViewById(R.id.radio_container_city)

            if (icon.isNotEmpty()) {
                iconViewPlayer.setImageResource(
                    resources.getIdentifier(icon.replace("@drawable/", ""), "drawable", packageName)
                )
            }

            nameViewPlayer.text = if (!stationName.isNullOrBlank()) stationName else "Nie wybrano radia"
            cityViewPlayer.text = if (!city.isNullOrBlank()) city else "Brak informacji"

            val viewPlayer: ImageView = findViewById(R.id.radio_player)
            if (isPlaying == true) {
                viewPlayer.setImageResource(R.drawable.pause_button)
            } else {
                viewPlayer.setImageResource(R.drawable.play_button)
            }
        } catch (e: Exception) {
            Log.e("loadPlayerState", "Error loading player state: ${e.message}")
        }
    }

    private fun initializeConsent() {
        val params = ConsentRequestParameters.Builder().build()
        consentInformation = UserMessagingPlatform.getConsentInformation(this)
        consentInformation.requestConsentInfoUpdate(this, params, {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { formError ->
                formError?.let { println("Błąd formularza zgody: ${it.message}") }
                if (consentInformation.canRequestAds()) initializeAds()
            }
        }, { requestError ->
            println("Błąd uzyskiwania zgody: ${requestError.message}")
        })
    }

    override fun onStart() {
        super.onStart()

        if (nightMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }

    private fun loadLayout(layoutResId: Int, container: LinearLayout) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)
        inflater.inflate(layoutResId, container, true)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupNavigation(
        container: LinearLayout,
        radioStations: List<RadioStation>,
        radioOkolicaStations: List<Wojewodztwo>,
        radioSwiatowe: List<Swiatowe>
    ) {
        findViewById<LinearLayout>(R.id.krajowe).setOnClickListener {
            clearAllBackgrounds()
            it.setBackgroundResource(R.drawable.corner_box_4)
            switchLayout(container, R.layout.radio_krajowe) {
                displayRadioStations(radioStations)
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

            val snakeLayout = container.findViewById<LinearLayout>(R.id.snake)
            snakeLayout.setOnClickListener {
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

            switchLayout(container, R.layout.settings) { setupSettingsInteractions() }

            val checkBoxLight = findViewById<View>(R.id.check_box_light_theme)
            val checkBoxDark = findViewById<View>(R.id.check_box_dark_theme)

            sharedPreferences = getSharedPreferences("MODE", MODE_PRIVATE)
            nightMode = sharedPreferences.getBoolean("nightMode", false)

            if (nightMode) {
                checkBoxDark.setBackgroundResource(R.drawable.dot_circle)
                checkBoxLight.setBackgroundResource(R.drawable.circle)
            } else {
                checkBoxLight.setBackgroundResource(R.drawable.dot_circle)
                checkBoxDark.setBackgroundResource(R.drawable.circle)
            }
        }
    }

    private fun clearAllBackgrounds() {
        findViewById<LinearLayout>(R.id.krajowe).background = null
        findViewById<LinearLayout>(R.id.biblioteka).background = null
        findViewById<LinearLayout>(R.id.w_okolicy).background = null
        findViewById<LinearLayout>(R.id.swiatowe).background = null
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
            val checkBoxLight = findViewById<View>(R.id.check_box_light_theme)
            val checkBoxDark = findViewById<View>(R.id.check_box_dark_theme)

            editor = sharedPreferences.edit()
            editor.putBoolean("nightMode", false)
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            editor.apply()

            val file = getPlayerFile()

            if (file.exists()) {
                val fileReader = FileReader(file)
                val jsonString = fileReader.readText()

                if (jsonString.isNotEmpty()) {
                    val jsonObject = JSONObject(jsonString)

                    isPlaying = jsonObject.getBoolean("isPlaying")
                    url = jsonObject.getString("url")
                    city = jsonObject.getString("city")
                    stationName = jsonObject.getString("stationName")
                    icon = jsonObject.getString("icon")

                    val jsonObjectValue = JSONObject().apply {
                        put("change_theme", true)
                        put("isPlaying", false)
                        put("url",  url)
                        put("city", city)
                        put("stationName", stationName)
                        put("icon", icon)
                    }

                    val file = File(filesDir, "player.json")
                    val fileWriter = FileWriter(file)

                    fileWriter.use {
                        it.write(jsonObjectValue.toString())
                    }
                }
            }

            checkBoxLight.setBackgroundResource(R.drawable.dot_circle)
            checkBoxDark.setBackgroundResource(R.drawable.circle)
        }

        findViewById<LinearLayout>(R.id.theme_dark)?.setOnClickListener {
            val checkBoxLight = findViewById<View>(R.id.check_box_light_theme)
            val checkBoxDark = findViewById<View>(R.id.check_box_dark_theme)

            editor = sharedPreferences.edit()
            editor.putBoolean("nightMode", true)
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            editor.apply()

            val file = getPlayerFile()
            if (file.exists()) {
                val fileReader = FileReader(file)
                val jsonString = fileReader.readText()

                if (jsonString.isNotEmpty()) {
                    val jsonObject = JSONObject(jsonString)

                    isPlaying = jsonObject.getBoolean("isPlaying")
                    url = jsonObject.getString("url")
                    city = jsonObject.getString("city")
                    stationName = jsonObject.getString("stationName")
                    icon = jsonObject.getString("icon")

                    val jsonObjectValue = JSONObject().apply {
                        put("change_theme", true)
                        put("isPlaying", false)
                        put("url",  url)
                        put("city", city)
                        put("stationName", stationName)
                        put("icon", icon)
                    }

                    val file = File(filesDir, "player.json")
                    val fileWriter = FileWriter(file)

                    fileWriter.use {
                        it.write(jsonObjectValue.toString())
                    }
                }
            }

            checkBoxDark.setBackgroundResource(R.drawable.dot_circle)
            checkBoxLight.setBackgroundResource(R.drawable.circle)
        }

        findViewById<LinearLayout>(R.id.tworcy)?.setOnClickListener {
            val container: LinearLayout = findViewById(R.id.container)

            switchLayout(container, R.layout.creators)
        }
    }

    private fun openWebsite(url: String) {
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).also { startActivity(it) }
    }

    private fun initializeAds() {
        if (consentInformation.isConsentFormAvailable) {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { formError ->
                if (formError != null) {
                    println("Błąd formularza zgody: ${formError.message}")
                }
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

    @RequiresApi(Build.VERSION_CODES.O)
    private fun displayRadioStations(radioStations: List<RadioStation>) {
        val radioContainer = findViewById<LinearLayout>(R.id.radio_container_krajowe) ?: return
        val favorites = getFavorites(this)

        if (radioStations.isNotEmpty()) {
            val viewPlayer = findViewById<ImageView>(R.id.radio_player)

            radioStations.forEach { station ->
                val radioView = LayoutInflater.from(this).inflate(R.layout.radio_item, null)
                val iconView: ImageView = radioView.findViewById(R.id.radio_icon)
                val nameView: TextView = radioView.findViewById(R.id.radio_name)
                val cityView: TextView = radioView.findViewById(R.id.radio_city)
                val starView: ImageView = radioView.findViewById(R.id.radio_favorite)

                iconView.setImageResource(resources.getIdentifier(station.icon.replace("@drawable/", ""), "drawable", packageName))
                nameView.text = station.name
                cityView.text = station.city

                radioContainer.addView(radioView)

                val wasFavorite = favorites.contains(station.name)

                if (wasFavorite) {
                    starView.setBackgroundResource(R.drawable.heart_active)
                } else {
                    starView.setBackgroundResource(R.drawable.heart)
                }

                radioView.setOnClickListener {
                    onRadioStationSelected(station, viewPlayer)
                }

                starView.setOnClickListener {
                    toggleFavorite(this, station.name, starView)
                }
            }

            viewPlayer.setOnClickListener {
                togglePlayPause(viewPlayer)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun displayRadioStationsOkolica(radioStations: List<RadioStationOkolica>) {
        val radioContainer = findViewById<LinearLayout>(R.id.radio_okolice_container) ?: return
        val favorites = getFavorites(this)

        if (radioStations.isNotEmpty()) {
            val viewPlayer = findViewById<ImageView>(R.id.radio_player)

            radioStations.forEach { station ->
                val radioView = LayoutInflater.from(this).inflate(R.layout.okolice_item, null)
                val iconView: ImageView = radioView.findViewById(R.id.radio_icon)
                val nameView: TextView = radioView.findViewById(R.id.radio_name)
                val cityView: TextView = radioView.findViewById(R.id.radio_city)
                val starView: ImageView = radioView.findViewById(R.id.radio_favorite)

                iconView.setImageResource(resources.getIdentifier(station.icon.replace("@drawable/", ""), "drawable", packageName))
                nameView.text = station.name
                cityView.text = station.city

                radioContainer.addView(radioView)

                val wasFavorite = favorites.contains(station.name)

                if (wasFavorite) {
                    starView.setBackgroundResource(R.drawable.heart_active)
                } else {
                    starView.setBackgroundResource(R.drawable.heart)
                }

                radioView.setOnClickListener {
                    onRadioStationSelected(station, viewPlayer)
                }

                starView.setOnClickListener {
                    toggleFavorite(this, station.name, starView)
                }
            }

            viewPlayer.setOnClickListener {
                togglePlayPause(viewPlayer)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun displayRadioOkolicaStations(radioStations: List<Wojewodztwo>) {
        val radioContainer = findViewById<LinearLayout>(R.id.radio_container_okolice) ?: return

        val viewPlayer = findViewById<ImageView>(R.id.radio_player)

        radioStations.forEach { wojewodztwo ->
            val wojView = LayoutInflater.from(this).inflate(R.layout.wojewodztwo_item, null)
            val nameView: TextView = wojView.findViewById(R.id.radio_name)
            val countView: TextView = wojView.findViewById(R.id.radio_count_okolice)
            val iconView: ImageView = wojView.findViewById(R.id.woj_icon)

            val container: LinearLayout = findViewById(R.id.container)

            val liczbaStacji = wojewodztwo.stations.size

            iconView.setImageResource(resources.getIdentifier(wojewodztwo.icon.replace("@drawable/", ""), "drawable", packageName))
            nameView.text = wojewodztwo.woj.capitalize()
            countView.text = "Liczba stacji: " + liczbaStacji

            wojView.setOnClickListener {
                switchLayout(container, R.layout.radio_okolica_container) {
                    displayRadioStationsOkolica(wojewodztwo.stations)
                }
            }

            radioContainer.addView(wojView)
        }

        viewPlayer.setOnClickListener {
            togglePlayPause(viewPlayer)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun displayRadioLibrarySwiatowe(radioSwiatowe: List<RadioSwiatowe>, radioStations: List<RadioStationOkolica>) {
        val radioContainer = findViewById<LinearLayout>(R.id.radio_library_container) ?: return
        val favorites = getFavorites(this)
        val addedStations = mutableSetOf<String>()

        val viewPlayer = findViewById<ImageView>(R.id.radio_player)

        radioStations.forEach { station ->
            if (favorites.contains(station.name) && addedStations.add(station.name)) {
                addRadioView(radioContainer, station.name, station.city, station.url, station.icon, viewPlayer)
            }
        }

        radioSwiatowe.forEach { station ->
            if (favorites.contains(station.name) && addedStations.add(station.name)) {
                addRadioView(radioContainer, station.name, station.city, station.url, station.icon, viewPlayer)
            }
        }

        viewPlayer.setOnClickListener {
            togglePlayPause(viewPlayer)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun addRadioView(
        container: LinearLayout,
        name: String,
        city: String,
        url: String,
        icon: String,
        viewPlayer: ImageView
    ) {
        val radioView = LayoutInflater.from(container.context).inflate(R.layout.library_item, null)
        val iconView: ImageView = radioView.findViewById(R.id.radio_icon)
        val nameView: TextView = radioView.findViewById(R.id.radio_name)
        val cityView: TextView = radioView.findViewById(R.id.radio_city)
        val starView: ImageView = radioView.findViewById(R.id.radio_favorite)

        val context = container.context

        iconView.setImageResource(
            context.resources.getIdentifier(
                icon.replace("@drawable/", ""), "drawable", context.packageName
            )
        )
        nameView.text = name
        cityView.text = city

        container.addView(radioView)

        val favorites = getFavorites(context)
        if (favorites.contains(name)) {
            starView.setBackgroundResource(R.drawable.heart_active)
        } else {
            starView.setBackgroundResource(R.drawable.heart)
        }

        radioView.setOnClickListener {
            onRadioStationSelected(RadioSwiatowe(name, city, url, icon), viewPlayer)
        }

        starView.setOnClickListener {
            toggleFavorite(context, name, starView)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun displayRadioSwiatoweStations(radioSwiatowe: List<Swiatowe>) {
        val radioContainer = findViewById<LinearLayout>(R.id.radio_swiat) ?: return

        val viewPlayer = findViewById<ImageView>(R.id.radio_player)

        radioSwiatowe.forEach { swiatowe ->
            val worldView = LayoutInflater.from(this).inflate(R.layout.world_item, null)
            val iconView: ImageView = worldView.findViewById(R.id.world_icon)
            val nameView: TextView = worldView.findViewById(R.id.radio_name)
            val countView: TextView = worldView.findViewById(R.id.radio_count_okolice)

            val container: LinearLayout = findViewById(R.id.container)

            val liczbaStacji = swiatowe.stations.size

            iconView.setImageResource(resources.getIdentifier(swiatowe.icon.replace("@drawable/", ""), "drawable", packageName))
            nameView.text = swiatowe.country.capitalize()
            countView.text = "Liczba stacji: " + liczbaStacji

            worldView.setOnClickListener {
                switchLayout(container, R.layout.world_container) {
                    displayRadioStationsSwiatowe(swiatowe.stations)
                }
            }

            radioContainer.addView(worldView)
        }

        viewPlayer.setOnClickListener {
            togglePlayPause(viewPlayer)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun displayRadioStationsSwiatowe(radioSwiatowe: List<RadioSwiatowe>) {
        val radioContainer = findViewById<LinearLayout>(R.id.radio_swiat_container) ?: return
        val favorites = getFavorites(this)

        if (radioSwiatowe.isNotEmpty()) {
            val viewPlayer = findViewById<ImageView>(R.id.radio_player)

            radioSwiatowe.forEach { station ->
                val radioView = LayoutInflater.from(this).inflate(R.layout.okolice_item, null)
                val iconView: ImageView = radioView.findViewById(R.id.radio_icon)
                val nameView: TextView = radioView.findViewById(R.id.radio_name)
                val cityView: TextView = radioView.findViewById(R.id.radio_city)
                val starView: ImageView = radioView.findViewById(R.id.radio_favorite)

                iconView.setImageResource(resources.getIdentifier(station.icon.replace("@drawable/", ""), "drawable", packageName))
                nameView.text = station.name
                cityView.text = station.city

                radioContainer.addView(radioView)

                val wasFavorite = favorites.contains(station.name)

                if (wasFavorite) {
                    starView.setBackgroundResource(R.drawable.heart_active)
                } else {
                    starView.setBackgroundResource(R.drawable.heart)
                }

                radioView.setOnClickListener {
                    onRadioStationSelected(station, viewPlayer)
                }

                starView.setOnClickListener {
                    toggleFavorite(this, station.name, starView)
                }
            }

            viewPlayer.setOnClickListener {
                togglePlayPause(viewPlayer)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun onRadioStationSelected(station: RadioStation, viewPlayer: ImageView) {
        val iconViewPlayer: ImageView = findViewById(R.id.radio_icon_player)
        val nameViewPlayer: TextView = findViewById(R.id.radio_name_player)
        val cityViewPlayer: TextView = findViewById(R.id.radio_container_city)

        val jsonObject = JSONObject().apply {
            put("change_theme", false)
            put("isPlaying", true)
            put("url", station.url)
            put("city", station.city)
            put("stationName", station.name)
            put("icon", station.icon)
        }

        val file = getPlayerFile()
        file.writeText(jsonObject.toString())

        viewPlayer.setImageResource(R.drawable.pause_button)

        val iconResId = resources.getIdentifier(
            station.icon.replace("@drawable/", ""), "drawable", packageName
        )

        if (iconResId != 0) {
            iconViewPlayer.setImageResource(iconResId)
        }

        nameViewPlayer.text = station.name
        cityViewPlayer.text = station.city

        val intent = Intent(this, RadioService::class.java).apply {
            action = "PLAY"
            putExtra("STATION_URL", station.url)
            putExtra("STATION_NAME", station.name)
            putExtra("ICON_RES", iconResId)
        }

        startForegroundService(intent)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun onRadioStationSelected(station: RadioStationOkolica, viewPlayer: ImageView) {
        val iconViewPlayer: ImageView = findViewById(R.id.radio_icon_player)
        val nameViewPlayer: TextView = findViewById(R.id.radio_name_player)
        val cityViewPlayer: TextView = findViewById(R.id.radio_container_city)

        val jsonObject = JSONObject().apply {
            put("change_theme", false)
            put("isPlaying", true)
            put("url", station.url)
            put("city", station.city)
            put("stationName", station.name)
            put("icon", station.icon)
        }

        val file = getPlayerFile()
        file.writeText(jsonObject.toString())

        viewPlayer.setImageResource(R.drawable.pause_button)

        iconViewPlayer.setImageResource(resources.getIdentifier(station.icon.replace("@drawable/", ""), "drawable", packageName))
        nameViewPlayer.text = station.name
        cityViewPlayer.text = station.city

        val iconResId = resources.getIdentifier(
            station.icon.replace("@drawable/", ""), "drawable", packageName
        )

        if (iconResId != 0) {
            iconViewPlayer.setImageResource(iconResId)
        }

        val intent = Intent(this, RadioService::class.java).apply {
            action = "PLAY"
            putExtra("STATION_URL", station.url)
            putExtra("STATION_NAME", station.name)
            putExtra("ICON_RES", iconResId)
        }

        startForegroundService(intent)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun onRadioStationSelected(station: RadioSwiatowe, viewPlayer: ImageView) {
        val iconViewPlayer: ImageView = findViewById(R.id.radio_icon_player)
        val nameViewPlayer: TextView = findViewById(R.id.radio_name_player)
        val cityViewPlayer: TextView = findViewById(R.id.radio_container_city)

        val jsonObject = JSONObject().apply {
            put("change_theme", false)
            put("isPlaying", true)
            put("url", station.url)
            put("city", station.city)
            put("stationName", station.name)
            put("icon", station.icon)
        }

        val file = getPlayerFile()
        file.writeText(jsonObject.toString())

        viewPlayer.setImageResource(R.drawable.pause_button)

        iconViewPlayer.setImageResource(resources.getIdentifier(station.icon.replace("@drawable/", ""), "drawable", packageName))
        nameViewPlayer.text = station.name
        cityViewPlayer.text = station.city

        val iconResId = resources.getIdentifier(
            station.icon.replace("@drawable/", ""), "drawable", packageName
        )

        if (iconResId != 0) {
            iconViewPlayer.setImageResource(iconResId)
        }

        val intent = Intent(this, RadioService::class.java).apply {
            action = "PLAY"
            putExtra("STATION_URL", station.url)
            putExtra("STATION_NAME", station.name)
            putExtra("ICON_RES", iconResId)
        }

        startForegroundService(intent)
    }

    private fun getFavorites(context: Context): MutableSet<String> {
        val file = getFavoritesFile()
        if (!file.exists()) return mutableSetOf()

        return try {
            val type = object : TypeToken<MutableSet<String>>() {}.type
            Gson().fromJson(FileReader(file), type) ?: mutableSetOf()
        } catch (e: Exception) {
            Log.e("getFavorites", "Error reading favorites: ${e.message}")
            mutableSetOf()
        }
    }

    private fun saveFavorites(context: Context, favorites: MutableSet<String>) {
        val file = getFavoritesFile()
        try {
            FileWriter(file).use { writer ->
                Gson().toJson(favorites, writer)
            }
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
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun togglePlayPause(viewPlayer: ImageView) {
        val file = getPlayerFile()
        if (file.exists()) {
            try {
                val jsonString = file.readText()

                if (jsonString.isNotEmpty()) {
                    val jsonObject = JSONObject(jsonString)

                    isPlaying = jsonObject.optBoolean("isPlaying", false)
                    url = jsonObject.optString("url", "")
                    city = jsonObject.optString("city", "")
                    stationName = jsonObject.optString("stationName", "")
                    icon = jsonObject.optString("icon", "")

                    if (url.isEmpty()) {
                        Toast.makeText(this, "Brak danych o stacji radiowej!", Toast.LENGTH_SHORT).show()
                        return
                    }

                    val iconResId = resources.getIdentifier(
                        icon.replace("@drawable/", ""), "drawable", packageName
                    )

                    Log.d("togglePlayPause", "iconResId: $iconResId, isPlaying: $isPlaying, stationName: $stationName, url: $url")

                    val intent = Intent(this, RadioService::class.java).apply {
                        putExtra("STATION_URL", url)
                        putExtra("STATION_NAME", stationName)
                        putExtra("ICON_RES", iconResId)
                    }

                    if (isPlaying == true) {
                        intent.action = "STOP"
                        startService(intent)
                        viewPlayer.setImageResource(R.drawable.play_button)
                        isPlaying = false
                    } else {
                        intent.action = "PLAY"
                        startService(intent)
                        viewPlayer.setImageResource(R.drawable.pause_button)
                        isPlaying = true
                    }

                    val updatedJsonObject = JSONObject().apply {
                        put("change_theme", false)
                        put("isPlaying", isPlaying)
                        put("url", url)
                        put("city", city)
                        put("stationName", stationName)
                        put("icon", icon)
                    }

                    file.writeText(updatedJsonObject.toString())
                }
            } catch (e: Exception) {
                Log.e("togglePlayPause", "Error reading or parsing JSON: ${e.message}")
                Toast.makeText(this, "Wystąpił problem z plikiem danych!", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Brak pliku player.json!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null

        try {
            unregisterReceiver(radioStopReceiver)
        } catch (e: Exception) {
            Log.e("onDestroy", "Error unregistering receiver: ${e.message}")
        }

        val file = getPlayerFile()
        if (!file.exists()) return

        try {
            val jsonString = file.readText()
            if (jsonString.isEmpty()) return

            val jsonObject = JSONObject(jsonString)

            val changeTheme = jsonObject.optBoolean("change_theme", false)
            val url = jsonObject.optString("url", "")
            val city = jsonObject.optString("city", "")
            val stationName = jsonObject.optString("stationName", "")
            val icon = jsonObject.optString("icon", "")

            if (!changeTheme) {
                val jsonObjectToSave = JSONObject().apply {
                    put("change_theme", false)
                    put("isPlaying", false)
                    put("url", url)
                    put("city", city)
                    put("stationName", stationName)
                    put("icon", icon)
                }
                file.writeText(jsonObjectToSave.toString())
            }
        } catch (e: Exception) {
            Log.e("onDestroy", "Error handling player file: ${e.message}")
        }
    }
}
