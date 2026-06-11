package com.nexchat.core.ui.model

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class MessageUiModel(
    val id: String,
    val localId: String,
    val chatId: String,
    val senderId: String,
    val isMine: Boolean,
    val content: String?,
    val type: String,
    val status: MessageStatus,
    val createdAt: Long,
    val serverTs: Long?,
    val mediaPath: String?,
    val mediaUrl: String?,
    val mediaThumb: String?,
    val mediaKey: String?,
    val replyToId: String?,
    val reactions: PersistentList<ReactionUiModel> = persistentListOf(),
    val isDeleted: Boolean = false,
    val isSystemMessage: Boolean = false,
)

enum class MessageStatus { QUEUED, SENDING, SENT, DELIVERED, READ, FAILED }

@Stable
data class ReactionUiModel(
    val emoji: String,
    val userId: String,
)
