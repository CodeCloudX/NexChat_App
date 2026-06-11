package com.nexchat.core.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber

class NexChatLoggingInterceptor : Interceptor {
    private val delegate = HttpLoggingInterceptor { message ->
        Timber.tag("OkHttp").d(message)
    }.apply { 
        level = HttpLoggingInterceptor.Level.BODY 
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        return delegate.intercept(chain)
    }
}
