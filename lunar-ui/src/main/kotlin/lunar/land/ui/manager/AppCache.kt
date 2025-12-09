package lunar.land.ui.manager

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lunar.land.ui.manager.model.AppInfo

/**
 * Cache manager for app list to improve loading performance.
 * Stores app metadata (without icons) in SharedPreferences for fast retrieval.
 * Uses a simple string-based serialization approach.
 */
class AppCache(private val context: Context) {
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("app_drawer_cache", Context.MODE_PRIVATE)
    
    private val cacheKeyPrefix = "cached_app_"
    private val cacheCountKey = "cache_count"
    private val cacheAppCountKey = "cache_app_count" // Alias for cache_count, kept for clarity
    private val cachePackageListKey = "cache_package_list"
    private val cacheTimestampKey = "cache_timestamp"
    private val cacheVersionKey = "cache_version"
    
    // Cache version for invalidating old caches
    private val currentCacheVersion = 1
    
    /**
     * Cached app metadata (without icons, as they can't be serialized easily).
     */
    data class CachedAppMetadata(
        val packageName: String,
        val displayName: String,
        val name: String,
        val isSystem: Boolean,
        val color: Int
    ) {
        companion object {
            // Separator for metadata fields
            private const val SEPARATOR = "|||"
            
            fun serialize(metadata: CachedAppMetadata): String {
                return listOf(
                    metadata.packageName,
                    metadata.displayName,
                    metadata.name,
                    metadata.isSystem.toString(),
                    metadata.color.toString()
                ).joinToString(SEPARATOR)
            }
            
            fun deserialize(data: String): CachedAppMetadata? {
                return try {
                    val parts = data.split(SEPARATOR)
                    if (parts.size == 5) {
                        CachedAppMetadata(
                            packageName = parts[0],
                            displayName = parts[1],
                            name = parts[2],
                            isSystem = parts[3].toBoolean(),
                            color = parts[4].toInt()
                        )
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
    
    /**
     * Saves app list metadata to cache (excluding icons).
     */
    suspend fun saveApps(apps: List<AppInfo>) = withContext(Dispatchers.IO) {
        val editor = sharedPreferences.edit()
        
        // Clear old cache entries
        val oldCount = sharedPreferences.getInt(cacheCountKey, 0)
        for (i in 0 until oldCount) {
            editor.remove("$cacheKeyPrefix$i")
        }
        
        // Save new cache entries
        val metadata = apps.map { appInfo ->
            CachedAppMetadata(
                packageName = appInfo.app.packageName,
                displayName = appInfo.app.displayName,
                name = appInfo.app.name,
                isSystem = appInfo.app.isSystem,
                color = appInfo.color
            )
        }
        
        metadata.forEachIndexed { index, meta ->
            editor.putString("$cacheKeyPrefix$index", CachedAppMetadata.serialize(meta))
        }
        
        // Save package list as comma-separated string for quick comparison
        val packageList = apps.map { it.app.packageName }.joinToString(",")
        
        editor.putInt(cacheCountKey, metadata.size)
            .putInt(cacheAppCountKey, metadata.size)
            .putString(cachePackageListKey, packageList)
            .putLong(cacheTimestampKey, System.currentTimeMillis())
            .putInt(cacheVersionKey, currentCacheVersion)
            .apply()
    }
    
    /**
     * Loads cached app metadata synchronously (for instant display).
     * Returns null if cache is invalid or doesn't exist.
     * SharedPreferences operations are fast and can be called from main thread.
     */
    fun loadCachedAppsSync(): List<CachedAppMetadata>? {
        return try {
            // Check cache version
            val cachedVersion = sharedPreferences.getInt(cacheVersionKey, 0)
            if (cachedVersion != currentCacheVersion) {
                return null
            }
            
            val count = sharedPreferences.getInt(cacheCountKey, 0)
            if (count == 0) {
                return null
            }
            
            val metadata = mutableListOf<CachedAppMetadata>()
            for (i in 0 until count) {
                val data = sharedPreferences.getString("$cacheKeyPrefix$i", null)
                if (data != null) {
                    CachedAppMetadata.deserialize(data)?.let { metadata.add(it) }
                }
            }
            
            if (metadata.isEmpty()) {
                null
            } else {
                metadata
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Loads cached app metadata (async version for background operations).
     * Returns null if cache is invalid or doesn't exist.
     */
    suspend fun loadCachedApps(): List<CachedAppMetadata>? = withContext(Dispatchers.IO) {
        loadCachedAppsSync()
    }
    
    /**
     * Clears the cache.
     */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        val editor = sharedPreferences.edit()
        val oldCount = sharedPreferences.getInt(cacheCountKey, 0)
        for (i in 0 until oldCount) {
            editor.remove("$cacheKeyPrefix$i")
        }
        editor.remove(cacheCountKey)
            .remove(cacheAppCountKey)
            .remove(cachePackageListKey)
            .remove(cacheTimestampKey)
            .remove(cacheVersionKey)
            .apply()
    }
    
    /**
     * Gets the cache timestamp.
     */
    fun getCacheTimestamp(): Long {
        return sharedPreferences.getLong(cacheTimestampKey, 0L)
    }
    
    /**
     * Gets the cached app count.
     * Returns 0 if cache doesn't exist or is invalid.
     */
    fun getCachedAppCount(): Int {
        return sharedPreferences.getInt(cacheAppCountKey, 0)
    }
    
    /**
     * Gets the cached package list as a Set of package names.
     * Returns empty set if cache doesn't exist or is invalid.
     */
    fun getCachedPackageList(): Set<String> {
        val packageListString = sharedPreferences.getString(cachePackageListKey, null)
        return if (packageListString.isNullOrBlank()) {
            emptySet()
        } else {
            packageListString.split(",").filter { it.isNotBlank() }.toSet()
        }
    }
}

