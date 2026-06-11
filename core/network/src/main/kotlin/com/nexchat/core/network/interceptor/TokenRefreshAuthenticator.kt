package com.nexchat.core.network.interceptor

import com.nexchat.core.auth.AuthManager
import com.nexchat.core.auth.TokenStorage
import com.nexchat.core.common.AppDispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import timber.log.Timber
import javax.inject.Inject

class TokenRefreshAuthenticator @Inject constructor(
    private val tokenStorage: TokenStorage,
    private val authManager: AuthManager,
    private val dispatchers: AppDispatchers
) : Authenticator {

    private val mutex = Mutex()

    // Dedicated client for token refresh only — avoids creating a new thread pool
    // and connection pool on every 401 response.
    private val refreshClient: OkHttpClient by lazy { OkHttpClient() }

    override fun authenticate(route: Route?, response: Response): Request? {
        return runBlocking(dispatchers.io) {
            withTimeoutOrNull(15_000L) {
                mutex.withLock {
                    val currentToken = tokenStorage.getAccessToken()
                    val requestToken = response.request.header("Authorization")
                        ?.removePrefix("Bearer ")

                    // Another thread already refreshed — reuse the new token.
                    if (currentToken != null && currentToken != requestToken) {
                        return@withLock response.request.newBuilder()
                            .header("Authorization", "Bearer $currentToken")
                            .build()
                    }

                    val refreshToken = tokenStorage.getRefreshToken()
                    if (refreshToken == null) {
                        Timber.w("No refresh token — clearing credentials and forcing logout.")
                        authManager.logout()
                        return@withLock null
                    }

                    try {
                        val refreshRequest = buildRefreshRequest(refreshToken)
                        // use{} guarantees the response body is ALWAYS closed,
                        // regardless of success, failure, or exception path.
                        val newAccessToken = refreshClient
                            .newCall(refreshRequest)
                            .execute()
                            .use { result ->
                                if (result.isSuccessful) {
                                    parseToken(result.body?.string() ?: "")
                                } else {
                                    Timber.e("Refresh call failed: HTTP ${result.code}")
                                    ""
                                }
                            }

                        if (newAccessToken.isNotEmpty()) {
                            tokenStorage.saveTokens(newAccessToken, refreshToken)
                            return@withLock response.request.newBuilder()
                                .header("Authorization", "Bearer $newAccessToken")
                                .build()
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Token refresh request failed")
                    }

                    Timber.w("Token refresh failed — clearing credentials and triggering logout.")
                    // Clear BEFORE emitting so that on Activity restart, checkInitialAuth
                    // finds no token and routes to login instead of re-entering as LoggedIn.
                    authManager.logout()
                    null
                }
            }
        }
    }

    private fun buildRefreshRequest(refreshToken: String): Request {
        val body = """{"refresh_token":"$refreshToken"}"""
            .toRequestBody("application/json".toMediaType())
        return Request.Builder()
            .url("${com.nexchat.core.network.BuildConfig.API_BASE_URL}auth/refresh")
            .post(body)
            .build()
    }

    // Backend /auth/refresh returns flat JSON: {"access_token":"..."}
    // NOT wrapped in {"data":{...}} — WriteJSONStatus bypasses the APIResponse envelope.
    private fun parseToken(jsonResponse: String): String {
        return try {
            Json.parseToJsonElement(jsonResponse)
                .jsonObject["access_token"]
                ?.jsonPrimitive?.content
                ?: ""
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse access token from refresh response")
            ""
        }
    }
}
