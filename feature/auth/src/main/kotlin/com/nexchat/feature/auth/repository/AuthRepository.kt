package com.nexchat.feature.auth.repository

import android.util.Base64
import com.nexchat.core.auth.AuthManager
import com.nexchat.core.auth.TokenStorage
import com.nexchat.core.common.DeviceInfoProvider
import com.nexchat.core.common.Resource
import com.nexchat.core.network.api.AuthApi
import com.nexchat.core.network.dto.GoogleAuthRequest
import com.nexchat.core.network.dto.MagicLinkRequest
import com.nexchat.core.network.dto.MagicLinkVerifyRequest
import com.nexchat.core.network.dto.PhoneAuthRequest
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val authManager: AuthManager,
    private val tokenStorage: TokenStorage,
    private val device: DeviceInfoProvider
) {
    // ── Magic link ────────────────────────────────────────────────────────────

    suspend fun sendMagicLink(email: String): Resource<Unit> = try {
        val response = authApi.sendMagicLink(MagicLinkRequest(email))
        if (response.isSuccessful) {
            Resource.Success(Unit)
        } else {
            val msg = parseErrorMessage(response.errorBody()?.string())
            Resource.Error(Exception(msg), msg)
        }
    } catch (e: Exception) {
        Timber.e(e, "sendMagicLink failed")
        Resource.Error(e, "Network error — check your connection")
    }

    suspend fun verifyMagicLink(token: String): Resource<AuthResult> = try {
        val response = authApi.verifyMagicLink(
            MagicLinkVerifyRequest(
                token      = token,
                deviceType = device.deviceType,
                androidId  = device.androidId,
                fcmToken   = device.fcmToken
            )
        )
        val body = response.body()
        if (response.isSuccessful && body != null) {
            persistSession(body.accessToken, body.refreshToken)
            Resource.Success(AuthResult(isNewUser = body.isNewUser))
        } else {
            val msg = parseErrorMessage(response.errorBody()?.string())
            Resource.Error(Exception(msg), msg)
        }
    } catch (e: Exception) {
        Timber.e(e, "verifyMagicLink failed")
        Resource.Error(e, "Network error — check your connection")
    }

    // ── Google ────────────────────────────────────────────────────────────────

    suspend fun googleAuth(firebaseIdToken: String): Resource<AuthResult> = try {
        val response = authApi.googleAuth(
            GoogleAuthRequest(
                firebaseToken = firebaseIdToken,
                deviceType    = device.deviceType,
                androidId     = device.androidId,
                fcmToken      = device.fcmToken
            )
        )
        val body = response.body()
        if (response.isSuccessful && body != null) {
            persistSession(body.accessToken, body.refreshToken)
            Resource.Success(AuthResult(isNewUser = body.isNewUser))
        } else {
            val msg = parseErrorMessage(response.errorBody()?.string())
            Resource.Error(Exception(msg), msg)
        }
    } catch (e: Exception) {
        Timber.e(e, "googleAuth failed")
        Resource.Error(e, "Network error — check your connection")
    }

    // ── Phone ─────────────────────────────────────────────────────────────────

    suspend fun phoneAuth(firebaseIdToken: String): Resource<AuthResult> = try {
        val response = authApi.phoneAuth(
            PhoneAuthRequest(
                firebaseIdToken = firebaseIdToken,
                deviceType      = device.deviceType,
                androidId       = device.androidId,
                fcmToken        = device.fcmToken
            )
        )
        val body = response.body()
        if (response.isSuccessful && body != null) {
            persistSession(body.accessToken, body.refreshToken)
            Resource.Success(AuthResult(isNewUser = body.isNewUser))
        } else {
            val msg = parseErrorMessage(response.errorBody()?.string())
            Resource.Error(Exception(msg), msg)
        }
    } catch (e: Exception) {
        Timber.e(e, "phoneAuth failed")
        Resource.Error(e, "Network error — check your connection")
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    suspend fun logout(): Resource<Unit> = try {
        authApi.logout()
        authManager.logout()
        Resource.Success(Unit)
    } catch (e: Exception) {
        Timber.e(e, "logout network call failed — clearing local session anyway")
        authManager.logout()
        Resource.Success(Unit)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun persistSession(accessToken: String, refreshToken: String) {
        val (userId, deviceId) = decodeJwt(accessToken)
        tokenStorage.saveTokens(accessToken, refreshToken)
        tokenStorage.saveIdentity(userId, deviceId)
        authManager.login(userId, deviceId, accessToken, refreshToken)
    }

    private fun parseErrorMessage(body: String?): String {
        if (body.isNullOrBlank()) return "Something went wrong — please try again"
        return try {
            val json = JSONObject(body)
            json.optString("message").takeIf { it.isNotBlank() }
                ?: json.optString("error").takeIf { it.isNotBlank() }
                ?: "Something went wrong — please try again"
        } catch (e: Exception) {
            "Something went wrong — please try again"
        }
    }

    private fun decodeJwt(token: String): Pair<String, String> {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return Pair("", "")
            val payload = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val json = JSONObject(String(payload, Charsets.UTF_8))
            Pair(json.optString("sub", ""), json.optString("device_id", ""))
        } catch (e: Exception) {
            Timber.e(e, "JWT decode failed")
            Pair("", "")
        }
    }

    data class AuthResult(val isNewUser: Boolean)
}
