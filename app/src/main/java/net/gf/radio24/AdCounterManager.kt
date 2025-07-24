package net.gf.radio24

import android.content.Context
import org.json.JSONObject
import java.io.File

class AdCounterManager(private val context: Context) {

    private val databaseDir = File(context.filesDir, "database")
    private val file = File(databaseDir, "ads.json")

    init {
        if (!databaseDir.exists()) databaseDir.mkdirs()
        if (!file.exists()) {
            saveCount(0)
        }
    }

    fun incrementAdCount() {
        val current = getAdCount()
        saveCount(current + 1)
    }

    fun getAdCount(): Int {
        return try {
            val content = file.readText()
            val json = JSONObject(content)
            json.optInt("adsWatched", 0)
        } catch (e: Exception) {
            0
        }
    }

    private fun saveCount(count: Int) {
        val json = JSONObject()
        json.put("adsWatched", count)
        file.writeText(json.toString())
    }
}
