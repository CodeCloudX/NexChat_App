package com.nexchat.core.common

import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for device identity across all auth flows.
 *
 * Injected into AuthRepository so every auth API (Google, Phone, Magic Link)
 * sends identical, correctly populated device fields — no repetition, no silent
 * fallbacks, no hardcoded strings scattered across call sites.
 *
 * FCM token is updated externally once Firebase delivers it (see FcmService).
 * It starts empty so auth can still succeed on devices where FCM is unavailable;
 * the backend treats fcm_token as optional.
 */
@Singleton
class DeviceInfoProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val androidId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
    }

    val deviceType: String = "android"

    @Volatile
    var fcmToken: String = ""
        private set

    fun updateFcmToken(token: String) {
        fcmToken = token
    }
}
