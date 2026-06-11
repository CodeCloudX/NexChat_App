package com.nexchat.core.auth

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenStorage @Inject constructor(
    private val prefs: SharedPreferences
) {
    fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .apply()
    }

    fun getAccessToken(): String? = prefs.getString("access_token", null)
    fun getRefreshToken(): String? = prefs.getString("refresh_token", null)

    fun saveIdentity(userId: String, deviceId: String) {
        prefs.edit()
            .putString("user_id", userId)
            .putString("device_id", deviceId)
            .apply()
    }

    fun getUserId(): String? = prefs.getString("user_id", null)
    fun getDeviceId(): String? = prefs.getString("device_id", null)

    fun saveRegistrationId(id: Int) {
        prefs.edit().putInt("registration_id", id).apply()
    }
    
    fun getRegistrationId(): Int = prefs.getInt("registration_id", 0)
    
    fun saveFcmToken(token: String) {
        prefs.edit().putString("fcm_token", token).apply()
    }
    
    fun getFcmToken(): String? = prefs.getString("fcm_token", null)
    
    fun saveBackupPassword(password: String) {
        // Normally this goes to EncryptedSharedPreferences (which we are using via Hilt module)
        prefs.edit().putString("backup_password", password).apply()
    }
    
    fun getBackupPassword(): String? = prefs.getString("backup_password", null)

    fun clear() {
        prefs.edit().clear().apply()
    }
}
