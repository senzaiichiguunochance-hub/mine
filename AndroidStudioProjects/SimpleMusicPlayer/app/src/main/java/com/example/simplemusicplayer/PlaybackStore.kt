package com.example.simplemusicplayer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class AudioFile(val name: String, val uri: String)

class PlaybackStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var speed: Float
        get() = prefs.getFloat(KEY_SPEED, 1f)
        set(value) = prefs.edit().putFloat(KEY_SPEED, value).apply()

    var lastUri: String?
        get() = prefs.getString(KEY_LAST_URI, null)
        set(value) = prefs.edit().putString(KEY_LAST_URI, value).apply()

    fun loadFiles(): List<AudioFile> {
        val json = prefs.getString(KEY_FILES, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            List(array.length()) { i ->
                val obj = array.getJSONObject(i)
                AudioFile(obj.getString("name"), obj.getString("uri"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveFiles(files: List<AudioFile>) {
        val array = JSONArray()
        files.forEach { array.put(JSONObject().put("name", it.name).put("uri", it.uri)) }
        prefs.edit().putString(KEY_FILES, array.toString()).apply()
    }

    fun getPosition(uri: String): Long =
        loadPositions().firstOrNull { it.first == uri }?.second ?: 0L

    fun savePosition(uri: String, positionMs: Long, durationMs: Long) {
        if (positionMs < MIN_POSITION_MS) return

        val positions = loadPositions().apply { removeAll { it.first == uri } }
        val finished = durationMs > 0 && positionMs >= durationMs - END_MARGIN_MS
        if (!finished) positions.add(0, uri to positionMs)
        writePositions(positions.take(MAX_ENTRIES))
    }

    fun removePosition(uri: String) {
        val positions = loadPositions()
        if (positions.removeAll { it.first == uri }) writePositions(positions)
    }

    private fun loadPositions(): MutableList<Pair<String, Long>> {
        val json = prefs.getString(KEY_POSITIONS, null) ?: return mutableListOf()
        return try {
            val array = JSONArray(json)
            MutableList(array.length()) { i ->
                val obj = array.getJSONObject(i)
                obj.getString("uri") to obj.getLong("pos")
            }
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun writePositions(positions: List<Pair<String, Long>>) {
        val array = JSONArray()
        positions.forEach { array.put(JSONObject().put("uri", it.first).put("pos", it.second)) }
        prefs.edit().putString(KEY_POSITIONS, array.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "player"
        const val KEY_SPEED = "speed"
        const val KEY_LAST_URI = "last_uri"
        const val KEY_FILES = "files"
        const val KEY_POSITIONS = "positions"

        const val MAX_ENTRIES = 10
        const val MIN_POSITION_MS = 3_000L
        const val END_MARGIN_MS = 3_000L
    }
}
