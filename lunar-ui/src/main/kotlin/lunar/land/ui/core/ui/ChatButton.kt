package lunar.land.ui.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Button component matching the TaskExecutorAgentScreen theme.
 * Features:
 * - Light grey background (0xFF3A3A3A) matching the input panel
 * - White text
 * - 12dp rounded corners
 * - Press state feedback with darker background
 */
@Composable
fun ChatButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Background color changes on press
    val backgroundColor = when {
        !enabled -> Color(0xFF2A2A2A) // Dark grey when disabled
        isPressed -> Color(0xFF2A2A2A) // Darker grey when pressed
        else -> Color(0xFF3A3A3A) // Light grey default (matching input panel)
    }
    
    // Text color
    val textColor = if (enabled) {
        Color.White
    } else {
        Color(0xFF666666) // Grey when disabled
    }
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Preview
@Composable
private fun PreviewChatButton() {
    Column(
        modifier = Modifier
            .background(Color(0xFF2A2A2A))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ChatButton(
            text = "Enabled Button",
            onClick = {},
            enabled = true
        )
        ChatButton(
            text = "Disabled Button",
            onClick = {},
            enabled = false
        )
    }
}

