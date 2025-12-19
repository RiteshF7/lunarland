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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lunar.land.ui.R
import lunar.land.ui.core.theme.LunarTheme

@Composable
fun SearchField(
    modifier: Modifier = Modifier,
    placeholder: String,
    query: String,
    onQueryChange: (String) -> Unit,
    paddingValues: PaddingValues = PaddingValues(horizontal = LunarTheme.Spacing.XXLarge, vertical = LunarTheme.Spacing.Medium)
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(paddingValues = paddingValues)
            .clip(RoundedCornerShape(12.dp)) // Rounded corners
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        LunarTheme.InactiveBackgroundColor.copy(alpha = 0.8f),
                        LunarTheme.InactiveBackgroundColor.copy(alpha = 0.6f)
                    )
                )
            )
            .border(
                width = LunarTheme.BorderWidth,
                color = LunarTheme.BorderColor,
                shape = RoundedCornerShape(12.dp) // Rounded corners
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
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = LunarTheme.ManropeFontFamily,
                        fontSize = 16.sp, // Increased text size
                        fontWeight = FontWeight.Medium,
                        color = LunarTheme.TextTertiary
                    )
                )
            },
            shape = RoundedCornerShape(12.dp), // Rounded corners
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                autoCorrect = false,
                imeAction = ImeAction.Search
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = LunarTheme.AccentColor.copy(alpha = 0.5f),
                unfocusedIndicatorColor = LunarTheme.BorderColor,
                disabledIndicatorColor = LunarTheme.BorderColor,
                cursorColor = LunarTheme.AccentColor,
                focusedTextColor = LunarTheme.TextPrimary,
                unfocusedTextColor = LunarTheme.TextPrimary.copy(alpha = 0.9f)
            ),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = LunarTheme.ManropeFontFamily,
                fontSize = 16.sp, // Increased text size
                fontWeight = FontWeight.Medium,
                color = LunarTheme.TextPrimary
            ),
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_search),
                    contentDescription = stringResource(id = R.string.search),
                    tint = LunarTheme.TextTertiary
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
                            tint = LunarTheme.TextTertiary
                        )
                    }
                }
            }
        )
    }
}

