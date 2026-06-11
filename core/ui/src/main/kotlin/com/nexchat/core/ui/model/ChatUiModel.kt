package com.nexchat.core.ui.model

import androidx.compose.runtime.Stable

@Stable
data class ChatUiModel(
    val chatId: String,
    val type: String,
    val name: String,
    val avatarPath: String?,
    val accentColorHex: String = "#0F766E",
    val lastMessagePreview: String,
    val lastMessageTime: String,
    val lastMessageStatus: MessageStatus?,
    val unreadCount: Int,
    val draftText: String?,
    val isQueued: Boolean,
    val isMuted: Boolean,
    val isPinned: Boolean,
    val isArchived: Boolean,
    val isOnline: Boolean = false,
)
