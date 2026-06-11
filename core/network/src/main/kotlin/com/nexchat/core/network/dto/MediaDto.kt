package com.nexchat.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PresignedUrlRequest(
    @SerialName("chat_id") val chatId: String,
    @SerialName("filename") val filename: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("size") val size: Long
)

@Serializable
data class PresignedUrlResponse(
    @SerialName("upload_url") val uploadUrl: String,
    @SerialName("media_id") val mediaId: String
)

@Serializable
data class ConfirmMediaRequest(
    @SerialName("media_id") val mediaId: String
)

@Serializable
data class MediaResponse(
    @SerialName("id") val id: String,
    @SerialName("url") val url: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("size") val size: Long
)
