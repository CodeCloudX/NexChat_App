package com.nexchat.core.network.api

import com.nexchat.core.network.dto.AddMemberRequest
import com.nexchat.core.network.dto.ChatResponse
import com.nexchat.core.network.dto.CreateGroupRequest
import com.nexchat.core.network.dto.UpdateGroupRequest
import com.nexchat.core.network.dto.UpdateRoleRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface GroupApi {
    @POST("groups")
    suspend fun createGroup(@Body req: CreateGroupRequest): Response<ChatResponse>

    @GET("groups/{id}")
    suspend fun getGroupById(@Path("id") id: String): Response<ChatResponse>

    @PUT("groups/{id}")
    suspend fun updateGroup(@Path("id") id: String, @Body req: UpdateGroupRequest): Response<ChatResponse>

    @POST("groups/{id}/members")
    suspend fun addMember(@Path("id") id: String, @Body req: AddMemberRequest): Response<Unit>

    @DELETE("groups/{id}/members/{userId}")
    suspend fun removeMember(@Path("id") id: String, @Path("userId") userId: String): Response<Unit>

    @PUT("groups/{id}/members/{userId}/role")
    suspend fun updateMemberRole(
        @Path("id") id: String,
        @Path("userId") userId: String,
        @Body req: UpdateRoleRequest
    ): Response<Unit>

    @DELETE("groups/{id}")
    suspend fun deleteGroup(@Path("id") id: String): Response<Unit>
}
