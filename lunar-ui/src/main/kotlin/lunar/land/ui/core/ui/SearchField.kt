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
    
    // Dark background optimized colors - light greys with subtle tints
    val lightGrey = Color(0xFF2a2a2a)
    val midGrey = Color(0xFF1f1f1f)
    val darkGrey = Color(0xFF181818)
    
    // Create gradient background optimized for dark black background
    val gradientColors = listOf(
        lightGrey.copy(alpha = 0.4f),
        midGrey.copy(alpha = 0.35f),
        darkGrey.copy(alpha = 0.3f),
        midGrey.copy(alpha = 0.25f)
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(paddingValues = paddingValues)
            // Soft shadow for dark background
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(cornerRadius),
                ambientColor = Color.Black.copy(alpha = 0.3f),
                spotColor = Color.Black.copy(alpha = 0.4f)
            )
            .clip(RoundedCornerShape(cornerRadius))
            // Theme-aware gradient background
            .background(
                brush = Brush.linearGradient(
                    start = Offset(0f, 0f),
                    end = Offset(0f, 1000f),
                    colors = gradientColors
                )
            )
            // Subtle border for dark background - light grey
            .border(
                width = 1.dp,
                color = Color(0xFF3a3a3a).copy(alpha = 0.4f),
                shape = RoundedCornerShape(cornerRadius)
            )
    ) {
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

