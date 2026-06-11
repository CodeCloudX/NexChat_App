package com.nexchat.feature.chat.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexchat.core.ui.model.MessageUiModel
import com.nexchat.design.CardShape
import com.nexchat.design.WarningYellow
import com.nexchat.design.WarningYellowDark

@Composable
fun SystemMessage(
    message: MessageUiModel,
    onViewSafetyNumber: (userId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!message.content.orEmpty().startsWith("KEY_CHANGED")) return

    val userId = message.content.orEmpty().removePrefix("KEY_CHANGED:").trim()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp),
        color = WarningYellow.copy(alpha = 0.15f),
        shape = CardShape,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = WarningYellowDark,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "$userId's security code changed",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = WarningYellowDark,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "This usually means $userId reinstalled NexChat.",
                style = MaterialTheme.typography.bodySmall,
                color = WarningYellowDark.copy(alpha = 0.7f),
            )
            TextButton(onClick = { onViewSafetyNumber(userId) }) {
                Text("View Safety Number", color = WarningYellowDark)
            }
        }
    }
}
