package lunar.land.ui.core.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Represents the state of a setup step button
 */
sealed class StepButtonState {
    object Idle : StepButtonState()
    object Running : StepButtonState()
    data class Completed(val message: String) : StepButtonState()
    object Skipped : StepButtonState()
    data class Failed(val error: String) : StepButtonState()
}

/**
 * A reusable button component for setup steps with run/skip/rerun states
 */
@Composable
fun StepButton(
    text: String,
    state: StepButtonState,
    enabled: Boolean = true,
    onRun: () -> Unit,
    onSkip: () -> Unit,
    onRerun: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when (state) {
            is StepButtonState.Idle -> {
                ActionButton(
                    text = text,
                    onClick = onRun,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "Skip",
                    onClick = onSkip,
                    modifier = Modifier.weight(1f)
                )
            }
            is StepButtonState.Running -> {
                ActionButton(
                    text = "$text...",
                    onClick = { /* Disabled during running */ },
                    modifier = Modifier.weight(2f)
                )
            }
            is StepButtonState.Completed -> {
                Crossfade(
                    label = "Step completed state",
                    targetState = state.message
                ) { message ->
                    ActionButton(
                        text = "$text ✓ ($message)",
                        onClick = { },
                        modifier = Modifier.weight(1f)
                    )
                }
                TextButton(
                    text = "Rerun",
                    onClick = onRerun,
                    modifier = Modifier.weight(1f)
                )
            }
            is StepButtonState.Skipped -> {
                ActionButton(
                    text = "$text (Skipped)",
                    onClick = { },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "Rerun",
                    onClick = onRerun,
                    modifier = Modifier.weight(1f)
                )
            }
            is StepButtonState.Failed -> {
                ActionButton(
                    text = "$text (Failed)",
                    onClick = { },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "Rerun",
                    onClick = onRerun,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewStepButton() {
    MaterialTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            StepButton(
                text = "1. Check Bootstrap",
                state = StepButtonState.Idle,
                onRun = {},
                onSkip = {},
                onRerun = {}
            )
        }
    }
}

