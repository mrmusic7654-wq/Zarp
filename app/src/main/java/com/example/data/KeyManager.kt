package com.example.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object KeyManager {
    private const val PREF_NAME = "zarp_secure_keys"

    // Key constants
    private const val KEY_GEMINI = "api_key_gemini"
    private const val KEY_GITHUB = "api_key_github"
    private const val KEY_TELEGRAM = "api_key_telegram"
    private const val KEY_OPENAI = "api_key_openai"
    private const val KEY_HUGGINGFACE = "api_key_huggingface"
    private const val KEY_HF_SPACE_URL = "hf_space_url"

    private fun getPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context, PREF_NAME, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ── Gemini ──
    fun saveGeminiKey(context: Context, key: String) =
        getPrefs(context).edit().putString(KEY_GEMINI, key).apply()
    fun getGeminiKey(context: Context): String? =
        getPrefs(context).getString(KEY_GEMINI, null)

    // ── GitHub ──
    fun saveGithubKey(context: Context, key: String) =
        getPrefs(context).edit().putString(KEY_GITHUB, key).apply()
    fun getGithubKey(context: Context): String? =
        getPrefs(context).getString(KEY_GITHUB, null)

    // ── Telegram ──
    fun saveTelegramKey(context: Context, key: String) =
        getPrefs(context).edit().putString(KEY_TELEGRAM, key).apply()
    fun getTelegramKey(context: Context): String? =
        getPrefs(context).getString(KEY_TELEGRAM, null)

    // ── OpenAI ──
    fun saveOpenAIKey(context: Context, key: String) =
        getPrefs(context).edit().putString(KEY_OPENAI, key).apply()
    fun getOpenAIKey(context: Context): String? =
        getPrefs(context).getString(KEY_OPENAI, null)

    // ── Hugging Face ──
    fun saveHuggingFaceKey(context: Context, key: String) =
        getPrefs(context).edit().putString(KEY_HUGGINGFACE, key).apply()
    fun getHuggingFaceKey(context: Context): String? =
        getPrefs(context).getString(KEY_HUGGINGFACE, null)

    // ── HF Space URL ──
    fun saveHFSpaceUrl(context: Context, url: String) =
        getPrefs(context).edit().putString(KEY_HF_SPACE_URL, url).apply()
    fun getHFSpaceUrl(context: Context): String =
        getPrefs(context).getString(KEY_HF_SPACE_URL, "") ?: ""

    // ── Custom slots (10) ──
    fun saveCustomKey(context: Context, slot: Int, key: String) =
        getPrefs(context).edit().putString("api_key_custom_$slot", key).apply()
    fun getCustomKey(context: Context, slot: Int): String? =
        getPrefs(context).getString("api_key_custom_$slot", null)

    // ── Legacy ──
    fun saveApiKey(context: Context, key: String) = saveGeminiKey(context, key)
    fun getApiKey(context: Context): String? = getGeminiKey(context)
}
