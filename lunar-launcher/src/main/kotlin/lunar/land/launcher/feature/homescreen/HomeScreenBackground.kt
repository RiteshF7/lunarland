package lunar.land.launcher.feature.homescreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import lunar.land.ui.R

/**
 * Background component using the homescreenbg.jpeg image as wallpaper.
 * The image fills the entire screen and is cropped to maintain aspect ratio.
 */
@Composable
fun HomeScreenBackground(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Image(
            painter = painterResource(id = R.drawable.homescreenbg),
            contentDescription = null, // Decorative background, no description needed
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop // Crop to fill while maintaining aspect ratio
        )
    }
}

