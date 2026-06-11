package com.nexchat.feature.chat.repository

import androidx.paging.PagingSource
import com.nexchat.core.common.AppDispatchers
import com.nexchat.core.common.Resource
import com.nexchat.core.db.dao.ChatDao
import com.nexchat.core.db.dao.ContactDao
import com.nexchat.core.db.dao.MessageDao
import com.nexchat.core.db.dao.ParticipantDao
import com.nexchat.core.db.entity.ChatEntity
import com.nexchat.core.db.entity.ContactEntity
import com.nexchat.core.db.entity.MessageEntity
import com.nexchat.core.db.entity.ParticipantEntity
import com.nexchat.core.network.api.ChatApi
import com.nexchat.core.network.api.UserApi
import com.nexchat.core.network.dto.CreateChatRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val participantDao: ParticipantDao,
    private val contactDao: ContactDao,
    private val chatApi: ChatApi,
    private val userApi: UserApi,
    private val dispatchers: AppDispatchers,
) {
    /** Reactive Room stream — always reflects the local truth, no network wait. */
    fun getChats(): Flow<List<ChatEntity>> = chatDao.getAllChats()

    /** Paging 3 source for individual chat message screens. */
    fun getMessagesPaged(chatId: String): PagingSource<Int, MessageEntity> =
        messageDao.getMessagesPaged(chatId)

    /**
     * Delta-sync: fetch lightweight server list → for each chat not yet locally
     * known, back-fill detail and participant profiles.
     *
     * Sequential for loop (not mapNotNull) so suspend calls are legal.
     */
    suspend fun syncChats(): Resource<Unit> = withContext(dispatchers.io) {
        runCatching {
            val listResp = chatApi.getChats()
            if (!listResp.isSuccessful) {
                Timber.e("syncChats: list HTTP ${listResp.code()}")
                return@runCatching
            }
            val serverChats = listResp.body()?.data ?: return@runCatching

            for (remote in serverChats) {
                val detailResp = chatApi.getChatById(remote.id)
                if (!detailResp.isSuccessful) {
                    Timber.w("syncChats: detail HTTP ${detailResp.code()} for ${remote.id}")
                    continue
                }
                val detail = detailResp.body()?.data ?: continue

                chatDao.insertOrReplace(
                    ChatEntity(
                        id = detail.id,
                        type = detail.type,
                        updatedAt = System.currentTimeMillis(),
                    )
                )

                for (pInfo in detail.participants) {
                    // Back-fill user profile only if not cached.
                    if (contactDao.getById(pInfo.userId) == null) {
                        val userResp = userApi.getUserById(pInfo.userId)
                        if (userResp.isSuccessful) {
                            val u = userResp.body()!!
                            contactDao.insertOrReplace(
                                ContactEntity(
                                    id = u.id,
                                    displayName = u.displayName ?: u.id,
                                    avatarUrl = u.avatarUrl,
                                    createdAt = System.currentTimeMillis(),
                                )
                            )
                        }
                    }
                    participantDao.insertOrReplace(
                        ParticipantEntity(
                            chatId = detail.id,
                            userId = pInfo.userId,
                            role = pInfo.role,
                            joinedAt = System.currentTimeMillis(),
                        )
                    )
                }
            }
        }.fold(
            onSuccess = { Resource.Success(Unit) },
            onFailure = { e ->
                Timber.e(e, "syncChats: failed")
                Resource.Error(Exception(e))
            }
        )
    }

    /**
     * Creates a direct chat and upserts the result into Room.
     * Returns the chat ID. Idempotent — server returns 200 if already exists.
     */
    suspend fun createDirectChat(participantId: String, isSelf: Boolean = false): Resource<String> =
        withContext(dispatchers.io) {
            runCatching {
                val type = if (isSelf) "self" else "direct"
                val resp = chatApi.createChat(
                    CreateChatRequest(participantIds = listOf(participantId), type = type)
                )
                check(resp.isSuccessful) { "createChat HTTP ${resp.code()}" }
                val body = checkNotNull(resp.body()?.data) { "createChat: empty body" }
                chatDao.insertOrReplace(
                    ChatEntity(id = body.id, type = body.type, updatedAt = System.currentTimeMillis())
                )
                body.id
            }.fold(
                onSuccess = { Resource.Success(it) },
                onFailure = { e ->
                    Timber.e(e, "createDirectChat")
                    Resource.Error(Exception(e))
                }
            )
        }

    /**
     * Soft-deletes a message: server first, then mirrors state in Room.
     * If server fails, local DB is not touched — UI stays consistent.
     */
    suspend fun deleteMessage(chatId: String, msgId: String): Resource<Unit> =
        withContext(dispatchers.io) {
            runCatching {
                val resp = chatApi.deleteMessage(chatId, msgId)
                check(resp.isSuccessful) { "deleteMessage HTTP ${resp.code()}" }
                messageDao.softDelete(msgId)
            }.fold(
                onSuccess = { Resource.Success(Unit) },
                onFailure = { e ->
                    Timber.e(e, "deleteMessage chatId=$chatId msgId=$msgId")
                    Resource.Error(Exception(e))
                }
            )
        }
}
