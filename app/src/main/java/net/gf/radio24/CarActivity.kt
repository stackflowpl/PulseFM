package net.gf.radio24

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject
import java.io.File
import java.io.FileReader
import java.io.FileWriter

class CarActivity : AppCompatActivity() {

    private var exoPlayer: ExoPlayer? = null

    private var isPlaying: Boolean = false
    private var url: String = ""
    private var city: String = ""
    private var stationName: String = ""
    private var icon: String = ""

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_car)

        window.setDecorFitsSystemWindows(false)

        exoPlayer = ExoPlayer.Builder(application).build()

        loadPlayerState()
        FirebaseFirestore.setLoggingEnabled(true)

        findViewById<ImageView>(R.id.radio_exit).setOnClickListener {
            navigateToMainActivity()
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

        updateUI()
    }

    private fun updateUI() {
        val iconViewPlayer: ImageView = findViewById(R.id.radio_icon_player)
        val nameViewPlayer: TextView = findViewById(R.id.radio_name_player)

        if (icon.isNotEmpty()) {
            iconViewPlayer.setImageResource(resources.getIdentifier(icon.replace("@drawable/", ""), "drawable", packageName))
        }
        nameViewPlayer.text = stationName

        val viewPlayer: ImageView = findViewById(R.id.radio_player)
        val textPlayStatus: TextView = findViewById(R.id.text_car_play)

        if (url.isEmpty()) {
            Toast.makeText(this, "Brak danych o stacji radiowej!", Toast.LENGTH_SHORT).show()
        } else {
            if (isPlaying) {
                viewPlayer.setImageResource(R.drawable.pause_button)
                textPlayStatus.text = "Play"
            } else {
                viewPlayer.setImageResource(R.drawable.play_button)
                textPlayStatus.text = "Pause"
            }
        }
    }

    private fun togglePlayPause(viewPlayer: ImageView) {
        if (url.isEmpty()) {
            Toast.makeText(this, "Brak danych o stacji radiowej!", Toast.LENGTH_SHORT).show()
            return
        }

        isPlaying = !isPlaying
        if (isPlaying) {
            playRadio(url)
            viewPlayer.setImageResource(R.drawable.pause_button)
            findViewById<TextView>(R.id.text_car_play).text = "Play"
        } else {
            stopRadio()
            viewPlayer.setImageResource(R.drawable.play_button)
            findViewById<TextView>(R.id.text_car_play).text = "Pause"
        }

        savePlayerState()
    }

    private fun playRadio(url: String) {
        val file = File(filesDir, "player.json")

        if (file.exists()) {
            try {
                val jsonString = FileReader(file).readText()

                if (jsonString.isNotEmpty()) {
                    val jsonObject = JSONObject(jsonString)

                    isPlaying = jsonObject.getBoolean("isPlaying")
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
                        isPlaying = false
                    } else {
                        intent.action = "PLAY"
                        startService(intent)
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

    private fun savePlayerState() {
        val jsonObject = JSONObject().apply {
            put("isPlaying", isPlaying)
            put("url", url)
            put("city", city)
            put("stationName", stationName)
            put("icon", icon)
            put("change_theme", false)
        }

        val file = File(filesDir, "player.json")
        FileWriter(file).use { writer ->
            writer.write(jsonObject.toString())
        }
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
        stopRadio()
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null

        savePlayerState()
    }
}
