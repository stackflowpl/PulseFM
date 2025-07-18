package net.gf.radio24

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class SplashActivity : ComponentActivity() {

    private lateinit var statusText: TextView
    private lateinit var statusTextAwait: TextView
    private lateinit var statusTextFinish: TextView

    private lateinit var sharedPreferences: SharedPreferences

    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private companion object {
        const val API_STATIONS = "https://api.stackflow.pl/__api/radio24/stations"
        const val API_OKOLICE = "https://api.stackflow.pl/__api/radio24/okolice"
        const val API_SWIAT = "https://api.stackflow.pl/__api/radio24/swiat"
        const val API_TOP10POP = "https://api.stackflow.pl/__api/radio24/top10pop"

        const val DATABASE_DIR = "database"
        const val STATIONS_FILE = "stations.json"
        const val OKOLICE_FILE = "okolice.json"
        const val SWIAT_FILE = "swiat.json"
        const val TOP10POP_FILE = "top10pop.json"

        const val PREF_NAME = "Radio24Preferences"
        const val PREF_PERMISSIONS_ACCEPTED = "permissions_accepted"
        const val PREF_DATA_CONSENT = "data_consent"

        const val CONNECT_TIMEOUT = 15000
        const val READ_TIMEOUT = 15000
        const val RETRY_DELAY = 2000L
        const val SUCCESS_DELAY = 1000L
    }

    data class Swiatowe(val country: String, val icon: String, val stations: List<RadioSwiatowe>)
    data class RadioSwiatowe(val name: String, val city: String, val url: String, val icon: String)
    data class Wojewodztwo(val woj: String, val icon: String, val stations: List<RadioStationOkolica>)
    data class RadioStationOkolica(val name: String, val city: String, val url: String, val icon: String)
    data class RadioStation(val name: String, val city: String, val url: String, val icon: String)
    data class Top10popStation(val name: String, val city: String, val url: String, val icon: String)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        handlePermissionResults(permissions)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        initializeViews()
        initializePreferences()
        initializeDatabase()

        Handler(Looper.getMainLooper()).postDelayed({
            checkPermissionsAndConsent()
        }, 500)
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }

    private fun initializeViews() {
        statusText = findViewById(R.id.statusText)
        statusTextAwait = findViewById(R.id.statusTextAwiat)
        statusTextFinish = findViewById(R.id.statusTextFinish)

        statusText.text = "Inicjalizacja aplikacji..."
        statusTextAwait.text = "Przygotowywanie..."
        statusTextFinish.text = ""
    }

    private fun initializePreferences() {
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
    }

    private fun initializeDatabase() {
        try {
            val databaseDir = File(filesDir, DATABASE_DIR)
            if (!databaseDir.exists()) {
                val created = databaseDir.mkdirs()
                Log.d("Database", "Database directory created: $created")
            }
        } catch (e: Exception) {
            Log.e("Database", "Error initializing database", e)
        }
    }

    private fun checkPermissionsAndConsent() {
        val permissionsAccepted = sharedPreferences.getBoolean(PREF_PERMISSIONS_ACCEPTED, false)
        val dataConsentGiven = sharedPreferences.getBoolean(PREF_DATA_CONSENT, false)

        when {
            !permissionsAccepted -> {
                updateStatus("Sprawdzanie uprawnień...", "Wymagana akceptacja", "")
                showPermissionDialog()
            }
            !dataConsentGiven -> {
                updateStatus("Sprawdzanie zgód...", "Wymagana akceptacja", "")
                showDataConsentDialog()
            }
            else -> {
                updateStatus("Sprawdzam połączenie z internetem:", "Łączenie...", "")
                checkInternetAndLoadData()
            }
        }
    }

    private fun showPermissionDialog() {
        AlertDialog.Builder(this, R.style.AlertDialogCustom)
            .setTitle("Uprawnienia aplikacji")
            .setMessage("""
                Radio24 potrzebuje następujących uprawnień:
                
                • Powiadomienia - informacje o odtwarzanych stacjach
                • Nagrywanie dźwięku - rozpoznawanie muzyki (opcjonalne)
                • Dostęp do plików - zapisywanie ulubionych stacji
                
                Czy wyrażasz zgodę na udzielenie uprawnień?
            """.trimIndent())
            .setPositiveButton("Akceptuj") { _, _ ->
                updateStatus("Sprawdzanie uprawnień...", "Przetwarzanie...", "")
                requestNecessaryPermissions()
            }
            .setNegativeButton("Odrzuć") { _, _ ->
                showPermissionDeniedDialog()
            }
            .setCancelable(false)
            .show()
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this, R.style.AlertDialogCustom)
            .setTitle("Uprawnienia wymagane")
            .setMessage("Aplikacja wymaga podstawowych uprawnień do prawidłowego działania.\n\nCzy chcesz spróbować ponownie?")
            .setPositiveButton("Spróbuj ponownie") { _, _ ->
                showPermissionDialog()
            }
            .setNegativeButton("Zamknij aplikację") { _, _ ->
                updateStatus("Zamykanie aplikacji...", "Do widzenia!", "")
                Handler(Looper.getMainLooper()).postDelayed({ finish() }, 1000)
            }
            .setCancelable(false)
            .show()
    }

    private fun requestNecessaryPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            updateStatus("Sprawdzanie uprawnień...", "Oczekiwanie na akceptację...", "")
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            savePermissionConsent(true)
        }
    }

    private fun handlePermissionResults(permissions: Map<String, Boolean>) {
        val allGranted = permissions.values.all { it }
        val criticalPermissionsDenied = permissions.entries.any { (permission, granted) ->
            !granted && (permission == Manifest.permission.POST_NOTIFICATIONS ||
                    permission == Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        when {
            allGranted -> {
                updateStatus("Uprawnienia przyznane", "Wszystko gotowe!", "")
                savePermissionConsent(true)
                Log.d("Permissions", "All permissions granted")
            }
            criticalPermissionsDenied -> {
                updateStatus("Uprawnienia odrzucone", "Wymagana akceptacja", "")
                showCriticalPermissionDialog()
            }
            else -> {
                updateStatus("Uprawnienia częściowo przyznane", "Kontynuowanie...", "")
                savePermissionConsent(true)
                Log.d("Permissions", "Some optional permissions denied, continuing")
            }
        }
    }

    private fun showCriticalPermissionDialog() {
        AlertDialog.Builder(this, R.style.AlertDialogCustom)
            .setTitle("Krytyczne uprawnienia")
            .setMessage("Niektóre kluczowe uprawnienia zostały odrzucone.\n\nAplikacja może nie działać prawidłowo.\n\nCzy chcesz spróbować ponownie?")
            .setPositiveButton("Spróbuj ponownie") { _, _ ->
                requestNecessaryPermissions()
            }
            .setNegativeButton("Kontynuuj mimo to") { _, _ ->
                updateStatus("Kontynuowanie...", "Ograniczona funkcjonalność", "")
                savePermissionConsent(true)
            }
            .setCancelable(false)
            .show()
    }

    private fun savePermissionConsent(accepted: Boolean) {
        sharedPreferences.edit()
            .putBoolean(PREF_PERMISSIONS_ACCEPTED, accepted)
            .apply()

        if (accepted) {
            Handler(Looper.getMainLooper()).postDelayed({
                checkDataConsent()
            }, 500)
        }
    }

    private fun checkDataConsent() {
        val dataConsentGiven = sharedPreferences.getBoolean(PREF_DATA_CONSENT, false)
        if (!dataConsentGiven) {
            updateStatus("Sprawdzanie zgód...", "Wymagana akceptacja", "")
            showDataConsentDialog()
        } else {
            updateStatus("Sprawdzam połączenie z internetem:", "Łączenie...", "")
            checkInternetAndLoadData()
        }
    }

    private fun showDataConsentDialog() {
        AlertDialog.Builder(this, R.style.AlertDialogCustom)
            .setTitle("Zgoda na przetwarzanie danych")
            .setMessage("""
                Radio24 będzie:
                
                • Pobierać listę stacji radiowych z internetu
                • Zapisywać dane w pamięci podręcznej urządzenia
                • Przechowywać Twoje preferencje lokalnie
                • Nie udostępniać danych osobowych stronom trzecim
                
                Czy wyrażasz zgodę na przetwarzanie danych?
            """.trimIndent())
            .setPositiveButton("Akceptuj") { _, _ ->
                updateStatus("Zgoda udzielona", "Przygotowywanie...", "")
                saveDataConsent(true)
                Handler(Looper.getMainLooper()).postDelayed({
                    checkInternetAndLoadData()
                }, 500)
            }
            .setNegativeButton("Odrzuć") { _, _ ->
                showDataConsentDeniedDialog()
            }
            .setCancelable(false)
            .show()
    }

    private fun showDataConsentDeniedDialog() {
        AlertDialog.Builder(this, R.style.AlertDialogCustom)
            .setTitle("Zgoda wymagana")
            .setMessage("Bez zgody na przetwarzanie danych aplikacja nie może pobrać listy stacji radiowych.\n\nCzy chcesz zmienić decyzję?")
            .setPositiveButton("Zmień decyzję") { _, _ ->
                showDataConsentDialog()
            }
            .setNegativeButton("Zamknij aplikację") { _, _ ->
                updateStatus("Zamykanie aplikacji...", "Brak zgody na dane", "")
                Handler(Looper.getMainLooper()).postDelayed({ finish() }, 1500)
            }
            .setCancelable(false)
            .show()
    }

    private fun saveDataConsent(accepted: Boolean) {
        sharedPreferences.edit()
            .putBoolean(PREF_DATA_CONSENT, accepted)
            .apply()
    }

    private fun checkInternetAndLoadData() {
        updateStatus("Sprawdzam połączenie z internetem:", "Testowanie połączenia...", "")

        if (isInternetAvailable()) {
            updateStatus("Sprawdzam połączenie z internetem:", "Połączenie ustanowione.", "Pobieranie danych...")
            Handler(Looper.getMainLooper()).postDelayed({
                loadDataFromAPI()
            }, 800)
        } else {
            updateStatus("Sprawdzam połączenie z internetem:", "Brak dostępu do internetu.", "Sprawdzanie ponownie...")
            scheduleRetry { checkInternetAndLoadData() }
        }
    }

    private fun loadDataFromAPI() {
        activityScope.launch {
            try {
                val apiCalls = listOf(
                    APICall("stacji krajowych", API_STATIONS, STATIONS_FILE) { json ->
                        Gson().fromJson<List<RadioStation>>(json, object : TypeToken<List<RadioStation>>() {}.type)
                    },
                    APICall("stacji regionalnych", API_OKOLICE, OKOLICE_FILE) { json ->
                        Gson().fromJson<List<Wojewodztwo>>(json, object : TypeToken<List<Wojewodztwo>>() {}.type)
                    },
                    APICall("stacji światowych", API_SWIAT, SWIAT_FILE) { json ->
                        Gson().fromJson<List<Swiatowe>>(json, object : TypeToken<List<Swiatowe>>() {}.type)
                    },
                    APICall("stacji top 10 pop", API_TOP10POP, TOP10POP_FILE) { json ->
                        Gson().fromJson<List<Top10popStation>>(json, object : TypeToken<List<Top10popStation>>() {}.type)
                    }
                )

                var successCount = 0
                val totalAPIs = apiCalls.size

                for ((index, apiCall) in apiCalls.withIndex()) {
                    updateStatus(
                        "Sprawdzam połączenie z internetem:",
                        "Pobieranie ${apiCall.description}...",
                        "Postęp: ${index + 1}/$totalAPIs"
                    )

                    try {
                        val jsonData = fetchFromAPI(apiCall.url)
                        val parsedData = apiCall.parser(jsonData)
                        saveToFile(apiCall.fileName, jsonData)
                        successCount++

                        val itemCount = when (parsedData) {
                            is List<*> -> parsedData.size
                            else -> 0
                        }

                        Log.d("API", "${apiCall.description} loaded successfully: $itemCount items")

                        delay(300)

                    } catch (e: Exception) {
                        Log.e("API", "Error loading ${apiCall.description}", e)
                        updateStatus(
                            "Sprawdzam połączenie z internetem:",
                            "Błąd pobierania ${apiCall.description}",
                            "Kontynuowanie..."
                        )
                        delay(500)
                    }
                }

                handleLoadingResult(successCount, totalAPIs)

            } catch (e: Exception) {
                Log.e("API", "General error loading data", e)
                updateStatus("Sprawdzam połączenie z internetem:", "Błąd podczas pobierania danych.", "Ponowienie próby...")
                scheduleRetry { checkInternetAndLoadData() }
            }
        }
    }

    private suspend fun handleLoadingResult(successCount: Int, totalAPIs: Int) {
        withContext(Dispatchers.Main) {
            when {
                successCount >= totalAPIs -> {
                    updateStatus(
                        "Sprawdzam połączenie z internetem:",
                        "Dane załadowane pomyślnie ($successCount/$totalAPIs).",
                        "Uruchamianie aplikacji..."
                    )
                    Handler(Looper.getMainLooper()).postDelayed({
                        proceedToMainActivity()
                    }, SUCCESS_DELAY)
                }
                else -> {
                    updateStatus(
                        "Sprawdzam połączenie z internetem:",
                        "Nie udało się pobrać danych.",
                        "Sprawdzanie ponownie..."
                    )
                    scheduleRetry { checkInternetAndLoadData() }
                }
            }
        }
    }

    private suspend fun fetchFromAPI(urlString: String): String {
        return withContext(Dispatchers.IO) {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection

            try {
                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT
                    readTimeout = READ_TIMEOUT
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "Radio24-Android")
                    setRequestProperty("Cache-Control", "no-cache")
                }

                when (connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        connection.inputStream.bufferedReader().use { it.readText() }
                    }
                    else -> {
                        throw Exception("HTTP ${connection.responseCode}: ${connection.responseMessage}")
                    }
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
            Log.d("File", "Successfully saved $fileName (${jsonData.length} bytes)")
        } catch (e: Exception) {
            Log.e("File", "Error saving $fileName", e)
        }
    }

    private fun isInternetAvailable(): Boolean {
        return try {
            val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) {
            Log.e("Network", "Error checking internet connectivity", e)
            false
        }
    }

    private fun updateStatus(status: String, await: String, finish: String) {
        runOnUiThread {
            if (status.isNotEmpty()) statusText.text = status
            if (await.isNotEmpty()) statusTextAwait.text = await
            if (finish.isNotEmpty()) statusTextFinish.text = finish
        }
    }

    private fun scheduleRetry(action: () -> Unit) {
        Handler(Looper.getMainLooper()).postDelayed(action, RETRY_DELAY)
    }

    private fun proceedToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
        overridePendingTransition(0, 0)
    }

    private data class APICall(
        val description: String,
        val url: String,
        val fileName: String,
        val parser: (String) -> Any
    )
}
