package com.nexchat.feature.contacts.repository

import com.nexchat.core.common.AppDispatchers
import com.nexchat.core.db.dao.ContactDao
import com.nexchat.core.db.entity.ContactEntity
import com.nexchat.core.network.api.ContactApi
import com.nexchat.core.network.api.UserApi
import com.nexchat.core.network.dto.PublicUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactsRepository @Inject constructor(
    private val contactDao: ContactDao,
    private val contactApi: ContactApi,
    private val userApi: UserApi,
    private val dispatchers: AppDispatchers,
) {

    fun getAllContacts(): Flow<List<ContactEntity>> = contactDao.getAllContacts()

    suspend fun searchLocal(query: String): List<ContactEntity> =
        withContext(dispatchers.io) {
            contactDao.searchLocal(query)
        }

    /**
     * Privacy-preserving server search: only fires an API call when the query is
     * structurally valid as a phone number (all-digits, ≥10 chars) or an email
     * address (contains '@' with non-empty local and domain parts). Everything else
     * short-circuits to null so we never leak partial user input to the backend.
     */
    suspend fun searchServer(q: String): Result<PublicUser?> {
        val trimmed = q.trim()
        val isEmail = trimmed.contains('@') && trimmed.substringAfter('@').isNotEmpty()
        val isPhone = trimmed.all { it.isDigit() } && trimmed.length >= 10
        if (!isEmail && !isPhone) return Result.success(null)

        return withContext(dispatchers.io) {
            try {
                val response = contactApi.search(trimmed)
                if (!response.isSuccessful) {
                    Timber.e("searchServer: HTTP ${response.code()} for query redacted")
                    return@withContext Result.success(null)
                }
                Result.success(response.body()?.firstOrNull())
            } catch (e: Exception) {
                Timber.e(e, "searchServer: network failure")
                Result.failure(e)
            }
        }
    }

    /**
     * Purely local — no API call. The caller is responsible for supplying a
     * validated [serverUser] obtained from [searchServer] before calling this.
     */
    suspend fun addContact(
        userId: String,
        firstName: String,
        lastName: String?,
        serverUser: PublicUser,
    ): Result<Unit> = withContext(dispatchers.io) {
        try {
            val entity = ContactEntity(
                id = userId,
                phone = serverUser.phone,
                email = serverUser.email,
                displayName = serverUser.displayName ?: userId,
                firstName = firstName.trim().takeIf { it.isNotEmpty() },
                lastName = lastName?.trim()?.takeIf { it.isNotEmpty() },
                avatarUrl = serverUser.avatarUrl,
                lastSeen = serverUser.lastSeen,
                createdAt = System.currentTimeMillis(),
            )
            contactDao.insertOrReplace(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "addContact: userId=$userId")
            Result.failure(e)
        }
    }

    suspend fun dismissBanner(userId: String): Result<Unit> = withContext(dispatchers.io) {
        try {
            contactDao.dismissBanner(userId)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "dismissBanner: userId=$userId")
            Result.failure(e)
        }
    }

    suspend fun blockContact(userId: String): Result<Unit> = withContext(dispatchers.io) {
        try {
            contactDao.blockContact(userId)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "blockContact: userId=$userId")
            Result.failure(e)
        }
    }

    suspend fun updateAccentColor(userId: String, color: String): Result<Unit> =
        withContext(dispatchers.io) {
            try {
                contactDao.updateAccentColor(userId, color)
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "updateAccentColor: userId=$userId")
                Result.failure(e)
            }
        }

    suspend fun updateName(userId: String, firstName: String, lastName: String?): Result<Unit> =
        withContext(dispatchers.io) {
            try {
                contactDao.updateName(userId, firstName, lastName)
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "updateName: userId=$userId")
                Result.failure(e)
            }
        }

    /**
     * Refreshes the remote profile and upserts it locally.
     * Uses REPLACE strategy so locally-set firstName/lastName are overwritten only
     * for fields that the server owns (avatarUrl, displayName, lastSeen). We keep
     * the existing local entity and merge to avoid clobbering user-customized names.
     */
    suspend fun refreshProfile(userId: String): Result<PublicUser?> =
        withContext(dispatchers.io) {
            try {
                val response = userApi.getUserById(userId)
                if (!response.isSuccessful) {
                    Timber.e("refreshProfile: HTTP ${response.code()} for userId=$userId")
                    return@withContext Result.success(null)
                }
                val remote = response.body() ?: return@withContext Result.success(null)

                val existing = contactDao.getById(userId)
                val merged = if (existing != null) {
                    existing.copy(
                        displayName = remote.displayName ?: existing.displayName,
                        avatarUrl = remote.avatarUrl ?: existing.avatarUrl,
                        phone = remote.phone ?: existing.phone,
                        email = remote.email ?: existing.email,
                        lastSeen = remote.lastSeen ?: existing.lastSeen,
                    )
                } else {
                    ContactEntity(
                        id = remote.id,
                        phone = remote.phone,
                        email = remote.email,
                        displayName = remote.displayName ?: remote.id,
                        avatarUrl = remote.avatarUrl,
                        lastSeen = remote.lastSeen,
                        createdAt = System.currentTimeMillis(),
                    )
                }
                contactDao.insertOrReplace(merged)
                Result.success(remote)
            } catch (e: Exception) {
                Timber.e(e, "refreshProfile: userId=$userId")
                Result.failure(e)
            }
        }
}
