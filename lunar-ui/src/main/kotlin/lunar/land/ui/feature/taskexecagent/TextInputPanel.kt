package lunar.land.ui.feature.taskexecagent

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lunar.land.ui.R
import lunar.land.ui.core.theme.LunarTheme

/**
 * Text input panel component with input field and execute button.
 * Features a modern, subtle design matching the screen's aesthetic.
 */
@Composable
fun TextInputPanel(
    onExecute: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LunarTheme.CornerRadius.Large))
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
                shape = RoundedCornerShape(LunarTheme.CornerRadius.Large)
            )
            .padding(LunarTheme.Spacing.Large),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Input field
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .onFocusChanged { focusState ->
                    val wasFocused = isFocused
                    isFocused = focusState.isFocused
                    if (wasFocused != isFocused) {
                        onFocusChange(isFocused)
                    }
                },
            interactionSource = interactionSource,
            placeholder = {
                Text(
                    text = "Enter your command...",
                    style = LunarTheme.Typography.Placeholder
                )
            },
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
            textStyle = LunarTheme.Typography.Input,
            singleLine = false,
            maxLines = 3,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Send
            ),
            keyboardActions = KeyboardActions(
                onSend = {
                    if (text.isNotBlank()) {
                        onExecute(text.trim())
                        text = ""
                    }
                }
            ),
            shape = RoundedCornerShape(12.dp)
        )
        
        // Execute button
        ExecuteButton(
            onClick = {
                if (text.isNotBlank()) {
                    onExecute(text.trim())
                    text = ""
                }
            },
            isEnabled = text.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ExecuteButton(
    onClick: () -> Unit,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isEnabled) 1f else 0.5f,
        animationSpec = tween(
            durationMillis = 200,
            easing = FastOutSlowInEasing
        ),
        label = "button_alpha"
    )
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(LunarTheme.CornerRadius.Medium))
            .background(
                brush = if (isEnabled) {
                    Brush.horizontalGradient(
                        colors = listOf(
                            LunarTheme.AccentColor.copy(alpha = 0.2f * animatedAlpha),
                            LunarTheme.AccentColor.copy(alpha = LunarTheme.Alpha.Medium * animatedAlpha)
                        )
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = listOf(LunarTheme.InactiveBackgroundColor, LunarTheme.InactiveBackgroundColor)
                    )
                }
            )
            .border(
                width = LunarTheme.BorderWidth,
                color = if (isEnabled) {
                    LunarTheme.AccentColor.copy(alpha = 0.4f * animatedAlpha)
                } else {
                    LunarTheme.BorderColor
                },
                shape = RoundedCornerShape(LunarTheme.CornerRadius.Medium)
            )
            .then(
                if (isEnabled) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            )
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Execute",
            style = LunarTheme.Typography.Button.copy(
                color = if (isEnabled) {
                    LunarTheme.AccentColor.copy(alpha = animatedAlpha)
                } else {
                    LunarTheme.TextDisabled
                }
            )
        )
    }
}

