package com.nexchat.core.network.api

import com.nexchat.core.network.dto.PreKeyBundleResponse
import com.nexchat.core.network.dto.UploadPreKeysRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface KeysApi {
    @POST("keys/prekeys")
    suspend fun uploadPreKeys(@Body req: UploadPreKeysRequest): Response<Unit>

    @GET("keys/{userId}")
    suspend fun getPreKeyBundle(@Path("userId") userId: String): Response<PreKeyBundleResponse>
}
