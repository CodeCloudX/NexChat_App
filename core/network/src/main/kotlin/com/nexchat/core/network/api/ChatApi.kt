package com.nexchat.core.network.api

import com.nexchat.core.network.dto.ChatDetailResponse
import com.nexchat.core.network.dto.ChatResponse
import com.nexchat.core.network.dto.CreateChatRequest
import com.nexchat.core.network.dto.ReactionRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

import com.nexchat.core.network.dto.DataWrapper

interface ChatApi {
    @GET("chats")
    suspend fun getChats(): Response<DataWrapper<List<ChatResponse>>>

    @POST("chats")
    suspend fun createChat(@Body req: CreateChatRequest): Response<DataWrapper<ChatResponse>>

    @GET("chats/{id}")
    suspend fun getChatById(@Path("id") id: String): Response<DataWrapper<ChatDetailResponse>>

    @DELETE("chats/{chatId}/messages/{msgId}")
    suspend fun deleteMessage(@Path("chatId") chatId: String, @Path("msgId") msgId: String): Response<Unit>

    @POST("chats/{chatId}/reactions")
    suspend fun addReaction(@Path("chatId") chatId: String, @Body req: ReactionRequest): Response<Unit>
}
