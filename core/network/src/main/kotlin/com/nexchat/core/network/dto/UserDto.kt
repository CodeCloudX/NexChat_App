package com.nexchat.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    @SerialName("id") val id: String,
    @SerialName("phone") val phone: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("last_seen") val lastSeen: Long? = null,
    @SerialName("backup_metadata") val backupMetadata: String? = null
)

@Serializable
data class UpdateProfileRequest(
    @SerialName("display_name") val displayName: String? = null
)

@Serializable
data class AvatarResponse(
    @SerialName("avatar_url") val avatarUrl: String
)

@Serializable
data class BackupMetaRequest(
    @SerialName("backup_metadata") val backupMetadata: String
)

@Serializable
data class FcmTokenRequest(
    @SerialName("token") val token: String
)
