package com.nexchat.feature.chat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexchat.feature.chat.ui.components.ChatListItem
import com.nexchat.feature.chat.viewmodel.ChatListSideEffect
import com.nexchat.feature.chat.viewmodel.ChatListViewModel
import com.nexchat.feature.chat.viewmodel.ConnectivityBanner
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onNavigateToChat: (chatId: String) -> Unit,
    onNavigateToProfile: (userId: String) -> Unit,
    onComposeNewChat: () -> Unit,
    viewModel: ChatListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.sideEffects.collectLatest { effect ->
            when (effect) {
                is ChatListSideEffect.NavigateToChat -> onNavigateToChat(effect.chatId)
                is ChatListSideEffect.NavigateToProfile -> onNavigateToProfile(effect.userId)
                is ChatListSideEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                ChatListSideEffect.ShowArchiveUndo -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "Chat archived",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short,
                    )
                    // Undo archival logic would be wired here in a future phase.
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("NexChat", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = onComposeNewChat) {
                        Icon(
                            imageVector = Icons.Default.Create,
                            contentDescription = "New conversation",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ConnectivityBannerBar(banner = uiState.banner)

            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                placeholder = { Text("Search conversations…") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                    )
                },
                singleLine = true,
            )

            val displayList = if (uiState.searchQuery.isBlank()) uiState.chats else uiState.searchResults

            if (displayList.isEmpty() && uiState.searchQuery.isNotBlank()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No results for \"${uiState.searchQuery}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            } else if (displayList.isEmpty() && uiState.searchQuery.isBlank()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Create,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No chats yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap the pencil icon to start messaging.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        items = displayList,
                        key = { it.chatId },
                    ) { chat ->
                        ChatListItem(
                            chat = chat,
                            onTap = viewModel::onChatTap,
                            onAvatarTap = viewModel::onAvatarTap,
                            onArchive = viewModel::onArchiveChat,
                            onPin = { /* Phase 4 */ },
                            onMute = { /* Phase 4 */ },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Three-state WhatsApp-style connectivity banner.
 * - Hidden     → everything is healthy (NONE)
 * - Red bar    → no internet at all (NO_NETWORK)
 * - Amber bar  → internet present but WebSocket reconnecting (CONNECTING)
 */
@Composable
private fun ConnectivityBannerBar(banner: ConnectivityBanner) {
    AnimatedVisibility(
        visible = banner != ConnectivityBanner.NONE,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        val (bgColor, text, showSpinner) = when (banner) {
            ConnectivityBanner.NO_NETWORK -> Triple(
                Color(0xFFB71C1C),
                "No internet connection",
                false,
            )
            ConnectivityBanner.CONNECTING -> Triple(
                Color(0xFFF57F17),
                "Connecting\u2026",
                true,
            )
            ConnectivityBanner.NONE -> Triple(Color.Transparent, "", false)
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = bgColor,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showSpinner) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
            }
        }
    }
}

