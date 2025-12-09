package lunar.land.ui.core.ui.extensions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

inline fun Modifier.modifyIf(
    predicate: () -> Boolean,
    block: Modifier.() -> Modifier
): Modifier = if (predicate()) this.then(other = block()) else this

fun Modifier.clickableNoRipple(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    this then Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        enabled = enabled,
        onClick = onClick
    )
}

inline fun Modifier.onSwipeDown(
    enabled: Boolean = true,
    crossinline action: () -> Unit
) = composed {
    val velocityThreshold = 600f
    var yStart = 0f
    var yDrag = 0f

    this then Modifier.draggable(
        enabled = enabled,
        orientation = Orientation.Vertical,
        onDragStarted = {
            yStart = it.y
            yDrag = yStart
        },
        state = rememberDraggableState { delta ->
            yDrag += delta
        },
        onDragStopped = { velocity ->
            if (yStart < yDrag && velocity > velocityThreshold) {
                action()
            }
        }
    )
}

inline fun Modifier.onSwipeUp(
    enabled: Boolean = true,
    crossinline action: () -> Unit
) = composed {
    val swipeThreshold = 100f // Reduced threshold for faster response
    var yStart by remember { mutableStateOf(0f) }
    var yDrag by remember { mutableStateOf(0f) }
    var hasTriggered by remember { mutableStateOf(false) }

    this then Modifier.draggable(
        enabled = enabled,
        orientation = Orientation.Vertical,
        onDragStarted = {
            yStart = it.y
            yDrag = yStart
            hasTriggered = false
        },
        state = rememberDraggableState { delta ->
            yDrag += delta
            // Trigger immediately when threshold is reached (not waiting for drag end)
            if (!hasTriggered && yStart > yDrag && (yStart - yDrag) > swipeThreshold) {
                hasTriggered = true
                action()
            }
        },
        onDragStopped = { velocity ->
            // Also trigger on fast swipe even if threshold not fully reached
            if (!hasTriggered && yStart > yDrag && velocity < -500f) {
                action()
            }
        }
    )
}

inline fun Modifier.onSwipeRight(
    enabled: Boolean = true,
    crossinline action: () -> Unit
) = composed {
    val velocityThreshold = 600f
    var xStart = 0f
    var xDrag = 0f

    this then Modifier.draggable(
        enabled = enabled,
        orientation = Orientation.Horizontal,
        onDragStarted = {
            xStart = it.x
            xDrag = xStart
        },
        state = rememberDraggableState { delta ->
            xDrag += delta
        },
        onDragStopped = { velocity ->
            if (xStart < xDrag && velocity > velocityThreshold) {
                action()
            }
        }
    )
}

inline fun Modifier.onSwipeLeft(
    enabled: Boolean = true,
    crossinline action: () -> Unit
) = composed {
    val velocityThreshold = 600f
    var xStart = 0f
    var xDrag = 0f

    this then Modifier.draggable(
        enabled = enabled,
        orientation = Orientation.Horizontal,
        onDragStarted = {
            xStart = it.x
            xDrag = xStart
        },
        state = rememberDraggableState { delta ->
            xDrag += delta
        },
        onDragStopped = { velocity ->
            if (xStart > xDrag && velocity < -velocityThreshold) {
                action()
            }
        }
    )
}

