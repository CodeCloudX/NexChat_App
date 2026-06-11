package com.nexchat.core.ui.model

import androidx.compose.runtime.Stable

@Stable
data class ContactUiModel(
    val id: String,
    val displayName: String,
    val firstName: String?,
    val lastName: String?,
    val resolvedName: String,
    val phone: String?,
    val email: String?,
    val avatarPath: String?,
    val accentColorHex: String = "#0F766E",
    val isOnline: Boolean = false,
    val lastSeen: Long? = null,
    val isBlocked: Boolean = false,
    val bannerDismissed: Boolean = false,
)
