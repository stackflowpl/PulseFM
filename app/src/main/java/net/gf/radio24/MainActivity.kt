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
import com.google.android.exoplayer2.MediaItem
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.google.gson.JsonParser
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

    private val FAVORITES_FILE = "favorites.json"

    data class Swiatowe(val country: String, val icon: String, val stations: List<RadioSwiatowe>)
    data class RadioSwiatowe(val name: String, val city: String, val url: String, val icon: String)

    data class Wojewodztwo(val woj: String, val icon: String, val stations: List<RadioStationOkolica>)
    data class RadioStationOkolica(val name: String, val city: String, val url: String, val icon: String)
    data class RadioStation(val name: String, val city: String, val url: String, val icon: String)

    var isPlaying: Boolean? = false
    var url = ""
    var city = ""
    var stationName = ""
    var icon = ""

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.setDecorFitsSystemWindows(true)

        exoPlayer = ExoPlayer.Builder(application).build()

        sharedPreferences = getSharedPreferences("MODE", Context.MODE_PRIVATE)
        nightMode = sharedPreferences.getBoolean("nightMode", false)

        loadPlayerState()
        initializeConsent()
        initializeAds()
        FirebaseFirestore.setLoggingEnabled(true)

        createNotificationChannel()

        radioStopReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "RADIO_STOPPED") {
                    val file = File(filesDir, "player.json")

                    if (file.exists()) {
                        try {
                            val jsonString = FileReader(file).readText()
                            if (jsonString.isNotEmpty()) {
                                val jsonObject = JSONObject(jsonString)

                                isPlaying = jsonObject.getBoolean("isPlaying")

                                jsonObject.put("isPlaying", isPlaying)

                                FileWriter(file).use { it.write(jsonObject.toString()) }
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
            Context.RECEIVER_NOT_EXPORTED
        } else {
            0
        }
        registerReceiver(radioStopReceiver, IntentFilter("RADIO_STOPPED"), flag)

        val container: LinearLayout = findViewById(R.id.container)
        loadLayout(R.layout.radio_krajowe, container)

        val radioSwiatowe = loadStationsFromRaw(R.raw.radio_swiat, Swiatowe::class.java)
        val radioOkolica = loadStationsFromRaw(R.raw.radio_okolice, Wojewodztwo::class.java)
        val radioStations = loadStationsFromRaw(R.raw.radio_stations, RadioStation::class.java)

        findViewById<View>(R.id.car_mode).setOnClickListener {
            val intent = Intent(this, CarActivity::class.java)
            startActivity(intent)
            finish()
            overridePendingTransition(0, 0)

            val file = File(filesDir, "player.json")
            if (!file.exists());

            val jsonString = file.readText()
            if (jsonString.isEmpty());

            val jsonObject = JSONObject(jsonString)

            val isPlaying = jsonObject.getBoolean("isPlaying")
            val url = jsonObject.optString("url", "")
            val city = jsonObject.optString("city", "")
            val stationName = jsonObject.optString("stationName", "")
            val icon = jsonObject.optString("icon", "")

            if (isPlaying) {
                val jsonObjectToSave = JSONObject().apply {
                    put("change_theme", true)
                    put("isPlaying", true)
                    put("url", url)
                    put("city", city)
                    put("stationName", stationName)
                    put("icon", icon)
                }
                file.writeText(jsonObjectToSave.toString())
            }
        }

        findViewById<ImageView>(R.id.radio_player).setOnClickListener {
            togglePlayPause(it as ImageView)
        }

        setupNavigation(container, radioStations, radioOkolica, radioSwiatowe)
        displayRadioStations(radioStations)
        updateStationCount(radioStations.size)
    }

    private fun loadPlayerState() {
        val file = File(filesDir, "player.json")
        if (!file.exists()) return

        val jsonString = file.readText().takeIf { it.isNotEmpty() } ?: return
        val jsonObject = JSONObject(jsonString)

        isPlaying = jsonObject.getBoolean("isPlaying")
        url = jsonObject.getString("url")
        city = jsonObject.getString("city")
        stationName = jsonObject.getString("stationName")
        icon = jsonObject.getString("icon")

        val iconViewPlayer: ImageView = findViewById(R.id.radio_icon_player)
        val nameViewPlayer: TextView = findViewById(R.id.radio_name_player)
        val cityViewPlayer: TextView = findViewById(R.id.radio_container_city)

        iconViewPlayer.setImageResource(resources.getIdentifier(icon.replace("@drawable/", ""), "drawable", packageName))
        nameViewPlayer.text = stationName
        cityViewPlayer.text = city

        val ViewPlayer: ImageView = findViewById(R.id.radio_player)
        if (isPlaying as Boolean) {
            ViewPlayer.setImageResource(R.drawable.pause_button)
            isPlaying = true
        } else {
            ViewPlayer.setImageResource(R.drawable.play_button)
            isPlaying = false
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
            switchLayout(container, R.layout.radio_krajowe) {
                displayRadioStations(radioStations)
                updateStationCount(radioStations.size)
                findViewById<TextView>(R.id.textView).text = "Wszystkie"
            }
        }

        findViewById<LinearLayout>(R.id.w_okolicy).setOnClickListener {
            switchLayout(container, R.layout.radio_okolice) {
                displayRadioOkolicaStations(radioOkolicaStations)
                updateStationCount(radioOkolicaStations.flatMap { it.stations }.size)
                findViewById<TextView>(R.id.textView).text = "Wybierz Region"
            }
        }

        findViewById<LinearLayout>(R.id.swiatowe).setOnClickListener {
            switchLayout(container, R.layout.radio_odkrywaj) {
                displayRadioSwiatoweStations(radioSwiatowe)
                updateStationCount(radioSwiatowe.flatMap { it.stations }.size)
                findViewById<TextView>(R.id.textView).text = "Wybierz Kraj"
            }
        }

        findViewById<LinearLayout>(R.id.biblioteka).setOnClickListener {
            switchLayout(container, R.layout.library_container) {
                displayRadioLibrarySwiatowe(
                    radioSwiatowe.flatMap { it.stations },
                    radioOkolicaStations.flatMap { it.stations }
                )
                findViewById<TextView>(R.id.station_count_view).text = "Ulubione"
                findViewById<TextView>(R.id.textView).text = "To co misie lubią najbardziej"
            }
        }

        findViewById<View>(R.id.settings).setOnClickListener {
            switchLayout(container, R.layout.settings) { setupSettingsInteractions() }
            findViewById<TextView>(R.id.textView).text = "Ustawienia"
            findViewById<TextView>(R.id.station_count_view).text = ":)"

            val checkBoxLight = findViewById<View>(R.id.check_box_light_theme)
            val checkBoxDark = findViewById<View>(R.id.check_box_dark_theme)

            sharedPreferences = getSharedPreferences("MODE", Context.MODE_PRIVATE)
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

    private fun switchLayout(container: LinearLayout, layoutResId: Int, setup: (() -> Unit)? = null) {
        container.removeAllViews()
        LayoutInflater.from(this).inflate(layoutResId, container, true)
        setup?.invoke()
    }

    private fun updateStationCount(count: Int) {
        findViewById<TextView>(R.id.station_count_view).text = "$count"
    }

    private fun setupSettingsInteractions() {
        findViewById<LinearLayout>(R.id.discord)?.setOnClickListener {
            openWebsite("https://discord.gg/hernrd9VWc")
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

            val file = File(filesDir, "player.json")

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

            val file = File(filesDir, "player.json")

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

            findViewById<TextView>(R.id.textView).text = "Zobacz kto mnie stworzył"
            findViewById<TextView>(R.id.station_count_view).text = ":)"

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

    private fun <T> loadStationsFromRaw(resourceId: Int, clazz: Class<T>): List<T> {
        val json = resources.openRawResource(resourceId).bufferedReader().use { it.readText() }
        val jsonArray = JsonParser.parseString(json).asJsonArray
        return jsonArray.map { Gson().fromJson(it, clazz) }
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
                    starView.setBackgroundResource(R.drawable.star)
                } else {
                    starView.setBackgroundResource(R.drawable.star_2)
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
                    starView.setBackgroundResource(R.drawable.star)
                } else {
                    starView.setBackgroundResource(R.drawable.star_2)
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
                    findViewById<TextView>(R.id.textView).text = "${wojewodztwo.woj}"
                    findViewById<TextView>(R.id.station_count_view).text =
                        "${wojewodztwo.stations.size}"
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
            starView.setBackgroundResource(R.drawable.star)
        } else {
            starView.setBackgroundResource(R.drawable.star_2)
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
                    findViewById<TextView>(R.id.textView).text = "${swiatowe.country}"
                    findViewById<TextView>(R.id.station_count_view).text =
                        "${swiatowe.stations.size}"
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
                    starView.setBackgroundResource(R.drawable.star)
                } else {
                    starView.setBackgroundResource(R.drawable.star_2)
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

        val file = File(filesDir, "player.json")
        file.writer().use { it.write(jsonObject.toString()) }

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

        val file = File(filesDir, "player.json")
        val fileWriter = FileWriter(file)
        fileWriter.use {
            it.write(jsonObject.toString())
        }

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

        val file = File(filesDir, "player.json")
        val fileWriter = FileWriter(file)
        fileWriter.use {
            it.write(jsonObject.toString())
        }

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
        val file = File(context.filesDir, FAVORITES_FILE)
        if (!file.exists()) return mutableSetOf()

        val type = object : TypeToken<MutableSet<String>>() {}.type
        return Gson().fromJson(FileReader(file), type) ?: mutableSetOf()
    }

    private fun saveFavorites(context: Context, favorites: MutableSet<String>) {
        val file = File(context.filesDir, FAVORITES_FILE)
        FileWriter(file).use { writer ->
            Gson().toJson(favorites, writer)
        }
    }

    private fun toggleFavorite(context: Context, stationName: String, starView: ImageView) {
        val favorites = getFavorites(context)
        val wasFavorite = favorites.contains(stationName)

        if (wasFavorite) {
            favorites.remove(stationName)
            starView.setBackgroundResource(R.drawable.star_2)
        } else {
            favorites.add(stationName)
            starView.setBackgroundResource(R.drawable.star)
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
        val file = File(filesDir, "player.json")

        if (file.exists()) {
            try {
                val jsonString = FileReader(file).readText()

                if (jsonString.isNotEmpty()) {
                    val jsonObject = JSONObject(jsonString)

                    isPlaying = jsonObject.getBoolean("isPlaying")
                    url = jsonObject.getString("url")
                    city = jsonObject.getString("city")
                    stationName = jsonObject.getString("stationName")
                    icon = jsonObject.getString("icon")

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

                    if (isPlaying as Boolean) {
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

                    FileWriter(file).use { it.write(updatedJsonObject.toString()) }
                }
            } catch (e: Exception) {
                Log.e("togglePlayPause", "Error reading or parsing JSON: ${e.message}")
                Toast.makeText(this, "Wystąpił problem z plikiem danych!", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Brak pliku player.json!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRadio() {
        exoPlayer?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null

        unregisterReceiver(radioStopReceiver)

        val file = File(filesDir, "player.json")
        if (!file.exists()) return

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
            return
        }
    }
}
