package com.nexchat.feature.chat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.nexchat.feature.chat.viewmodel.ChatUiModel

private val OnlineGreen = Color(0xFF4CAF50)
private val DraftOrange = Color(0xFFE65100)
private val NexChatTeal = Color(0xFF0F766E)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatListItem(
    chat: ChatUiModel,
    onTap: (chatId: String) -> Unit,
    onAvatarTap: (userId: String) -> Unit,
    onArchive: (chatId: String) -> Unit,
    onPin: (chatId: String) -> Unit,
    onMute: (chatId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showContextMenu by remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState()

    // Observe settled swipe direction — fire callback then reset to Settled.
    // This is the M3-correct replacement for the deprecated confirmValueChange.
    LaunchedEffect(dismissState.currentValue) {
        when (dismissState.currentValue) {
            SwipeToDismissBoxValue.EndToStart -> {
                onArchive(chat.chatId)
                dismissState.reset()
            }
            SwipeToDismissBoxValue.StartToEnd -> {
                onPin(chat.chatId)
                dismissState.reset()
            }
            SwipeToDismissBoxValue.Settled -> Unit
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = { SwipeBackground(dismissState.dismissDirection) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .combinedClickable(
                    onClick = { onTap(chat.chatId) },
                    onLongClick = { showContextMenu = true },
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarWithPresence(
                chat = chat,
                onAvatarTap = onAvatarTap,
            )

            Spacer(modifier = Modifier.width(12.dp))

            ChatContent(
                chat = chat,
                modifier = Modifier.weight(1f),
            )
        }

        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text(if (chat.isArchived) "Unarchive" else "Archive") },
                onClick = { showContextMenu = false; onArchive(chat.chatId) },
            )
            DropdownMenuItem(
                text = { Text(if (chat.isPinned) "Unpin" else "Pin") },
                onClick = { showContextMenu = false; onPin(chat.chatId) },
            )
            DropdownMenuItem(
                text = { Text(if (chat.isMuted) "Unmute" else "Mute") },
                onClick = { showContextMenu = false; onMute(chat.chatId) },
            )
        }
    }
}

@Composable
private fun AvatarWithPresence(
    chat: ChatUiModel,
    onAvatarTap: (userId: String) -> Unit,
) {
    val accentColor = remember(chat.accentColor) {
        runCatching { Color(android.graphics.Color.parseColor(chat.accentColor)) }
            .getOrDefault(Color(0xFF0F766E))
    }
    val hasCustomAccent = chat.accentColor != "#0F766E"

    Box(
        modifier = Modifier
            .size(52.dp)
            .combinedClickable(onClick = { onAvatarTap(chat.chatId) }),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(chat.avatarPath)
                .build(),
            contentDescription = "${chat.name} avatar",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .align(Alignment.Center),
        )

        if (hasCustomAccent) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .border(2.dp, accentColor.copy(alpha = 0.6f), CircleShape)
                    .align(Alignment.Center),
            )
        }

        AnimatedVisibility(
            visible = chat.isOnline,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .padding(2.dp)
                    .background(OnlineGreen, CircleShape),
            )
        }
    }
}

@Composable
private fun ChatContent(
    chat: ChatUiModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = chat.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (chat.lastMessageTime != null) {
                Text(
                    text = chat.lastMessageTime,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            MessagePreview(
                chat = chat,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(6.dp))
            DeliveryIndicator(chat = chat)
        }
    }
}

@Composable
private fun MessagePreview(
    chat: ChatUiModel,
    modifier: Modifier = Modifier,
) {
    val (text, color) = when {
        chat.draftText != null && chat.unreadCount == 0 ->
            "Draft: ${chat.draftText}" to DraftOrange
        chat.isQueued ->
            "⏰ ${chat.lastMessagePreview.orEmpty()}" to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        else ->
            (chat.lastMessagePreview ?: "") to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    }

    Text(
        text = text,
        fontSize = 14.sp,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun DeliveryIndicator(chat: ChatUiModel) {
    when {
        chat.unreadCount > 0 -> {
            Badge(containerColor = Color(0xFF0F766E)) {
                Text(
                    text = if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString(),
                    color = Color.White,
                    fontSize = 11.sp,
                )
            }
        }
        chat.lastMessageStatus == "queued" -> {
            Icon(
                imageVector = Icons.Default.AccessTime,
                contentDescription = "Queued",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
        }
        chat.lastMessageStatus == "sent" -> {
            Icon(
                imageVector = Icons.Default.Done,
                contentDescription = "Sent",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
        }
        chat.lastMessageStatus == "delivered" -> {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = "Delivered",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
        }
        chat.lastMessageStatus == "read" -> {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = "Read",
                modifier = Modifier.size(16.dp),
                tint = Color(0xFF0F766E),
            )
        }
    }
}

@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue?) {
    val color by animateColorAsState(
        targetValue = when (direction) {
            SwipeToDismissBoxValue.EndToStart -> Color(0xFF0F766E).copy(alpha = 0.12f)
            SwipeToDismissBoxValue.StartToEnd -> Color(0xFFF59E0B).copy(alpha = 0.12f)
            else -> Color.Transparent
        },
        label = "swipe_bg",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color),
    )
}
