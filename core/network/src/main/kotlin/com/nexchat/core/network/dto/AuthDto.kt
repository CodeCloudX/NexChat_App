package com.nexchat.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.nexchat.core.network.util.Iso8601TimestampSerializer

@Serializable
data class MagicLinkRequest(
    @SerialName("email") val email: String
)

@Serializable
data class MagicLinkVerifyRequest(
    @SerialName("token") val token: String,
    @SerialName("device_type") val deviceType: String,
    @SerialName("android_id") val androidId: String,
    @SerialName("fcm_token") val fcmToken: String = ""
)

@Serializable
data class MessageResponse(
    @SerialName("message") val message: String
)

@Serializable
data class AuthResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("is_new_user") val isNewUser: Boolean = false
)

@Serializable
data class GoogleAuthRequest(
    @SerialName("firebase_token") val firebaseToken: String,
    @SerialName("device_type") val deviceType: String,
    @SerialName("android_id") val androidId: String,
    @SerialName("fcm_token") val fcmToken: String = ""
)

@Serializable
data class PhoneAuthRequest(
    @SerialName("firebase_id_token") val firebaseIdToken: String,
    @SerialName("device_type") val deviceType: String,
    @SerialName("android_id") val androidId: String,
    @SerialName("fcm_token") val fcmToken: String = ""
)

@Serializable
data class RefreshRequest(
    @SerialName("refresh_token") val refreshToken: String
)

@Serializable
data class AccessTokenResponse(
    @SerialName("access_token") val accessToken: String
)

@Serializable
data class QRTokenResponse(
    @SerialName("token") val token: String,
    @SerialName("expires_in") val expiresIn: Int
)

@Serializable
data class PublicUser(
    @SerialName("id") val id: String,
    @SerialName("phone") val phone: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @Serializable(with = Iso8601TimestampSerializer::class) @SerialName("last_seen") val lastSeen: Long? = null
)
