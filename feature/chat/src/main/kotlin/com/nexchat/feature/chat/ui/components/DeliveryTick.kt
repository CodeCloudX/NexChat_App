package com.nexchat.feature.chat.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexchat.core.ui.model.MessageStatus
import com.nexchat.design.DraftOrange
import com.nexchat.design.ErrorRed
import com.nexchat.design.ReadBlue
import androidx.compose.ui.graphics.Color

@Composable
fun DeliveryTick(status: MessageStatus, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = status,
        transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
        label = "DeliveryTick",
        modifier = modifier,
    ) { target ->
        when (target) {
            // QUEUED (offline/pending) and SENDING (in-flight) are visually identical.
            // WhatsApp convention: one clock icon for both states — no separate "spinner" for sending.
            MessageStatus.QUEUED, MessageStatus.SENDING ->
                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(12.dp), tint = DraftOrange)
            MessageStatus.SENT ->
                Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.Gray)
            MessageStatus.DELIVERED ->
                Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.Gray)
            MessageStatus.READ ->
                Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(12.dp), tint = ReadBlue)
            MessageStatus.FAILED ->
                Icon(Icons.Default.Error, contentDescription = null, modifier = Modifier.size(12.dp), tint = ErrorRed)
        }
    }
}
