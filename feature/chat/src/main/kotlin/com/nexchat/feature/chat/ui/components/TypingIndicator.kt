package com.nexchat.feature.chat.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TypingIndicator(userName: String, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    @Composable
    fun dot(delayMs: Int): Float {
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(500, delayMillis = delayMs, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "dot_$delayMs",
        )
        return alpha
    }

    val a1 = dot(0)
    val a2 = dot(150)
    val a3 = dot(300)

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Text(
            text = if (userName.isNotBlank()) "$userName is typing" else "typing",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Text("●", fontSize = 6.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.alpha(a1))
        Spacer(Modifier.width(2.dp))
        Text("●", fontSize = 6.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.alpha(a2))
        Spacer(Modifier.width(2.dp))
        Text("●", fontSize = 6.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.alpha(a3))
    }
}
