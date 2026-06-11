package com.nexchat.core.network.api

import com.nexchat.core.network.dto.AccessTokenResponse
import com.nexchat.core.network.dto.ApiResponse
import com.nexchat.core.network.dto.AuthResponse
import com.nexchat.core.network.dto.GoogleAuthRequest
import com.nexchat.core.network.dto.MagicLinkRequest
import com.nexchat.core.network.dto.MagicLinkVerifyRequest
import com.nexchat.core.network.dto.MessageResponse
import com.nexchat.core.network.dto.PhoneAuthRequest
import com.nexchat.core.network.dto.QRTokenResponse
import com.nexchat.core.network.dto.RefreshRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/email/magic-link")
    suspend fun sendMagicLink(@Body req: MagicLinkRequest): Response<ApiResponse<MessageResponse>>

    @POST("auth/magic-link/verify")
    suspend fun verifyMagicLink(@Body req: MagicLinkVerifyRequest): Response<AuthResponse>

    @POST("auth/google")
    suspend fun googleAuth(@Body req: GoogleAuthRequest): Response<AuthResponse>

    @POST("auth/phone/verify")
    suspend fun phoneAuth(@Body req: PhoneAuthRequest): Response<AuthResponse>

    @POST("auth/refresh")
    suspend fun refresh(@Body req: RefreshRequest): Response<AccessTokenResponse>

    @POST("auth/logout")
    suspend fun logout(): Response<Unit>

    @GET("auth/qr")
    suspend fun getQRToken(): Response<ApiResponse<QRTokenResponse>>
}
