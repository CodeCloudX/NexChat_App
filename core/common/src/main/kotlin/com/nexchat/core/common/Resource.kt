package com.nexchat.core.common

sealed class Resource<out T> {
    data class Success<out T>(val data: T) : Resource<T>()
    data class Error(val exception: Exception, val message: String? = exception.message) : Resource<Nothing>()
    object Loading : Resource<Nothing>()
}
