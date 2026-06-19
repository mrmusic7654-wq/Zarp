package com.example.data

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

object UsageTracker {
    private const val PREFS_NAME = "zarp_usage"
    private const val KEY_DATE = "usage_date"

    val modelLimits = mapOf(
        "Gemini 2.5 Flash" to 1500,
        "Gemini 2.5 Flash-Lite" to 1500,
        "Gemini 3 Flash Preview" to 1500,
        "Gemini 3.1 Flash Lite Preview" to 1000,
        "Gemma 4 26B" to 1500,
        "Gemma 4 31B" to 1500
    )

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun checkDate(context: Context) {
        val today = LocalDate.now().toString()
        val last = prefs(context).getString(KEY_DATE, null)
        if (last != today) {
            prefs(context).edit().clear().putString(KEY_DATE, today).apply()
        }
    }

    fun recordRequest(context: Context, modelName: String) {
        checkDate(context)
        val key = "req_${modelName}"
        val current = prefs(context).getInt(key, 0)
        prefs(context).edit().putInt(key, current + 1).apply()
    }

    fun getCount(context: Context, modelName: String): Int {
        checkDate(context)
        return prefs(context).getInt("req_${modelName}", 0)
    }

    fun getLimit(modelName: String): Int = modelLimits[modelName] ?: 1500
}
