package com.termux.droidrun.portal.util

import com.termux.droidrun.portal.model.FormattedDeviceState
import android.util.Log
import java.util.LinkedHashMap
import kotlin.jvm.Synchronized

/**
 * LRU (Least Recently Used) cache manager for FormattedDeviceState
 * Uses LinkedHashMap with access order to implement LRU eviction
 */
class StateCacheManager(private val maxSize: Int = 5) {
    companion object {
        private const val TAG = "StateCacheManager"
        private const val DEFAULT_CACHE_KEY = "current_state"
    }
    
    // LinkedHashMap with access order enabled for LRU behavior
    private val cache: LinkedHashMap<String, FormattedDeviceState> = 
        object : LinkedHashMap<String, FormattedDeviceState>(maxSize + 1, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FormattedDeviceState>?): Boolean {
                return size > maxSize
            }
        }
    
    /**
     * Get cached state by key
     * 
     * @param key The cache key (defaults to "current_state")
     * @return Cached FormattedDeviceState or null if not found
     */
    @Synchronized
    fun get(key: String = DEFAULT_CACHE_KEY): FormattedDeviceState? {
        return try {
            cache[key]
        } catch (e: Exception) {
            Log.e(TAG, "Error getting from cache: ${e.message}", e)
            null
        }
    }
    
    /**
     * Put state into cache
     * If cache is full, oldest entry will be evicted (LRU)
     * 
     * @param key The cache key (defaults to "current_state")
     * @param value The FormattedDeviceState to cache
     */
    @Synchronized
    fun put(key: String = DEFAULT_CACHE_KEY, value: FormattedDeviceState) {
        try {
            cache[key] = value
        } catch (e: Exception) {
            Log.e(TAG, "Error putting into cache: ${e.message}", e)
        }
    }
    
    /**
     * Clear all cached entries
     */
    @Synchronized
    fun clear() {
        try {
            cache.clear()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache: ${e.message}", e)
        }
    }
    
    /**
     * Get current cache size
     * 
     * @return Number of entries in cache
     */
    @Synchronized
    fun getCacheSize(): Int {
        return cache.size
    }
    
    /**
     * Get the default cache key
     */
    fun getDefaultKey(): String = DEFAULT_CACHE_KEY
    
    /**
     * Check if cache contains a key
     * 
     * @param key The cache key to check
     * @return true if key exists in cache
     */
    @Synchronized
    fun containsKey(key: String = DEFAULT_CACHE_KEY): Boolean {
        return cache.containsKey(key)
    }
}

