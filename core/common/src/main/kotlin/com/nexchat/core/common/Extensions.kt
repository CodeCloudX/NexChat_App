package com.nexchat.core.common

import kotlinx.coroutines.flow.MutableStateFlow

fun <T> MutableStateFlow<T>.updateIfChanged(newValue: T) {
    if (this.value != newValue) {
        this.value = newValue
    }
}
