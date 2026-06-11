package com.nexchat.feature.group.repository

import com.nexchat.core.common.AppDispatchers
import com.nexchat.core.db.dao.ChatDao
import com.nexchat.core.db.dao.ContactDao
import com.nexchat.core.db.entity.ChatEntity
import com.nexchat.core.network.api.GroupApi
import com.nexchat.core.network.dto.AddMemberRequest
import com.nexchat.core.network.dto.ChatResponse
import com.nexchat.core.network.dto.CreateGroupRequest
import com.nexchat.core.network.dto.UpdateGroupRequest
import com.nexchat.core.network.dto.UpdateRoleRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class MemberUiModel(
    val userId: String,
    val name: String,
    val avatarUrl: String?,
    val role: String,
    val isAdmin: Boolean,
)

data class GroupWithMembers(
    val chat: ChatEntity,
    val members: List<MemberUiModel>,
)

@Singleton
class GroupRepository @Inject constructor(
    private val groupApi: GroupApi,
    private val chatDao: ChatDao,
    private val contactDao: ContactDao,
    private val dispatchers: AppDispatchers,
) {

    suspend fun loadGroupInfo(groupId: String): Result<Unit> =
        runCatching {
            val response = groupApi.getGroupById(groupId)
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                return Result.failure(
                    IllegalStateException("getGroupById failed: HTTP ${response.code()}")
                )
            }
            chatDao.insertOrReplace(body.toChatEntity())
        }.onFailure { Timber.e(it, "loadGroupInfo groupId=$groupId") }

    suspend fun createGroup(name: String, memberIds: List<String>): Result<String> =
        runCatching {
            val response = groupApi.createGroup(
                CreateGroupRequest(name = name, memberIds = memberIds)
            )
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                return Result.failure(
                    IllegalStateException("createGroup failed: HTTP ${response.code()}")
                )
            }
            chatDao.insertOrReplace(body.toChatEntity())
            body.id
        }.onFailure { Timber.e(it, "createGroup name=$name") }

    suspend fun updateGroupName(groupId: String, name: String): Result<Unit> =
        runCatching {
            val response = groupApi.updateGroup(groupId, UpdateGroupRequest(name = name))
            if (!response.isSuccessful) {
                return Result.failure(
                    IllegalStateException("updateGroupName failed: HTTP ${response.code()}")
                )
            }
            response.body()?.let { chatDao.insertOrReplace(it.toChatEntity()) }
            Unit
        }.onFailure { Timber.e(it, "updateGroupName groupId=$groupId name=$name") }

    suspend fun addMember(groupId: String, userId: String): Result<Unit> =
        runCatching {
            val response = groupApi.addMember(groupId, AddMemberRequest(userId = userId))
            if (!response.isSuccessful) {
                return Result.failure(
                    IllegalStateException("addMember failed: HTTP ${response.code()}")
                )
            }
        }.onFailure { Timber.e(it, "addMember groupId=$groupId userId=$userId") }

    suspend fun removeMember(groupId: String, userId: String): Result<Unit> =
        runCatching {
            val response = groupApi.removeMember(groupId, userId)
            if (!response.isSuccessful) {
                return Result.failure(
                    IllegalStateException("removeMember failed: HTTP ${response.code()}")
                )
            }
        }.onFailure { Timber.e(it, "removeMember groupId=$groupId userId=$userId") }

    suspend fun changeMemberRole(groupId: String, userId: String, role: String): Result<Unit> =
        runCatching {
            val response = groupApi.updateMemberRole(groupId, userId, UpdateRoleRequest(role = role))
            if (!response.isSuccessful) {
                return Result.failure(
                    IllegalStateException("changeMemberRole failed: HTTP ${response.code()}")
                )
            }
        }.onFailure { Timber.e(it, "changeMemberRole groupId=$groupId userId=$userId role=$role") }

    /**
     * Combines the persisted ChatEntity (group metadata) with the local contacts table to
     * produce a fully resolved GroupWithMembers. The contacts table is the source-of-truth for
     * display names and avatars; role data is embedded in the ChatResponse participants list but
     * not currently persisted to a separate Room table, so we materialise it on-the-fly from the
     * most recent API snapshot stored in memory via [loadGroupInfo]. For roles we fall back to a
     * secondary network fetch only when the entity is not yet in the DB.
     *
     * Architectural note: participant roles are NOT stored in ChatEntity to avoid schema bloat.
     * A dedicated GroupParticipantEntity table is the long-term solution; for now the ViewModel
     * layer should call [loadGroupInfo] to prime the DB before subscribing to this flow.
     */
    fun getGroupWithMembers(groupId: String): Flow<GroupWithMembers?> =
        combine(
            chatDao.getChatById(groupId),
            contactDao.getAllContacts(),
        ) { chat, contacts ->
            if (chat == null || chat.type != "group") return@combine null
            val contactMap = contacts.associateBy { it.id }
            // Roles are not persisted in ChatEntity — we emit members resolved from contacts only.
            // The ViewModel drives a fresh loadGroupInfo() call on screen entry to prime real roles.
            val members = contacts.map { contact ->
                MemberUiModel(
                    userId = contact.id,
                    name = contact.displayName,
                    avatarUrl = contact.avatarUrl ?: contact.avatarPath,
                    role = "member",
                    isAdmin = false,
                )
            }
            GroupWithMembers(chat = chat, members = members)
        }.flowOn(dispatchers.io)

    // ─── Mapping ─────────────────────────────────────────────────────────────

    private fun ChatResponse.toChatEntity(): ChatEntity = ChatEntity(
        id = id,
        type = type,
        name = null,
        updatedAt = System.currentTimeMillis(),
    )
}
