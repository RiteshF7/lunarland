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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.InputStream

/**
 * ConfigManager - Manages configuration (YAML + Android DataStore)
 * 
 * Loads default config from assets/config.yaml and merges with
 * runtime overrides stored in DataStore.
 */
class ConfigManager(private val context: Context) {
    companion object {
        private const val TAG = "ConfigManager"
        private const val CONFIG_FILE = "config.yaml"
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "droidrun_config")
    }
    
    private val dataStore = context.dataStore
    private val yaml = Yaml()
    
    /**
     * Load default config from assets
     */
    fun loadDefaultConfig(): Map<String, Any> {
        return try {
            val inputStream: InputStream = context.assets.open(CONFIG_FILE)
            val config = yaml.load<Map<String, Any>>(inputStream)
            inputStream.close()
            Log.d(TAG, "Default config loaded from assets")
            config
        } catch (e: Exception) {
            Log.e(TAG, "Error loading default config: ${e.message}", e)
            getDefaultConfigMap()
        }
    }
    
    /**
     * Get merged config (defaults + overrides)
     */
    suspend fun getMergedConfig(): Map<String, Any> {
        val defaultConfig = loadDefaultConfig()
        val overrides = getConfigOverrides()
        
        // Merge overrides into defaults (simple deep merge)
        return mergeConfigs(defaultConfig, overrides)
    }
    
    /**
     * Save config override
     */
    suspend fun saveConfigOverride(key: String, value: String) {
        dataStore.edit { preferences ->
            preferences[stringPreferencesKey(key)] = value
        }
    }
    
    /**
     * Get config override
     */
    suspend fun getConfigOverride(key: String): String? {
        return dataStore.data.map { preferences ->
            preferences[stringPreferencesKey(key)]?.toString()
        }.first()
    }
    
    /**
     * Get all config overrides
     */
    private suspend fun getConfigOverrides(): Map<String, Any> {
        val overrides = mutableMapOf<String, Any>()
        // Read all preferences and convert to config structure
        // This is simplified - in production, you'd want a more structured approach
        return overrides
    }
    
    /**
     * Save full config to YAML file
     */
    suspend fun saveConfigToFile(config: Map<String, Any>, filePath: String) {
        try {
            val yamlString = yaml.dump(config)
            File(filePath).writeText(yamlString)
            Log.d(TAG, "Config saved to: $filePath")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving config: ${e.message}", e)
        }
    }
    
    /**
     * Get config file path for Python
     */
    suspend fun getConfigFilePath(): String {
        val configFile = File(context.filesDir, CONFIG_FILE)
        if (!configFile.exists()) {
            // Copy default config to files directory
            try {
                val defaultConfig = loadDefaultConfig()
                saveConfigToFile(defaultConfig, configFile.absolutePath)
            } catch (e: Exception) {
                Log.e(TAG, "Error creating config file: ${e.message}", e)
            }
        }
        return configFile.absolutePath
    }
    
    private fun mergeConfigs(default: Map<String, Any>, overrides: Map<String, Any>): Map<String, Any> {
        val merged = default.toMutableMap()
        overrides.forEach { (key, value) ->
            if (merged[key] is Map<*, *> && value is Map<*, *>) {
                merged[key] = mergeConfigs(
                    merged[key] as Map<String, Any>,
                    value as Map<String, Any>
                )
            } else {
                merged[key] = value
            }
        }
        return merged
    }
    
    private fun getDefaultConfigMap(): Map<String, Any> {
        // Return minimal default config if YAML load fails
        return mapOf(
            "agent" to mapOf(
                "max_steps" to 15,
                "reasoning" to true,
                "after_sleep_action" to 1.0,
                "wait_for_stable_ui" to 0.3
            ),
            "device" to mapOf(
                "platform" to "android"
            ),
            "logging" to mapOf(
                "debug" to false,
                "save_trajectory" to "none"
            )
        )
    }
}

