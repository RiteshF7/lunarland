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

/**
 * Manrope font family matching TaskExecutorAgentScreen theme.
 */
private val manropeFontFamily = FontFamily(
    Font(resId = R.font.manrope_variable, weight = FontWeight.Normal)
)

@Composable
fun SearchField(
    modifier: Modifier = Modifier,
    placeholder: String,
    query: String,
    onQueryChange: (String) -> Unit,
    paddingValues: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
) {
    // Theme colors from TaskExecutorAgentScreen
    val accentColor = Color(0xFF4DFF88)
    val inputBackgroundColor = Color(0xFF1a1f1a)
    val borderColor = Color(0xFF2a3a2a)
    val cornerRadius = 16.dp
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(paddingValues = paddingValues)
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        inputBackgroundColor.copy(alpha = 0.8f),
                        inputBackgroundColor.copy(alpha = 0.6f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = borderColor,
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
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = manropeFontFamily,
                        fontSize = 14.sp
                    ),
                    color = Color.White.copy(alpha = 0.4f)
                )
            },
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                autoCorrect = false,
                imeAction = ImeAction.Search
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = accentColor.copy(alpha = 0.5f),
                unfocusedIndicatorColor = borderColor,
                disabledIndicatorColor = borderColor,
                cursorColor = accentColor,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White.copy(alpha = 0.9f)
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = manropeFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            ),
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_search),
                    contentDescription = stringResource(id = R.string.search),
                    tint = Color.White.copy(alpha = 0.4f)
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
                            tint = Color.White.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        )
    }
}

