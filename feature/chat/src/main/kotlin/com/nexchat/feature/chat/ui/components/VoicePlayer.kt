package com.nexchat.feature.chat.ui.components

import android.media.MediaPlayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexchat.design.NexChatTeal
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.sin

private const val WAVEFORM_BARS = 30

@Composable
fun VoicePlayer(
    mediaUrl: String?,
    mediaPath: String?,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    val player = remember {
        MediaPlayer().apply {
            runCatching {
                val source = mediaPath ?: mediaUrl ?: return@apply
                setDataSource(source)
                prepare()
            }
        }
    }
    DisposableEffect(Unit) { onDispose { runCatching { player.release() } } }

    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var speed by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            val dur = player.duration.takeIf { it > 0 } ?: 1
            progress = player.currentPosition.toFloat() / dur
            if (progress >= 1f) {
                isPlaying = false
                progress = 0f
                player.seekTo(0)
            }
            delay(100)
        }
    }

    val playedColor = NexChatTeal
    val unplayedColor = Color.Gray.copy(alpha = 0.4f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(
            onClick = {
                if (isPlaying) {
                    player.pause()
                    isPlaying = false
                } else {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        player.playbackParams = player.playbackParams.setSpeed(speed)
                    }
                    player.start()
                    isPlaying = true
                }
            },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = NexChatTeal,
            )
        }

        // Single Canvas draw call for all 30 waveform bars — no per-bar layout cost.
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(32.dp)
                .pointerInput(Unit) {
                    detectDragGestures { _, drag ->
                        val newProgress = (progress + drag.x / size.width).coerceIn(0f, 1f)
                        progress = newProgress
                        val dur = player.duration.takeIf { it > 0 } ?: 1
                        player.seekTo((newProgress * dur).toInt())
                    }
                },
        ) {
            val barWidth = size.width / (WAVEFORM_BARS * 2f)
            val radius = CornerRadius(barWidth / 2f, barWidth / 2f)
            for (i in 0 until WAVEFORM_BARS) {
                val barFraction = (i + 1f) / WAVEFORM_BARS
                val heightFraction = 0.3f + 0.7f * abs(sin(i * 0.6f))
                val barHeight = size.height * heightFraction
                val x = i * barWidth * 2f
                val y = (size.height - barHeight) / 2f
                drawRoundRect(
                    color = if (barFraction <= progress) playedColor else unplayedColor,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = radius,
                )
            }
        }

        val elapsed = if (isPlaying) (progress * durationMs).toLong() else durationMs
        Text(
            text = formatDuration(elapsed),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = "${speed}x",
            style = MaterialTheme.typography.labelSmall.copy(
                color = NexChatTeal,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            modifier = Modifier.clickable {
                speed = when (speed) {
                    1f   -> 1.5f
                    1.5f -> 2f
                    else -> 1f
                }
                if (isPlaying && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    player.playbackParams = player.playbackParams.setSpeed(speed)
                }
            },
        )
    }
}

private fun formatDuration(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return "%d:%02d".format(minutes, seconds)
}
