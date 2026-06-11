package com.nexchat.core.network.api

import com.nexchat.core.network.dto.ConfirmMediaRequest
import com.nexchat.core.network.dto.MediaResponse
import com.nexchat.core.network.dto.PresignedUrlRequest
import com.nexchat.core.network.dto.PresignedUrlResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface MediaApi {
    @POST("media/presigned")
    suspend fun getPresignedUrl(@Body req: PresignedUrlRequest): Response<PresignedUrlResponse>

    @POST("media/confirm")
    suspend fun confirmUpload(@Body req: ConfirmMediaRequest): Response<MediaResponse>

    @GET("media/{id}")
    suspend fun getMediaInfo(@Path("id") id: String): Response<MediaResponse>
}
