package net.gf.radio24

import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class SplashActivity : ComponentActivity() {

    private lateinit var statusText: TextView
    private lateinit var statusTextAwait: TextView
    private lateinit var statusTextFinish: TextView

    private val API_STATIONS = "https://api.stackflow.pl/__api/radio24/stations"
    private val API_OKOLICE = "https://api.stackflow.pl/__api/radio24/okolice"
    private val API_SWIAT = "https://api.stackflow.pl/__api/radio24/swiat"
    private val API_TOP10POP = "https://api.stackflow.pl/__api/radio24/top10pop"

    data class Swiatowe(val country: String, val icon: String, val stations: List<RadioSwiatowe>)
    data class RadioSwiatowe(val name: String, val city: String, val url: String, val icon: String)
    data class Wojewodztwo(val woj: String, val icon: String, val stations: List<RadioStationOkolica>)
    data class RadioStationOkolica(val name: String, val city: String, val url: String, val icon: String)
    data class RadioStation(val name: String, val city: String, val url: String, val icon: String)
    data class Top10popStation(val name: String, val city: String, val url: String, val icon: String)

    private val DATABASE_DIR = "database"
    private val STATIONS_FILE = "stations.json"
    private val OKOLICE_FILE = "okolice.json"
    private val SWIAT_FILE = "swiat.json"
    private val TOP10POP_FILE = "top10pop.json"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        statusText = findViewById(R.id.statusText)
        statusTextAwait = findViewById(R.id.statusTextAwiat)
        statusTextFinish = findViewById(R.id.statusTextFinish)

        initializeDatabase()
        checkInternetAndLoadData()
    }

    private fun initializeDatabase() {
        try {
            val databaseDir = File(filesDir, DATABASE_DIR)
            if (!databaseDir.exists()) {
                databaseDir.mkdirs()
                Log.d("Database", "Created database directory in SplashActivity")
            }
        } catch (e: Exception) {
            Log.e("Database", "Error initializing database: ${e.message}")
        }
    }

    private fun checkInternetAndLoadData() {
        statusTextAwait.text = "Sprawdzanie połączenia..."

        if (isInternetAvailable()) {
            statusTextAwait.text = "Połączenie ustanowione."
            statusTextFinish.text = "Pobieranie danych..."
            loadDataFromAPI()
        } else {
            statusTextAwait.text = "Brak dostępu do internetu."
            statusTextFinish.text = "Sprawdzanie ponownie..."
            Handler(Looper.getMainLooper()).postDelayed({
                checkInternetAndLoadData()
            }, 2000)
        }
    }

    private fun loadDataFromAPI() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                var successCount = 0
                var totalAPIs = 4

                withContext(Dispatchers.Main) {
                    statusTextAwait.text = "Pobieranie stacji krajowych..."
                }

                try {
                    val stationsJson = fetchFromAPI(API_STATIONS)
                    val stations: List<RadioStation> = Gson().fromJson(stationsJson, object : TypeToken<List<RadioStation>>() {}.type)
                    saveToFile(STATIONS_FILE, stationsJson)
                    successCount++
                    Log.d("API", "Stations loaded successfully: ${stations.size} stations")
                } catch (e: Exception) {
                    Log.e("API", "Error loading stations: ${e.message}")
                }

                withContext(Dispatchers.Main) {
                    statusTextAwait.text = "Pobieranie stacji regionalnych..."
                }

                try {
                    val okolicaJson = fetchFromAPI(API_OKOLICE)
                    val okolica: List<Wojewodztwo> = Gson().fromJson(okolicaJson, object : TypeToken<List<Wojewodztwo>>() {}.type)
                    saveToFile(OKOLICE_FILE, okolicaJson)
                    successCount++
                    Log.d("API", "Okolica loaded successfully: ${okolica.size} regions")
                } catch (e: Exception) {
                    Log.e("API", "Error loading okolica: ${e.message}")
                }

                withContext(Dispatchers.Main) {
                    statusTextAwait.text = "Pobieranie stacji światowych..."
                }

                try {
                    val swiatoweJson = fetchFromAPI(API_SWIAT)
                    val swiatowe: List<Swiatowe> = Gson().fromJson(swiatoweJson, object : TypeToken<List<Swiatowe>>() {}.type)
                    saveToFile(SWIAT_FILE, swiatoweJson)
                    successCount++
                    Log.d("API", "Swiatowe loaded successfully: ${swiatowe.size} countries")
                } catch (e: Exception) {
                    Log.e("API", "Error loading swiatowe: ${e.message}")
                }

                withContext(Dispatchers.Main) {
                    statusTextAwait.text = "Pobieranie stacji top 10 pop..."
                }

                try {
                    val top10popJson = fetchFromAPI(API_TOP10POP)
                    val top10pop: List<Top10popStation> = Gson().fromJson(top10popJson, object : TypeToken<List<Top10popStation>>() {}.type)
                    saveToFile(TOP10POP_FILE, top10popJson)
                    successCount++
                    Log.d("API", "Top10pop loaded successfully: ${top10pop.size} regions")
                } catch (e: Exception) {
                    Log.e("API", "Error loading okolica: ${e.message}")
                }

                withContext(Dispatchers.Main) {
                    if (successCount > 3) {
                        statusTextAwait.text = "Dane załadowane pomyślnie ($successCount/$totalAPIs)."
                        statusTextFinish.text = "Uruchamianie aplikacji..."
                        Handler(Looper.getMainLooper()).postDelayed({
                            proceedToMainActivity()
                        }, 1000)
                    } else {
                        statusTextAwait.text = "Nie udało się pobrać żadnych danych."
                        statusTextFinish.text = "Sprawdzanie ponownie..."
                        Handler(Looper.getMainLooper()).postDelayed({
                            checkInternetAndLoadData()
                        }, 3000)
                    }
                }

            } catch (e: Exception) {
                Log.e("API", "General error loading data: ${e.message}")
                withContext(Dispatchers.Main) {
                    statusTextAwait.text = "Błąd podczas pobierania danych."
                    statusTextFinish.text = "Ponowienie próby..."
                    Handler(Looper.getMainLooper()).postDelayed({
                        checkInternetAndLoadData()
                    }, 3000)
                }
            }
        }
    }

    private suspend fun fetchFromAPI(urlString: String): String {
        return withContext(Dispatchers.IO) {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "Radio24-Android")

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    throw Exception("HTTP ${connection.responseCode}: ${connection.responseMessage}")
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun saveToFile(fileName: String, jsonData: String) {
        try {
            val file = File(File(filesDir, DATABASE_DIR), fileName)
            file.writeText(jsonData)
            Log.d("File", "Saved $fileName successfully")
        } catch (e: Exception) {
            Log.e("File", "Error saving $fileName: ${e.message}")
        }
    }

    private fun isInternetAvailable(): Boolean {
        return try {
            val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) {
            Log.e("Network", "Error checking internet connectivity: ${e.message}")
            false
        }
    }

    private fun proceedToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
        overridePendingTransition(0, 0)
    }
}