package lunar.land.ui.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import lunar.land.ui.R

@Composable
fun SearchField(
    modifier: Modifier = Modifier,
    placeholder: String,
    query: String,
    onQueryChange: (String) -> Unit,
    paddingValues: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
) {
    val cornerRadius = 24.dp
    
    // Match AppItem theme colors for consistency
    val almostWhite = Color(0xFF4a4a4a)    // Almost white grey for top highlight
    val veryLightGrey = Color(0xFF3a3a3a)   // Very light grey
    val lightGrey = Color(0xFF2a2a2a)       // Light grey
    val midGrey = Color(0xFF1f1f1f)         // Medium grey
    val darkGrey = Color(0xFF181818)       // Dark grey
    val almostBlack = Color(0xFF0a0a0a)    // Almost black for bottom
    
    // Create dramatic gradient matching AppItem theme - bright top, dark bottom
    // Using similar alpha values as AppItem for consistency
    val topHighlight = Color(
        red = (almostWhite.red * 0.8f).coerceIn(0f, 1f),
        green = (almostWhite.green * 0.8f).coerceIn(0f, 1f),
        blue = (almostWhite.blue * 0.8f).coerceIn(0f, 1f),
        alpha = 0.75f
    )
    val highlight = Color(
        red = (veryLightGrey.red * 0.75f).coerceIn(0f, 1f),
        green = (veryLightGrey.green * 0.75f).coerceIn(0f, 1f),
        blue = (veryLightGrey.blue * 0.75f).coerceIn(0f, 1f),
        alpha = 0.55f
    )
    val lightColor = Color(
        red = (lightGrey.red * 0.7f).coerceIn(0f, 1f),
        green = (lightGrey.green * 0.7f).coerceIn(0f, 1f),
        blue = (lightGrey.blue * 0.7f).coerceIn(0f, 1f),
        alpha = 0.4f
    )
    val midColor = Color(
        red = (midGrey.red * 0.75f).coerceIn(0f, 1f),
        green = (midGrey.green * 0.75f).coerceIn(0f, 1f),
        blue = (midGrey.blue * 0.75f).coerceIn(0f, 1f),
        alpha = 0.3f
    )
    val darkColor = Color(
        red = (darkGrey.red * 0.8f).coerceIn(0f, 1f),
        green = (darkGrey.green * 0.8f).coerceIn(0f, 1f),
        blue = (darkGrey.blue * 0.8f).coerceIn(0f, 1f),
        alpha = 0.2f
    )
    val bottomColor = Color(
        red = (almostBlack.red * 0.85f).coerceIn(0f, 1f),
        green = (almostBlack.green * 0.85f).coerceIn(0f, 1f),
        blue = (almostBlack.blue * 0.85f).coerceIn(0f, 1f),
        alpha = 0.15f
    )
    
    // Dramatic gradient matching AppItem - very bright top, very dark bottom
    val gradientColors = listOf(
        topHighlight,      // Almost white top - maximum brightness
        highlight,         // Very light highlight
        lightColor,         // Light
        midColor,          // Medium
        darkColor,         // Dark
        bottomColor        // Almost black bottom - maximum darkness
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(paddingValues = paddingValues)
            // Soft shadow matching AppItem style
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(cornerRadius),
                ambientColor = Color.Black.copy(alpha = 0.3f),
                spotColor = Color.Black.copy(alpha = 0.4f)
            )
            .clip(RoundedCornerShape(cornerRadius))
            // Dramatic upward 3D gradient matching AppItem - very bright top, very dark bottom
            .background(
                brush = Brush.linearGradient(
                    start = Offset(0f, 0f),      // Top - brightest (almost white)
                    end = Offset(0f, 1200f),    // Bottom - darkest (almost black)
                    colors = gradientColors
                )
            )
            // Top border highlight matching AppItem - simulates light reflection
            .border(
                width = 1.dp,
                color = Color(0xFF4a4a4a).copy(alpha = 0.6f),
                shape = RoundedCornerShape(cornerRadius)
            )
    ) {
        // Top highlight overlay matching AppItem theme
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(cornerRadius))
                .background(
                    brush = Brush.linearGradient(
                        start = Offset(0f, 0f),
                        end = Offset(0f, 40f),  // Only top portion
                        colors = listOf(
                            Color.White.copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    )
                )
        )
        
        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            value = query,
            onValueChange = { onQueryChange(it) },
            placeholder = {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFaaaaaa) // Light grey for dark background
                )
            },
            shape = RoundedCornerShape(cornerRadius),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                autoCorrect = false,
                imeAction = ImeAction.Search
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color(0xFFe0e0e0), // Light text for dark background
                unfocusedTextColor = Color(0xFFe0e0e0),
                focusedPlaceholderColor = Color(0xFFaaaaaa),
                unfocusedPlaceholderColor = Color(0xFFaaaaaa)
            ),
            textStyle = MaterialTheme.typography.bodyMedium,
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_search),
                    contentDescription = stringResource(id = R.string.search),
                    tint = Color(0xFFaaaaaa) // Light grey for dark background
                )
            },
            trailingIcon = {
                AnimatedVisibility(visible = query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(shape = CircleShape)
                            .clickable { onQueryChange("") }
                            .padding(all = 4.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_close),
                            contentDescription = stringResource(id = R.string.clear),
                            tint = Color(0xFFaaaaaa) // Light grey for dark background
                        )
                    }
                }
            }
        )
    }
}

