package com.termux.droidrun.wrapper.utils

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * ApiKeyManager - Manages LLM API keys securely
 * 
 * Stores API keys in Android DataStore (encrypted at rest on Android 6.0+)
 * and provides methods to retrieve them for Python environment variables.
 */
class ApiKeyManager(private val context: Context) {
    companion object {
        private const val TAG = "ApiKeyManager"
        private val Context.apiKeyStore: DataStore<Preferences> by preferencesDataStore(name = "api_keys")
        
        // Environment variable names used by DroidRun
        const val GOOGLE_API_KEY = "GOOGLE_API_KEY"
        const val OPENAI_API_KEY = "OPENAI_API_KEY"
        const val ANTHROPIC_API_KEY = "ANTHROPIC_API_KEY"
        const val DEEPSEEK_API_KEY = "DEEPSEEK_API_KEY"
    }
    
    private val dataStore = context.apiKeyStore
    
    /**
     * Save an API key
     */
    suspend fun saveApiKey(provider: String, apiKey: String) {
        try {
            dataStore.edit { preferences ->
                preferences[stringPreferencesKey(provider)] = apiKey
            }
            Log.d(TAG, "API key saved for provider: $provider")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving API key: ${e.message}", e)
        }
    }
    
    /**
     * Get an API key
     */
    suspend fun getApiKey(provider: String): String? {
        return try {
            dataStore.data.map { preferences ->
                preferences[stringPreferencesKey(provider)]?.toString()
            }.first()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting API key: ${e.message}", e)
            null
        }
    }
    
    /**
     * Delete an API key
     */
    suspend fun deleteApiKey(provider: String) {
        try {
            dataStore.edit { preferences ->
                preferences.remove(stringPreferencesKey(provider))
            }
            Log.d(TAG, "API key deleted for provider: $provider")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting API key: ${e.message}", e)
        }
    }
    
    /**
     * Get all API keys as a map for environment variables
     */
    suspend fun getAllApiKeys(): Map<String, String> {
        return try {
            val preferences = dataStore.data.first()
            val keys = mutableMapOf<String, String>()
            
            // Check for each known provider
            val providers = listOf(GOOGLE_API_KEY, OPENAI_API_KEY, ANTHROPIC_API_KEY, DEEPSEEK_API_KEY)
            providers.forEach { provider ->
                preferences[stringPreferencesKey(provider)]?.toString()?.let { key ->
                    if (key.isNotEmpty()) {
                        keys[provider] = key
                    }
                }
            }
            
            keys
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all API keys: ${e.message}", e)
            emptyMap()
        }
    }
    
    /**
     * Check if an API key exists
     */
    suspend fun hasApiKey(provider: String): Boolean {
        val key = getApiKey(provider)
        return !key.isNullOrEmpty()
    }
}

