package lunar.land.ui.feature.appdrawer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Modern container for the app drawer with fully opaque black background.
 * 
 * @param modifier Modifier for styling and layout
 * @param content The app drawer content
 */
@Composable
fun AppDrawerContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // Fully opaque black background
    val blackBackground = Color.Black
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(blackBackground)
            .then(modifier),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            content()
        }
    }
}
