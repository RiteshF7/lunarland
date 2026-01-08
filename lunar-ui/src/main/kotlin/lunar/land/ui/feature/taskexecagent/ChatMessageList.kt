package lunar.land.ui.feature.taskexecagent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Chat message list component showing user commands, system messages, and output.
 */
@Composable
fun ChatMessageList(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    
    // Auto-scroll to bottom when new messages are added
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        items(
            count = messages.size,
            key = { index -> index } // Use index as key to ensure uniqueness
        ) { index ->
            ChatMessageItem(message = messages[index])
        }
        
        if (messages.isEmpty()) {
            item {
                Text(
                    text = "Send a message to start...",
                    color = Color(0xFF666666),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ChatMessageItem(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when (message.type) {
        MessageType.USER -> Color(0xFF1a3a2a) // Dark green for user messages
        MessageType.SYSTEM -> Color(0xFF2a2a2a) // Dark grey for system
        MessageType.OUTPUT -> Color(0xFF1a1a1a) // Very dark for output
        MessageType.ERROR -> Color(0xFF3a1a1a) // Dark red for errors
    }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = message.text,
            color = message.type.getColor(),
            fontSize = 13.sp,
            fontFamily = if (message.type == MessageType.OUTPUT) {
                FontFamily.Monospace
            } else {
                FontFamily.Default
            },
            modifier = Modifier.weight(1f)
        )
    }
}

