package lunar.land.ui.feature.appdrawer

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import lunar.land.ui.feature.favorites.ui.StaggeredFlowRow

/**
 * Screen demonstrating the App Drawer with modern 3D effects and glow.
 * Uses specific colors matching the design.
 */
@Composable
fun AppDrawerScreen() {
    // Define apps with their specific colors from the screenshot
    val appItems = listOf(
        AppItemData(
            name = "Camera",
            icon = Icons.Filled.Camera,
            backgroundColor = Color(0xFF4A2C4A), // Purple-magenta
            textColor = Color(0xFFD870D8),
            glowColor = Color(0x66D870D8) // rgba(216, 112, 216, 0.4)
        ),
        AppItemData(
            name = "Chrome",
            icon = Icons.Filled.Public,
            backgroundColor = Color(0xFF5A2C2C), // Reddish-brown
            textColor = Color(0xFFE07070),
            glowColor = Color(0x66E07070)
        ),
        AppItemData(
            name = "Settings",
            icon = Icons.Filled.Settings,
            backgroundColor = Color(0xFF2C5A2C), // Green
            textColor = Color(0xFF70E070),
            glowColor = Color(0x6670E070)
        ),
        AppItemData(
            name = "Drive",
            icon = Icons.Filled.Cloud,
            backgroundColor = Color(0xFF2C2C5A), // Dark blue
            textColor = Color(0xFF7070E0),
            glowColor = Color(0x667070E0)
        ),
        AppItemData(
            name = "Maps",
            icon = Icons.Filled.Map,
            backgroundColor = Color(0xFF4A5A2C), // Olive-green
            textColor = Color(0xFFD8E070),
            glowColor = Color(0x66D8E070)
        ),
        AppItemData(
            name = "Messages",
            icon = Icons.Filled.Email,
            backgroundColor = Color(0xFF2C5A4A), // Teal-blue
            textColor = Color(0xFF70E0D8),
            glowColor = Color(0x6670E0D8)
        ),
        AppItemData(
            name = "Photos",
            icon = Icons.Filled.Image,
            backgroundColor = Color(0xFF5A4A2C), // Golden-brown
            textColor = Color(0xFFE0D870),
            glowColor = Color(0x66E0D870)
        ),
        AppItemData(
            name = "YT Music",
            icon = Icons.Filled.MusicNote,
            backgroundColor = Color(0xFF5A2C5A), // Purple
            textColor = Color(0xFFE070E0),
            glowColor = Color(0x66E070E0),
            isWide = true
        )
    )
    
    AppDrawerContainer {
        StaggeredFlowRow(
            modifier = Modifier.fillMaxWidth(),
            mainAxisSpacing = 12.dp,
            crossAxisSpacing = 12.dp
        ) {
            appItems.forEach { appItem ->
                AppItem(appItem)
            }
        }
    }
}
