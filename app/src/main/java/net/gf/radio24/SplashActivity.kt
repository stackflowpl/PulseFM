package net.gf.radio24

import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.activity.ComponentActivity

class SplashActivity : ComponentActivity() {

    private lateinit var statusText: TextView
    private lateinit var statusTextAwait: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        statusText = findViewById(R.id.statusText)
        statusTextAwait = findViewById(R.id.statusTextAwiat)

        checkInternetConnection()
    }

    private fun checkInternetConnection() {
        Handler(Looper.getMainLooper()).postDelayed({
            if (isInternetAvailable()) {
                Handler(Looper.getMainLooper()).postDelayed({
                    proceedToNextActivity()
                    statusTextAwait.text = "Dostęp do internetu."
                }, 1000)
            } else {
                Handler(Looper.getMainLooper()).postDelayed({
                    checkInternetConnection()
                    statusTextAwait.text = "Brak dostępu do internetu."
                }, 1000)
            }
        }, 2000)
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun proceedToNextActivity() {
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
            overridePendingTransition(0, 0)
        }, 1000)
    }
}