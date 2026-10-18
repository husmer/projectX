package com.husmer.hanzigate

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONArray
import org.json.JSONObject

/** Progress and settings, saved in SharedPreferences. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("hanzigate", Context.MODE_PRIVATE)
    private val states = HashMap<String, WordState>()

    /** Bumped on every change so Compose screens re-read. */
    val version = mutableIntStateOf(0)

    init {
        val json = JSONObject(prefs.getString(KEY_STATES, "{}")!!)
        for (id in json.keys()) {
            val a = json.getJSONArray(id)
            states[id] = WordState(
                known = a.getInt(0) == 1,
                sorted = a.getInt(1) == 1,
                box = a.getInt(2),
                due = a.getLong(3),
            )
        }
    }

    fun state(id: String): WordState = states[id] ?: WordState()

    fun update(id: String, s: WordState) = updateAll(mapOf(id to s))

    /** Several words in one save (marking a whole category). */
    fun updateAll(changes: Map<String, WordState>) {
        states.putAll(changes)
        val json = JSONObject()
        for ((k, v) in states) {
            json.put(k, JSONArray().put(if (v.known) 1 else 0).put(if (v.sorted) 1 else 0).put(v.box).put(v.due))
        }
        prefs.edit().putString(KEY_STATES, json.toString()).apply()
        version.intValue++
    }

    var needed: Int
        get() = prefs.getInt("needed", 3)
        set(v) { prefs.edit().putInt("needed", v).apply(); version.intValue++ }

    var unlockMinutes: Int
        get() = prefs.getInt("unlockMinutes", 15)
        set(v) { prefs.edit().putInt("unlockMinutes", v).apply(); version.intValue++ }

    var unlockedUntil: Long
        get() = prefs.getLong("unlockedUntil", 0L)
        set(v) { prefs.edit().putLong("unlockedUntil", v).apply(); version.intValue++ }

    private companion object {
        const val KEY_STATES = "states"
    }
}
