package com.nexchat.core.network.interceptor

import com.nexchat.core.auth.TokenStorage
import com.nexchat.core.common.DeviceInfoProvider
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val tokenStorage: TokenStorage,
    private val device: DeviceInfoProvider
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokenStorage.getAccessToken() 
            ?: return chain.proceed(request)

        val newRequest = request.newBuilder()
            .header("Authorization", "Bearer $token")
            .header("X-Android-ID", device.androidId)
            .build()
            
        return chain.proceed(newRequest)
    }
}
