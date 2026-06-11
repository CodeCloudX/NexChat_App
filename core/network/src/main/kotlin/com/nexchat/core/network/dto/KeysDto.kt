package com.nexchat.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PreKeyDto(
    @SerialName("key_id") val keyId: Int,
    @SerialName("public_key") val publicKey: String
)

@Serializable
data class SignedPreKeyDto(
    @SerialName("key_id") val keyId: Int,
    @SerialName("public_key") val publicKey: String,
    @SerialName("signature") val signature: String
)

// Mirrors backend keys.UploadRequest exactly.
// identity_key, signed_prekey, one_time_prekeys are base64 strings decoded
// by goccy/go-json on the server — no extra encoding layer needed.
@Serializable
data class UploadPreKeysRequest(
    @SerialName("type") val type: String,
    @SerialName("identity_key") val identityKey: String,
    @SerialName("signed_prekey") val signedPreKey: SignedPreKeyDto,
    @SerialName("one_time_prekeys") val oneTimePrekeys: List<PreKeyDto>
)

// Mirrors backend keys.PrekeyBundleResponse.
// one_time_prekey is nullable — server sends null when the OTK pool is empty.
@Serializable
data class PreKeyBundleResponse(
    @SerialName("identity_key") val identityKey: String,
    @SerialName("signed_prekey") val signedPreKey: SignedPreKeyDto,
    @SerialName("one_time_prekey") val oneTimePreKey: PreKeyDto? = null
)
