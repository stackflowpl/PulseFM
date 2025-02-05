package net.gf.radio24

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject
import java.io.File
import java.io.FileReader
import java.io.FileWriter

class CarActivity : AppCompatActivity() {

    private var exoPlayer: ExoPlayer? = null

    var isPlaying: Boolean? = false
    var url = ""
    var city = ""
    var stationName = ""
    var icon = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_car)

        exoPlayer = ExoPlayer.Builder(application).build()

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        loadPlayerState()
        FirebaseFirestore.setLoggingEnabled(true)

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

                if (url.isEmpty()) {
                    Toast.makeText(this, "Brak danych o stacji radiowej!", Toast.LENGTH_SHORT).show()
                } else {
                    if (isPlaying as Boolean) {
                        playRadio(url)
                        val viewPlayer = findViewById<ImageView>(R.id.radio_player)

                        viewPlayer.setImageResource(R.drawable.pause_button)
                        findViewById<TextView>(R.id.text_car_play).text = "Play"
                    }
                }
            }
        }

        findViewById<ImageView>(R.id.radio_exit).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
            stopRadio()
        }

        findViewById<ImageView>(R.id.radio_player).setOnClickListener {
            togglePlayPause(it as ImageView)
        }
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

        iconViewPlayer.setImageResource(resources.getIdentifier(icon.replace("@drawable/", ""), "drawable", packageName))
        nameViewPlayer.text = stationName
    }

    private fun togglePlayPause(viewPlayer: ImageView) {
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

                if (url.isEmpty()) {
                    Toast.makeText(this, "Brak danych o stacji radiowej!", Toast.LENGTH_SHORT).show()
                } else {
                    if (isPlaying as Boolean) {
                        stopRadio()
                        viewPlayer.setImageResource(R.drawable.play_button)

                        val jsonObject = JSONObject().apply {
                            put("change_theme", false)
                            put("isPlaying", false)
                            put("url",  url)
                            put("city", city)
                            put("stationName", stationName)
                            put("icon", icon)
                        }

                        findViewById<TextView>(R.id.text_car_play).text = "Pause"

                        val file = File(filesDir, "player.json")
                        val fileWriter = FileWriter(file)

                        fileWriter.use {
                            it.write(jsonObject.toString())
                        }
                    } else {
                        playRadio(url)
                        viewPlayer.setImageResource(R.drawable.pause_button)

                        val jsonObject = JSONObject().apply {
                            put("change_theme", false)
                            put("isPlaying", true)
                            put("url",  url)
                            put("city", city)
                            put("stationName", stationName)
                            put("icon", icon)
                        }

                        findViewById<TextView>(R.id.text_car_play).text = "Play"

                        val file = File(filesDir, "player.json")
                        val fileWriter = FileWriter(file)

                        fileWriter.use {
                            it.write(jsonObject.toString())
                        }
                    }
                }
            }
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