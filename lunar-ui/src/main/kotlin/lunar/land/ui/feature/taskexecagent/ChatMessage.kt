package lunar.land.ui.feature.taskexecagent

import androidx.compose.ui.graphics.Color

/**
 * Represents a message in the chat interface.
 */
data class ChatMessage(
    val text: String,
    val type: MessageType,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Type of chat message.
 */
enum class MessageType {
    USER,      // User command
    SYSTEM,    // System status/progress messages
    OUTPUT,    // Command output
    ERROR      // Error messages
}

/**
 * Get color for message type.
 */
fun MessageType.getColor(): Color {
    return when (this) {
        MessageType.USER -> Color(0xFF4DFF88) // Accent green
        MessageType.SYSTEM -> Color(0xFF999999) // Grey
        MessageType.OUTPUT -> Color.White
        MessageType.ERROR -> Color(0xFFFF6B6B) // Red
    }
}

