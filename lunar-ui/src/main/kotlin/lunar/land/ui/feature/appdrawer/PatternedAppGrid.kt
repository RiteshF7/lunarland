package lunar.land.ui.feature.appdrawer

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
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
 * 2. Distribute apps to rows following the pattern
 * 3. For rows with more icons (3 icons), prioritize shorter names
 * 4. For rows with fewer icons (1-2 icons), longer names are acceptable
 */
fun List<AppInfo>.divideIntoPatternedRows(): List<List<AppInfo>> {
    if (isEmpty()) return emptyList()
    
    // Sort apps by name length (shortest first)
    // This ensures shorter names go to rows with more icons
    val sortedApps = sortedBy { it.app.displayName.length }
    
    val rows = mutableListOf<List<AppInfo>>()
    var appIndex = 0
    var patternIndex = 0
    
    while (appIndex < sortedApps.size) {
        // Get the number of icons for this row from the pattern
        val iconsInRow = ROW_PATTERN[patternIndex % ROW_PATTERN.size]
        
        // Take apps for this row
        val rowApps = sortedApps.subList(
            appIndex,
            (appIndex + iconsInRow).coerceAtMost(sortedApps.size)
        )
        
        if (rowApps.isNotEmpty()) {
            rows.add(rowApps.toList())
        }
        
        appIndex += iconsInRow
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
 */
@Composable
fun PatternedAppGrid(
    apps: List<AppInfo>,
    onAppClick: (AppInfo) -> Unit,
    modifier: Modifier = Modifier,
    horizontalSpacing: androidx.compose.ui.unit.Dp = 14.dp,
    verticalSpacing: androidx.compose.ui.unit.Dp = 14.dp
) {
    val rows = remember(apps) { apps.divideIntoPatternedRows() }
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing)
    ) {
        rows.forEach { rowApps ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(horizontalSpacing)
            ) {
                // Calculate width for each item based on number of items in row
                val itemCount = rowApps.size
                val itemWeight = 1f / itemCount
                
                rowApps.forEach { appInfo ->
                    NeumorphicAppItem(
                        appData = appInfo.toAppItemData(),
                        onClick = { onAppClick(appInfo) },
                        modifier = Modifier.weight(itemWeight)
                    )
                }
            }
        }
    }
}

