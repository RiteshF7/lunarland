package lunar.land.launcher.feature.appdrawer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import lunar.land.ui.manager.model.AppInfo

/**
 * Pattern for app drawer rows: [2, 3, 2, 1, 2, 3, 2, 1, ...]
 * This pattern repeats every 4 rows.
 */
private val ROW_PATTERN = listOf(2, 3, 2, 1)

/**
 * Divides apps into rows following the pattern [2, 3, 2, 1, ...]
 * Apps with shorter names are placed in rows with more icons (less space per icon).
 * 
 * Algorithm:
 * 1. Sort apps by name length (shortest first)
 * 2. Distribute apps to rows following the pattern with name length constraints:
 *    - 3-icon rows: total name length < 18 characters
 *    - 2-icon rows: total name length < 25 characters
 *    - 1-icon rows: no limit
 * 3. Smart distribution to avoid UI overflow
 */
fun List<AppInfo>.divideIntoPatternedRows(): List<List<AppInfo>> {
    if (isEmpty()) return emptyList()
    
    // Pre-compute name lengths to avoid repeated property access
    // Use Pair for efficient sorting without creating intermediate objects
    val appsWithLength = map { it to it.app.displayName.length }
    
    // Sort apps by name length (shortest first)
    // This ensures shorter names go to rows with more icons
    // Use stable sort for consistent ordering
    val sortedApps = appsWithLength
        .sortedBy { it.second }
        .map { it.first }
    
    val rows = mutableListOf<List<AppInfo>>()
    val remainingApps = sortedApps.toMutableList()
    var patternIndex = 0
    
    while (remainingApps.isNotEmpty()) {
        // Get the number of icons for this row from the pattern
        val iconsInRow = ROW_PATTERN[patternIndex % ROW_PATTERN.size]
        
        // Get name length limit based on row type
        val maxTotalLength = when (iconsInRow) {
            3 -> 18  // 3-icon row: max 18 chars total
            2 -> 25  // 2-icon row: max 25 chars total
            1 -> Int.MAX_VALUE  // 1-icon row: no limit
            else -> Int.MAX_VALUE
        }
        
        // Select apps for this row that fit within the length constraint
        val rowApps = mutableListOf<AppInfo>()
        var currentTotalLength = 0
        var appIndex = 0
        
        while (appIndex < remainingApps.size && rowApps.size < iconsInRow) {
            val app = remainingApps[appIndex]
            val appNameLength = app.app.displayName.length
            
            // Check if adding this app would exceed the limit
            if (currentTotalLength + appNameLength <= maxTotalLength) {
                rowApps.add(app)
                currentTotalLength += appNameLength
                remainingApps.removeAt(appIndex)
                // Don't increment appIndex since we removed an item
            } else {
                // If we can't fit this app:
                // - For 1-icon rows: take it anyway (no limit)
                // - For other rows: skip it and continue to next row
                if (iconsInRow == 1) {
                    rowApps.add(app)
                    remainingApps.removeAt(appIndex)
                } else {
                    appIndex++ // Skip this app
                }
                break
            }
        }
        
        // If we still need more apps and have remaining apps, fill the row
        // But only if we haven't exceeded the limit
        while (rowApps.size < iconsInRow && remainingApps.isNotEmpty()) {
            val app = remainingApps[0]
            val appNameLength = app.app.displayName.length
            
            // Only add if it fits within the limit (or if it's a 1-icon row)
            if (iconsInRow == 1 || currentTotalLength + appNameLength <= maxTotalLength) {
                rowApps.add(remainingApps.removeAt(0))
                currentTotalLength += appNameLength
            } else {
                // Can't fit more apps in this row, move to next row
                break
            }
        }
        
        if (rowApps.isNotEmpty()) {
            rows.add(rowApps) // Use rowApps directly, no need to copy
        }
        
        patternIndex++
    }
    
    return rows
}

/**
 * Custom layout component that displays apps in a patterned grid:
 * Row 1: 2 icons
 * Row 2: 3 icons
 * Row 3: 2 icons
 * Row 4: 1 icon
 * Pattern repeats...
 * 
 * Apps with shorter names are automatically placed in rows with more icons.
 * Returns the divided rows for use in LazyColumn.items() for optimal performance.
 */
fun List<AppInfo>.getPatternedRows(): List<List<AppInfo>> {
    return divideIntoPatternedRows()
}

/**
 * Composable for a single row of apps in the patterned grid.
 * Optimized with stable callback references and composition boundaries.
 */
@Composable
fun PatternedAppRow(
    rowApps: List<AppInfo>,
    onAppClick: (AppInfo) -> Unit,
    horizontalSpacing: androidx.compose.ui.unit.Dp = 14.dp
) {
    // Stable callback reference prevents unnecessary recompositions
    // This is critical for performance in lazy lists
    val stableOnAppClick = androidx.compose.runtime.rememberUpdatedState(onAppClick)
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing)
    ) {
        // Calculate width for each item based on number of items in row
        // Memoize to avoid recalculation
        val itemCount = remember(rowApps.size) { rowApps.size }
        val itemWeight = remember(itemCount) { 1f / itemCount }
        
        rowApps.forEach { appInfo ->
            // Use stable unique key (package name) for efficient item tracking
            // This allows Compose to intelligently reuse compositions
            key(appInfo.app.packageName) {
                // Memoize app item data - only recompute when app data changes
                // Using stable keys: packageName, icon reference, and color
                val appItemData = remember(
                    appInfo.app.packageName,
                    appInfo.icon,
                    appInfo.color,
                    appInfo.app.displayName
                ) {
                    appInfo.toAppItemData()
                }
                
                // Isolate heavy composable in its own composition scope
                // This prevents recomposition cascades
                AppItem(
                    appData = appItemData,
                    onClick = { stableOnAppClick.value(appInfo) },
                    modifier = Modifier.weight(itemWeight)
                )
            }
        }
    }
}

