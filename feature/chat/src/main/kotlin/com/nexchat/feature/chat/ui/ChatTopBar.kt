package com.nexchat.feature.chat.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.nexchat.core.ui.model.ContactUiModel
import com.nexchat.design.MessageDuration
import com.nexchat.feature.chat.ui.components.TypingIndicator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    contact: ContactUiModel?,
    isTyping: Boolean,
    typingName: String,
    onBack: () -> Unit,
    onAvatarTap: () -> Unit,
    onMenuItemClick: (ChatMenuAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(contact?.avatarPath)
                        .build(),
                    contentDescription = contact?.resolvedName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onAvatarTap),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = contact?.resolvedName ?: "",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                    )
                    val lastSeenMs = contact?.lastSeen
                    AnimatedContent(
                        targetState = when {
                            isTyping                   -> SubtitleState.Typing
                            contact?.isOnline == true  -> SubtitleState.Online
                            lastSeenMs != null         -> SubtitleState.LastSeen(lastSeenMs)
                            else                       -> SubtitleState.None
                        },
                        transitionSpec = {
                            fadeIn(tween(MessageDuration)) togetherWith fadeOut(tween(MessageDuration))
                        },
                        label = "TopBarSubtitle",
                    ) { state ->
                        when (state) {
                            is SubtitleState.Typing    -> TypingIndicator(userName = typingName)
                            is SubtitleState.Online    -> Text("Online", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            is SubtitleState.LastSeen  -> Text(formatLastSeen(state.epochMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            is SubtitleState.None      -> Unit
                        }
                    }
                }
            }
        },
        actions = {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Menu")
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("View Contact") },
                    onClick = { menuExpanded = false; onMenuItemClick(ChatMenuAction.ViewContact) },
                )
                DropdownMenuItem(
                    text = { Text("Block") },
                    onClick = { menuExpanded = false; onMenuItemClick(ChatMenuAction.Block) },
                )
                DropdownMenuItem(
                    text = { Text("Clear Chat") },
                    onClick = { menuExpanded = false; onMenuItemClick(ChatMenuAction.ClearChat) },
                )
            }
        },
    )
}

private sealed interface SubtitleState {
    data object Typing : SubtitleState
    data object Online : SubtitleState
    data class LastSeen(val epochMs: Long) : SubtitleState
    data object None : SubtitleState
}

enum class ChatMenuAction { ViewContact, Block, ClearChat }

private val lastSeenFmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

private fun formatLastSeen(epochMs: Long): String =
    "last seen ${lastSeenFmt.format(Date(epochMs))}"
