package com.nexchat.feature.chat.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexchat.core.ui.model.MessageUiModel
import com.nexchat.design.ReceivedBubbleShape
import com.nexchat.design.SentBubbleShape
import com.nexchat.design.SpringMedium
import kotlinx.coroutines.launch
import java.util.Calendar

private const val MAX_SWIPE_OFFSET = 72f

@Composable
fun MessageBubble(
    message: MessageUiModel,
    onSwipeToReply: (MessageUiModel) -> Unit,
    onLongPress: (MessageUiModel) -> Unit,
    onReactionTap: (emoji: String) -> Unit,
    onNavigateToMedia: (messageId: String) -> Unit,
    onViewSafetyNumber: (userId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (message.isSystemMessage) {
        SystemMessage(message = message, onViewSafetyNumber = onViewSafetyNumber, modifier = modifier)
        return
    }
    if (message.isDeleted) {
        DeletedBubble(isMine = message.isMine, modifier = modifier)
        return
    }

    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var hasTriggeredReply by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val bubbleShape = if (message.isMine) SentBubbleShape else ReceivedBubbleShape
    val bubbleColor = if (message.isMine)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = if (message.isMine) 64.dp else 8.dp,
                end   = if (message.isMine) 8.dp else 64.dp,
                top   = 2.dp,
                bottom = 2.dp,
            ),
        contentAlignment = if (message.isMine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start,
            // graphicsLayer defers the offsetX state read to the Draw phase — skips Composition
            // and Layout entirely, giving 120fps-class swipe physics on mid-range devices.
            modifier = Modifier
                .graphicsLayer { translationX = offsetX.value }
                .pointerInput(message.isMine) {
                    detectTapGestures(onLongPress = { showMenu = true })
                }
                .then(
                    if (!message.isMine) Modifier.pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { hasTriggeredReply = false },
                            onHorizontalDrag = { _, delta ->
                                val target = (offsetX.value + delta).coerceIn(0f, MAX_SWIPE_OFFSET)
                                scope.launch { offsetX.snapTo(target) }
                                val pct = offsetX.value / MAX_SWIPE_OFFSET
                                if (pct in 0.40f..0.45f) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (pct >= 0.5f && !hasTriggeredReply) {
                                    hasTriggeredReply = true
                                    onSwipeToReply(message)
                                }
                            },
                            onDragEnd = {
                                scope.launch { offsetX.animateTo(0f, animationSpec = SpringMedium) }
                            },
                        )
                    } else Modifier
                ),
        ) {
            Surface(shape = bubbleShape, color = bubbleColor, tonalElevation = 1.dp) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .widthIn(min = 64.dp),
                ) {
                    when (message.type) {
                        "image", "video" -> MediaPreview(
                            mediaUrl = message.mediaUrl,
                            mediaPath = message.mediaPath,
                            mediaThumb = message.mediaThumb,
                            onTap = { onNavigateToMedia(message.id) },
                        )
                        "voice" -> VoicePlayer(
                            mediaUrl = message.mediaUrl,
                            mediaPath = message.mediaPath,
                            durationMs = 0L,
                        )
                        else -> Text(
                            text = message.content.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = formatMessageTime(message.createdAt),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                        if (message.isMine) {
                            Spacer(Modifier.width(3.dp))
                            DeliveryTick(status = message.status)
                        }
                    }
                }
            }

            ReactionRow(reactions = message.reactions, onReactionTap = onReactionTap)
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(text = { Text("Reply") }, onClick = { showMenu = false; onSwipeToReply(message) })
            if (!message.isMine) {
                DropdownMenuItem(text = { Text("Copy") }, onClick = { showMenu = false })
            }
            DropdownMenuItem(text = { Text("Delete") }, onClick = { showMenu = false })
        }
    }
}

@Composable
private fun DeletedBubble(isMine: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start  = if (isMine) 64.dp else 8.dp,
                end    = if (isMine) 8.dp else 64.dp,
                top    = 2.dp,
                bottom = 2.dp,
            ),
        contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Text(
            text = "🚫 This message was deleted",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

private fun formatMessageTime(epochMs: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
    return "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
}
