package lunar.land.ui.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * A reusable error banner component that displays error messages with a retry button
 */
@Composable
fun ErrorBanner(
    error: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryButtonText: String = "Retry"
) {
    AnimatedVisibility(
        visible = error != null,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = error ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.fillMaxWidth()
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onRetry,
                    text = retryButtonText,
                    textColor = MaterialTheme.colorScheme.onErrorContainer,
                    backgroundColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewErrorBanner() {
    MaterialTheme {
        ErrorBanner(
            error = "Download failed: Connection timeout",
            onRetry = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

