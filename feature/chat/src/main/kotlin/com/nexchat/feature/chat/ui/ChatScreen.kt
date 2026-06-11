package com.nexchat.feature.chat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.nexchat.core.ui.model.MessageUiModel
import com.nexchat.design.WarningYellow
import com.nexchat.design.WarningYellowDark
import com.nexchat.feature.chat.ui.components.InputBar
import com.nexchat.feature.chat.ui.components.MessageBubble
import com.nexchat.feature.chat.ui.components.ReplyPreview
import com.nexchat.feature.chat.viewmodel.ChatSideEffect
import com.nexchat.feature.chat.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onNavigateToContact: (userId: String) -> Unit,
    onNavigateToMedia: (messageId: String) -> Unit,
    onNavigateToSafetyNumber: (userId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val messages = viewModel.messages.collectAsLazyPagingItems()
    val listState = rememberLazyListState()

    DisposableEffect(Unit) {
        onDispose { viewModel.saveDraft() }
    }

    LaunchedEffect(Unit) {
        viewModel.sideEffects.collect { effect ->
            when (effect) {
                is ChatSideEffect.ScrollToMessage -> {
                    val index = (0 until messages.itemCount).firstOrNull {
                        messages.peek(it)?.localId == effect.localId
                    }
                    if (index != null) listState.animateScrollToItem(index)
                }
                else -> Unit
            }
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            ChatTopBar(
                contact = state.contact,
                isTyping = state.isTyping,
                typingName = state.typingUserName,
                onBack = onBack,
                onAvatarTap = { state.contact?.let { onNavigateToContact(it.id) } },
                onMenuItemClick = { action ->
                    when (action) {
                        ChatMenuAction.ViewContact -> state.contact?.let { onNavigateToContact(it.id) }
                        ChatMenuAction.Block       -> viewModel.blockContact()
                        ChatMenuAction.ClearChat   -> viewModel.clearChat()
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // Applied here so BOTH the LazyColumn and InputBar shift up together
                // when the IME opens — prevents a gap between the list and keyboard.
                .imePadding(),
        ) {
            AnimatedVisibility(
                visible = state.contact?.bannerDismissed == false,
                enter = slideInVertically(),
                exit = slideOutVertically(),
            ) {
                NotAddedBanner(
                    onAdd = { viewModel.addContact() },
                    onDismiss = { viewModel.dismissBanner() },
                )
            }

            // reverseLayout=true: newest messages appear at the bottom. LazyColumn renders
            // from bottom-up so Room inserts appear without scroll jumps.
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.weight(1f),
            ) {
                items(
                    count = messages.itemCount,
                    // key is mandatory — prevents item recomposition on list changes.
                    key = messages.itemKey { it.localId },
                    // contentType lets Compose reuse composition nodes for same-type items.
                    contentType = { index -> messages.peek(index)?.type ?: "text" },
                ) { index ->
                    val message = messages[index] ?: return@items
                    val prevMessage = if (index + 1 < messages.itemCount) messages.peek(index + 1) else null

                    if (shouldShowDateSeparator(message, prevMessage)) {
                        DateSeparator(epochMs = message.createdAt)
                    }

                    MessageBubble(
                        message = message,
                        onSwipeToReply = { viewModel.setReplyTarget(it) },
                        onLongPress = { /* contextual menu handled inside bubble */ },
                        onReactionTap = { emoji -> viewModel.sendReaction(message.localId, emoji) },
                        onNavigateToMedia = onNavigateToMedia,
                        onViewSafetyNumber = onNavigateToSafetyNumber,
                    )
                }
            }

            AnimatedVisibility(visible = state.replyTarget != null) {
                state.replyTarget?.let { target ->
                    ReplyPreview(
                        message = target,
                        onDismiss = { viewModel.clearReplyTarget() },
                    )
                }
            }

            InputBar(
                inputText = state.inputText,
                onInputChanged = { viewModel.onInputChanged(it) },
                onSend = { viewModel.sendMessage() },
                onStartRecording = { viewModel.startRecording() },
                onStopRecording = { viewModel.stopRecording() },
            )
        }
    }
}

@Composable
private fun NotAddedBanner(
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = WarningYellow.copy(alpha = 0.15f),
    ) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = "This contact is not in your address book.",
                style = MaterialTheme.typography.bodySmall,
                color = WarningYellowDark,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            androidx.compose.foundation.layout.Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                TextButton(onClick = onAdd) { Text("Add", color = WarningYellowDark) }
                TextButton(onClick = onDismiss) { Text("Dismiss", color = WarningYellowDark) }
            }
        }
    }
}

@Composable
private fun DateSeparator(epochMs: Long, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        ) {
            Text(
                text = formatDateSeparator(epochMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun shouldShowDateSeparator(current: MessageUiModel, previous: MessageUiModel?): Boolean {
    if (previous == null) return true
    val currentDay = TimeUnit.MILLISECONDS.toDays(current.createdAt)
    val previousDay = TimeUnit.MILLISECONDS.toDays(previous.createdAt)
    return currentDay != previousDay
}

private val dateSeparatorFmt = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())

private fun formatDateSeparator(epochMs: Long): String {
    val nowDays = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())
    val msgDays = TimeUnit.MILLISECONDS.toDays(epochMs)
    return when (nowDays - msgDays) {
        0L   -> "Today"
        1L   -> "Yesterday"
        else -> dateSeparatorFmt.format(Date(epochMs))
    }
}
