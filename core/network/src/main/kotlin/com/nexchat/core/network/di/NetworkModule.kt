package com.nexchat.core.network.di

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.nexchat.core.network.api.AuthApi
import com.nexchat.core.network.api.ChatApi
import com.nexchat.core.network.api.ContactApi
import com.nexchat.core.network.api.GroupApi
import com.nexchat.core.network.api.KeysApi
import com.nexchat.core.network.api.MediaApi
import com.nexchat.core.network.api.UserApi
import com.nexchat.core.network.interceptor.AuthInterceptor
import com.nexchat.core.network.interceptor.NexChatLoggingInterceptor
import com.nexchat.core.network.interceptor.TokenRefreshAuthenticator
import com.nexchat.core.network.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.CertificatePinner
import okhttp3.ConnectionSpec
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        authenticator: TokenRefreshAuthenticator
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
            .addInterceptor(authInterceptor)
            .authenticator(authenticator)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        if (!BuildConfig.DEBUG) {
            val certificatePinner = CertificatePinner.Builder()
                .add("codecloudex.dpdns.org", "sha256/${BuildConfig.CERT_PIN_PRIMARY}")
                .add("codecloudex.dpdns.org", "sha256/${BuildConfig.CERT_PIN_BACKUP}")
                .build()
            builder.certificatePinner(certificatePinner)
        }

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(NexChatLoggingInterceptor())
        }

        return builder.build()
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideUserApi(retrofit: Retrofit): UserApi = retrofit.create(UserApi::class.java)

    @Provides
    @Singleton
    fun provideContactApi(retrofit: Retrofit): ContactApi = retrofit.create(ContactApi::class.java)

    @Provides
    @Singleton
    fun provideChatApi(retrofit: Retrofit): ChatApi = retrofit.create(ChatApi::class.java)

    @Provides
    @Singleton
    fun provideGroupApi(retrofit: Retrofit): GroupApi = retrofit.create(GroupApi::class.java)

    @Provides
    @Singleton
    fun provideMediaApi(retrofit: Retrofit): MediaApi = retrofit.create(MediaApi::class.java)

    @Provides
    @Singleton
    fun provideKeysApi(retrofit: Retrofit): KeysApi = retrofit.create(KeysApi::class.java)
}
