package com.nexchat.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateGroupRequest(
    @SerialName("name") val name: String,
    @SerialName("description") val description: String? = null,
    @SerialName("member_ids") val memberIds: List<String>
)

@Serializable
data class UpdateGroupRequest(
    @SerialName("name") val name: String? = null,
    @SerialName("description") val description: String? = null
)

@Serializable
data class AddMemberRequest(
    @SerialName("user_id") val userId: String,
    @SerialName("role") val role: String = "member"
)

@Serializable
data class UpdateRoleRequest(
    @SerialName("role") val role: String
)
