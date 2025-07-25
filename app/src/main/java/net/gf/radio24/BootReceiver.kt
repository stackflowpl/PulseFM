package net.gf.radio24

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                handleBootCompleted(context)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                handlePackageUpdate()
            }
        }
    }

    private fun handleBootCompleted(context: Context) {
        val sharedPreferences = context.getSharedPreferences("PulseFMPreferences", Context.MODE_PRIVATE)
        val lastUpdate = sharedPreferences.getLong("last_update", 0)

        val currentTime = System.currentTimeMillis()
        val cacheAge = currentTime - lastUpdate
        val cacheValidityMs = 24 * 60 * 60 * 1000

        if (cacheAge > cacheValidityMs) {
            Log.d(TAG, "Cache expired, may need update on next app start")
        }
    }

    private fun handlePackageUpdate() {
        Log.d(TAG, "App was updated, clearing old cache if needed")
    }
}
