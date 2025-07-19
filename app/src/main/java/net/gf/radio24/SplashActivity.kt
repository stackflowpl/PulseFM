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
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.security.cert.X509Certificate
import javax.net.ssl.*

class SplashActivity : ComponentActivity() {

    private lateinit var statusText: TextView
    private lateinit var statusTextAwait: TextView
    private lateinit var statusTextFinish: TextView
    private lateinit var sharedPreferences: SharedPreferences

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
        const val PREF_LAST_UPDATE = "last_update"

        const val CONNECT_TIMEOUT = 30000
        const val READ_TIMEOUT = 30000
        const val MAX_RETRIES = 3
        const val RETRY_DELAY_BASE = 1000L
        const val SUCCESS_DELAY = 1000L
        const val CACHE_VALIDITY_HOURS = 24
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

        configureTrustManager()

        Handler(Looper.getMainLooper()).postDelayed({
            checkPermissionsAndConsent()
        }, 500)
    }

    private fun initializeViews() {
        statusText = findViewById(R.id.statusText)
        statusTextAwait = findViewById(R.id.statusTextAwiat)
        statusTextFinish = findViewById(R.id.statusTextFinish)

        updateStatus("Inicjalizacja aplikacji...", "Przygotowywanie...", "")
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

    private fun configureTrustManager() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })

                val sslContext = SSLContext.getInstance("SSL")
                sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
                HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
            }
        } catch (e: Exception) {
            Log.w("SSL", "Could not configure SSL for older devices", e)
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
                checkCacheAndLoadData()
            }
        }
    }

    private fun showPermissionDialog() {
        if (isFinishing || isDestroyed) return

        AlertDialog.Builder(this, R.style.AlertDialogCustom)
            .setTitle("Uprawnienia aplikacji")
            .setMessage("""
                Radio24 potrzebuje następujących uprawnień:
                
                • Powiadomienia - informacje o odtwarzanych stacjach
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
        if (isFinishing || isDestroyed) return

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

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
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
        val criticalDenied = permissions.entries.any { (permission, granted) ->
            !granted && permission == Manifest.permission.POST_NOTIFICATIONS &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        }

        if (criticalDenied) {
            updateStatus("Uprawnienia odrzucone", "Ograniczona funkcjonalność", "")
            showCriticalPermissionDialog()
        } else {
            updateStatus("Uprawnienia przyznane", "Wszystko gotowe!", "")
            savePermissionConsent(true)
        }
    }

    private fun showCriticalPermissionDialog() {
        if (isFinishing || isDestroyed) return

        AlertDialog.Builder(this, R.style.AlertDialogCustom)
            .setTitle("Ważne uprawnienia")
            .setMessage("Niektóre uprawnienia zostały odrzucone. Aplikacja będzie działać z ograniczoną funkcjonalnością.\n\nCzy chcesz kontynuować?")
            .setPositiveButton("Kontynuuj") { _, _ ->
                savePermissionConsent(true)
            }
            .setNegativeButton("Spróbuj ponownie") { _, _ ->
                requestNecessaryPermissions()
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
            showDataConsentDialog()
        } else {
            checkCacheAndLoadData()
        }
    }

    private fun showDataConsentDialog() {
        if (isFinishing || isDestroyed) return

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
                    checkCacheAndLoadData()
                }, 500)
            }
            .setNegativeButton("Odrzuć") { _, _ ->
                showDataConsentDeniedDialog()
            }
            .setCancelable(false)
            .show()
    }

    private fun showDataConsentDeniedDialog() {
        if (isFinishing || isDestroyed) return

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

    private fun checkCacheAndLoadData() {
        val lastUpdate = sharedPreferences.getLong(PREF_LAST_UPDATE, 0)
        val currentTime = System.currentTimeMillis()
        val cacheAge = currentTime - lastUpdate
        val cacheValidityMs = CACHE_VALIDITY_HOURS * 60 * 60 * 1000

        val hasValidCache = cacheAge < cacheValidityMs && hasAllCachedFiles()

        if (hasValidCache && !isInternetAvailable()) {
            updateStatus("Brak internetu", "Używanie danych z pamięci podręcznej", "Uruchamianie...")
            Handler(Looper.getMainLooper()).postDelayed({
                proceedToMainActivity()
            }, SUCCESS_DELAY)
        } else {
            updateStatus("Sprawdzam połączenie z internetem:", "Testowanie połączenia...", "")
            checkInternetAndLoadData()
        }
    }

    private fun hasAllCachedFiles(): Boolean {
        val files = listOf(STATIONS_FILE, OKOLICE_FILE, SWIAT_FILE, TOP10POP_FILE)
        return files.all { fileName ->
            val file = File(File(filesDir, DATABASE_DIR), fileName)
            file.exists() && file.length() > 0
        }
    }

    private fun checkInternetAndLoadData() {
        if (isInternetAvailable()) {
            updateStatus("Sprawdzam połączenie z internetem:", "Połączenie ustanowione.", "Pobieranie danych...")
            Handler(Looper.getMainLooper()).postDelayed({
                loadDataFromAPI()
            }, 800)
        } else {
            if (hasAllCachedFiles()) {
                updateStatus("Brak internetu", "Używanie danych z pamięci podręcznej", "Uruchamianie...")
                Handler(Looper.getMainLooper()).postDelayed({
                    proceedToMainActivity()
                }, SUCCESS_DELAY)
            } else {
                updateStatus("Sprawdzam połączenie z internetem:", "Brak dostępu do internetu.", "Sprawdzanie ponownie...")
                scheduleRetry(1) { checkInternetAndLoadData() }
            }
        }
    }

    private fun loadDataFromAPI() {
        lifecycleScope.launch {
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
                    if (isFinishing || isDestroyed) return@launch

                    updateStatus(
                        "Pobieranie danych:",
                        "Ładowanie ${apiCall.description}...",
                        "Postęp: ${index + 1}/$totalAPIs"
                    )

                    try {
                        val jsonData = fetchFromAPIWithRetry(apiCall.url)
                        val parsedData = apiCall.parser(jsonData)
                        saveToFile(apiCall.fileName, jsonData)
                        successCount++

                        val itemCount = when (parsedData) {
                            is List<*> -> parsedData.size
                            else -> 0
                        }
                        Log.d("API", "${apiCall.description} loaded successfully: $itemCount items")
                        delay(200)
                    } catch (e: Exception) {
                        Log.e("API", "Error loading ${apiCall.description}", e)
                        updateStatus(
                            "Pobieranie danych:",
                            "Błąd pobierania ${apiCall.description}",
                            "Kontynuowanie..."
                        )
                        delay(300)
                    }
                }

                handleLoadingResult(successCount, totalAPIs)
            } catch (e: Exception) {
                Log.e("API", "General error loading data", e)
                if (hasAllCachedFiles()) {
                    updateStatus("Błąd pobierania", "Używanie danych z pamięci podręcznej", "Uruchamianie...")
                    Handler(Looper.getMainLooper()).postDelayed({
                        proceedToMainActivity()
                    }, SUCCESS_DELAY)
                } else {
                    updateStatus("Błąd pobierania danych", "Ponowienie próby...", "")
                    scheduleRetry(1) { checkInternetAndLoadData() }
                }
            }
        }
    }

    private suspend fun handleLoadingResult(successCount: Int, totalAPIs: Int) {
        withContext(Dispatchers.Main) {
            if (isFinishing || isDestroyed) return@withContext

            when {
                successCount >= totalAPIs -> {
                    sharedPreferences.edit()
                        .putLong(PREF_LAST_UPDATE, System.currentTimeMillis())
                        .apply()

                    updateStatus(
                        "Pobieranie danych:",
                        "Dane załadowane pomyślnie ($successCount/$totalAPIs).",
                        "Uruchamianie aplikacji..."
                    )
                    Handler(Looper.getMainLooper()).postDelayed({
                        proceedToMainActivity()
                    }, SUCCESS_DELAY)
                }
                successCount > 0 -> {
                    updateStatus(
                        "Pobieranie danych:",
                        "Częściowo załadowane ($successCount/$totalAPIs).",
                        "Uruchamianie aplikacji..."
                    )
                    Handler(Looper.getMainLooper()).postDelayed({
                        proceedToMainActivity()
                    }, SUCCESS_DELAY)
                }
                hasAllCachedFiles() -> {
                    updateStatus(
                        "Błąd pobierania",
                        "Używanie danych z pamięci podręcznej",
                        "Uruchamianie..."
                    )
                    Handler(Looper.getMainLooper()).postDelayed({
                        proceedToMainActivity()
                    }, SUCCESS_DELAY)
                }
                else -> {
                    updateStatus(
                        "Błąd pobierania danych",
                        "Ponowienie próby...",
                        ""
                    )
                    scheduleRetry(1) { checkInternetAndLoadData() }
                }
            }
        }
    }

    private suspend fun fetchFromAPIWithRetry(urlString: String, retryCount: Int = 0): String {
        return try {
            fetchFromAPI(urlString)
        } catch (e: Exception) {
            if (retryCount < MAX_RETRIES) {
                val delayMs = RETRY_DELAY_BASE * (retryCount + 1)
                Log.w("API", "Retry ${retryCount + 1}/$MAX_RETRIES for $urlString after ${delayMs}ms", e)
                delay(delayMs)
                fetchFromAPIWithRetry(urlString, retryCount + 1)
            } else {
                throw e
            }
        }
    }

    private suspend fun fetchFromAPI(urlString: String): String {
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(urlString)
                connection = url.openConnection() as HttpURLConnection

                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT
                    readTimeout = READ_TIMEOUT
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "Radio24-Android/${getAppVersion()}")
                    setRequestProperty("Cache-Control", "no-cache")
                    setRequestProperty("Accept-Encoding", "gzip, deflate")

                    // Dodatkowe nagłówki dla kompatybilności
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                        setRequestProperty("Connection", "close")
                    }
                }

                val responseCode = connection.responseCode
                Log.d("API", "Response code for $urlString: $responseCode")

                when (responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val inputStream = if (connection.contentEncoding == "gzip") {
                            java.util.zip.GZIPInputStream(connection.inputStream)
                        } else {
                            connection.inputStream
                        }

                        inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                            val response = reader.readText()
                            if (response.isBlank()) {
                                throw IOException("Empty response from server")
                            }

                            try {
                                Gson().fromJson(response, Any::class.java)
                            } catch (e: JsonSyntaxException) {
                                throw IOException("Invalid JSON response", e)
                            }

                            response
                        }
                    }
                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        throw IOException("API endpoint not found (404)")
                    }
                    HttpURLConnection.HTTP_INTERNAL_ERROR -> {
                        throw IOException("Server error (500)")
                    }
                    else -> {
                        throw IOException("HTTP $responseCode: ${connection.responseMessage}")
                    }
                }
            } catch (e: SocketTimeoutException) {
                throw IOException("Connection timeout", e)
            } catch (e: UnknownHostException) {
                throw IOException("Cannot resolve host", e)
            } catch (e: Exception) {
                throw IOException("Network error: ${e.message}", e)
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun getAppVersion(): String {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    private fun saveToFile(fileName: String, jsonData: String) {
        try {
            val databaseDir = File(filesDir, DATABASE_DIR)
            if (!databaseDir.exists()) {
                databaseDir.mkdirs()
            }

            val file = File(databaseDir, fileName)
            file.writeText(jsonData, Charsets.UTF_8)
            Log.d("File", "Successfully saved $fileName (${jsonData.length} bytes)")
        } catch (e: Exception) {
            Log.e("File", "Error saving $fileName", e)
            throw e
        }
    }

    private fun isInternetAvailable(): Boolean {
        return try {
            val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                networkInfo?.isConnected == true
            }
        } catch (e: Exception) {
            Log.e("Network", "Error checking internet connectivity", e)
            false
        }
    }

    private fun updateStatus(status: String, await: String, finish: String) {
        if (isFinishing || isDestroyed) return

        runOnUiThread {
            try {
                if (status.isNotEmpty()) statusText.text = status
                if (await.isNotEmpty()) statusTextAwait.text = await
                if (finish.isNotEmpty()) statusTextFinish.text = finish
            } catch (e: Exception) {
                Log.e("UI", "Error updating status", e)
            }
        }
    }

    private fun scheduleRetry(attempt: Int, action: () -> Unit) {
        if (isFinishing || isDestroyed) return

        val delay = RETRY_DELAY_BASE * attempt
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing && !isDestroyed) {
                action()
            }
        }, delay)
    }

    private fun proceedToMainActivity() {
        if (isFinishing || isDestroyed) return

        try {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
            overridePendingTransition(0, 0)
        } catch (e: Exception) {
            Log.e("Navigation", "Error starting MainActivity", e)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.cancel()
    }

    private data class APICall(
        val description: String,
        val url: String,
        val fileName: String,
        val parser: (String) -> Any
    )
}
