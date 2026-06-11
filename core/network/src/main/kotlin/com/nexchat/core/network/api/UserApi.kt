package com.nexchat.core.network.api

import com.nexchat.core.network.dto.AvatarResponse
import com.nexchat.core.network.dto.BackupMetaRequest
import com.nexchat.core.network.dto.FcmTokenRequest
import com.nexchat.core.network.dto.PublicUser
import com.nexchat.core.network.dto.UpdateProfileRequest
import com.nexchat.core.network.dto.UserProfile
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path

interface UserApi {
    @GET("user/me")
    suspend fun getProfile(): Response<UserProfile>

    @PUT("user/me")
    suspend fun updateProfile(@Body req: UpdateProfileRequest): Response<UserProfile>

    @Multipart
    @POST("user/me/avatar")
    suspend fun updateAvatar(@Part avatar: MultipartBody.Part): Response<AvatarResponse>

    @POST("user/me/backup-metadata")
    suspend fun updateBackupMetadata(@Body req: BackupMetaRequest): Response<Unit>

    @GET("user/{id}")
    suspend fun getUserById(@Path("id") id: String): Response<PublicUser>

    @POST("user/fcm-token")
    suspend fun updateFcmToken(@Body req: FcmTokenRequest): Response<Unit>
}
