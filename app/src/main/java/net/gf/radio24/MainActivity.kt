package net.gf.radio24

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
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

class MainActivity : ComponentActivity() {

    private lateinit var consentInformation: ConsentInformation

    private var exoPlayer: ExoPlayer? = null
    private val playerStatus = PlayerStatus()

    private lateinit var radioTime: TextView
    private var seconds = 0
    private var isRunning = false
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                seconds++
                val hours = seconds / 3600
                val minutes = (seconds % 3600) / 60
                val secs = seconds % 60

                val timeFormatted = String.format("%02d:%02d:%02d", hours, minutes, secs)
                radioTime.text = timeFormatted

                handler.postDelayed(this, 1000)
            }
        }
    }

    data class Wojewodztwo(val woj: String, val stations: List<RadioStationOkolica>)
    data class RadioStationOkolica(val name: String, val city: String, val url: String, val icon: String)
    data class RadioStation(val name: String, val url: String, val icon: String)
    data class PlayerStatus(var isPlaying: Boolean = false, var url: String = "", var stationName: String = "", var city: String = "", var icon: String = "")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val params = ConsentRequestParameters.Builder().build()
        consentInformation = UserMessagingPlatform.getConsentInformation(this)

        consentInformation.requestConsentInfoUpdate(
            this, params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { formError ->
                    if (formError != null) {
                        println("Błąd formularza zgody: ${formError.message}")
                    }

                    if (consentInformation.canRequestAds()) {
                        initializeAds()
                    }
                }
            },
            { requestError ->
                println("Błąd uzyskiwania zgody: ${requestError.message}")
            }
        )

        initializeAds()

        FirebaseFirestore.setLoggingEnabled(true)

        exoPlayer = ExoPlayer.Builder(this).build()

        val container: LinearLayout = findViewById(R.id.container)

        loadLayout(R.layout.radio_krajowe, container)

        val radioStations = loadStationsFromRaw<RadioStation>(R.raw.radio_stations)
        val radioOkolicaStations = loadStationsFromRaw<Wojewodztwo>(R.raw.radio_okolice)

        setupNavigation(container, radioStations, radioOkolicaStations)
        displayRadioStations(radioStations)
        updateStationCount(radioStations.size)
    }

    private fun loadLayout(layoutResId: Int, container: LinearLayout) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)
        inflater.inflate(layoutResId, container, true)
    }

    private fun setupNavigation(
        container: LinearLayout,
        radioStations: List<RadioStation>,
        radioOkolicaStations: List<Wojewodztwo>
    ) {
        findViewById<LinearLayout>(R.id.krajowe).setOnClickListener {
            switchLayout(container, R.layout.radio_krajowe) {
                displayRadioStations(radioStations)
                updateStationCount(radioStations.size)
                findViewById<TextView>(R.id.textView).text = "Polskie Radio Stacje"
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
            switchLayout(container, R.layout.radio_odkrywaj)
        }

        findViewById<View>(R.id.settings).setOnClickListener {
            switchLayout(container, R.layout.settings) { setupSettingsInteractions() }
            findViewById<TextView>(R.id.textView).text = "Ustawienia"
            findViewById<TextView>(R.id.station_count_view).text = ":)"
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
        findViewById<LinearLayout>(R.id.discord)?.setOnClickListener { openWebsite("https://discord.gg/hernrd9VWc") }
        findViewById<LinearLayout>(R.id.github)?.setOnClickListener { openWebsite("https://github.com/gofluxpl/Radio24") }
    }

    private fun openWebsite(url: String) {
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).also { startActivity(it) }
    }

    private fun initializeAds() {
        CoroutineScope(Dispatchers.IO).launch {
            MobileAds.initialize(this@MainActivity) {}
            runOnUiThread {
                findViewById<AdView>(R.id.adView)?.loadAd(AdRequest.Builder().build())
            }
        }
    }

    private inline fun <reified T> loadStationsFromRaw(resourceId: Int): List<T> {
        val json = resources.openRawResource(resourceId).bufferedReader().use { it.readText() }
        val type = object : TypeToken<List<T>>() {}.type
        return Gson().fromJson(json, type)
    }

    private fun displayRadioStations(radioStations: List<RadioStation>) {
        val radioContainer = findViewById<LinearLayout>(R.id.radio_container_krajowe) ?: return

        if (radioStations.isNotEmpty()) {
            val viewPlayer = findViewById<ImageView>(R.id.radio_player)

            radioStations.forEach { station ->
                val radioView = LayoutInflater.from(this).inflate(R.layout.radio_item, null)
                val iconView: ImageView = radioView.findViewById(R.id.radio_icon)
                val nameView: TextView = radioView.findViewById(R.id.radio_name)

                iconView.setImageResource(resources.getIdentifier(station.icon.replace("@drawable/", ""), "drawable", packageName))
                nameView.text = station.name

                radioContainer.addView(radioView)

                radioView.setOnClickListener {
                    onRadioStationSelected(station, viewPlayer)
                    radioTime = findViewById(R.id.radio_time)
                    resetTimer()
                    radioTime.visibility = View.VISIBLE
                }
            }

            viewPlayer.setOnClickListener {
                togglePlayPause(viewPlayer)
            }
        } else {
            Toast.makeText(this, "Brak dostępnych stacji radiowych!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun displayRadioStationsOkolica(radioStations: List<RadioStationOkolica>) {
        val radioContainer = findViewById<LinearLayout>(R.id.radio_okolice_container) ?: return

        if (radioStations.isNotEmpty()) {
            val viewPlayer = findViewById<ImageView>(R.id.radio_player)

            radioStations.forEach { station ->
                val radioView = LayoutInflater.from(this).inflate(R.layout.okolice_item, null)
                val iconView: ImageView = radioView.findViewById(R.id.radio_icon)
                val nameView: TextView = radioView.findViewById(R.id.radio_name)
                val cityView: TextView = radioView.findViewById(R.id.radio_city)

                iconView.setImageResource(resources.getIdentifier(station.icon.replace("@drawable/", ""), "drawable", packageName))
                nameView.text = station.name
                cityView.text = station.city

                radioContainer.addView(radioView)

                radioView.setOnClickListener {
                    onRadioStationSelected(station, viewPlayer)
                    radioTime = findViewById(R.id.radio_time)
                    resetTimer()
                    radioTime.visibility = View.VISIBLE
                }
            }

            viewPlayer.setOnClickListener {
                togglePlayPause(viewPlayer)
            }
        } else {
            Toast.makeText(this, "Brak dostępnych stacji radiowych!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun displayRadioOkolicaStations(radioStations: List<Wojewodztwo>) {
        val radioContainer = findViewById<LinearLayout>(R.id.radio_container_okolice) ?: return

        val viewPlayer = findViewById<ImageView>(R.id.radio_player)

        radioStations.forEach { wojewodztwo ->
            val wojView = LayoutInflater.from(this).inflate(R.layout.wojewodztwo_item, null)
            val nameView: TextView = wojView.findViewById(R.id.radio_name)
            val countView: TextView = wojView.findViewById(R.id.radio_count_okolice)

            val container: LinearLayout = findViewById(R.id.container)

            val liczbaStacji = wojewodztwo.stations.size

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

    private fun String.trimPrefix(prefix: String) = this.removePrefix(prefix)

    private fun onRadioStationSelected(station: RadioStation, viewPlayer: ImageView) {
        playRadio(station.url)

        val iconViewPlayer: ImageView = findViewById(R.id.radio_icon_player)
        val nameViewPlayer: TextView = findViewById(R.id.radio_name_player)

        playerStatus.apply {
            isPlaying = true
            url = station.url
            stationName = station.name
            icon = station.icon
        }

        viewPlayer.setImageResource(R.drawable.pause_button)

        iconViewPlayer.setImageResource(resources.getIdentifier(station.icon.replace("@drawable/", ""), "drawable", packageName))
        nameViewPlayer.text = station.name
    }

    private fun onRadioStationSelected(station: RadioStationOkolica, viewPlayer: ImageView) {
        playRadio(station.url)

        val iconViewPlayer: ImageView = findViewById(R.id.radio_icon_player)
        val nameViewPlayer: TextView = findViewById(R.id.radio_name_player)
        val cityViewPlayer: TextView = findViewById(R.id.radio_container_city)

        playerStatus.apply {
            isPlaying = true
            url = station.url
            city = station.city
            stationName = station.name
            icon = station.icon
        }

        viewPlayer.setImageResource(R.drawable.pause_button)

        iconViewPlayer.setImageResource(resources.getIdentifier(station.icon.replace("@drawable/", ""), "drawable", packageName))
        nameViewPlayer.text = station.name
        cityViewPlayer.text = station.city
    }

    private fun resetTimer() {
        isRunning = false
        handler.removeCallbacks(updateRunnable)
        seconds = 0
        radioTime.text = "00:00:00"
        isRunning = true
        handler.post(updateRunnable)
    }

    private fun togglePlayPause(viewPlayer: ImageView) {
        if (playerStatus.url.isEmpty()) {
            Toast.makeText(this, "Brak danych o stacji radiowej!", Toast.LENGTH_SHORT).show()
        } else {
            if (playerStatus.isPlaying) {
                stopRadio()
                toggleTimer()
                viewPlayer.setImageResource(R.drawable.play_button)
            } else {
                playRadio(playerStatus.url)
                toggleTimer()
                viewPlayer.setImageResource(R.drawable.pause_button)
            }
            playerStatus.isPlaying = !playerStatus.isPlaying
        }
    }

    private fun toggleTimer() {
        isRunning = !isRunning
        if (isRunning) {
            handler.post(updateRunnable)
        } else {
            handler.removeCallbacks(updateRunnable)
        }
    }

    private fun playRadio(url: String) {
        exoPlayer?.apply {
            stop()
            clearMediaItems()
            setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            prepare()
            play()
        }
    }

    private fun stopRadio() {
        exoPlayer?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
    }
}
