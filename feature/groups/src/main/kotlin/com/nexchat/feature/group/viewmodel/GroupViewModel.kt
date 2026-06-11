package com.nexchat.feature.group.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexchat.core.common.AppDispatchers
import com.nexchat.core.db.dao.ContactDao
import com.nexchat.feature.group.repository.GroupRepository
import com.nexchat.feature.group.repository.GroupWithMembers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@Immutable
data class ContactUiModel(
    val id: String,
    val resolvedName: String,
    val avatarUrl: String?
)

sealed class CreateGroupState {
    object Idle : CreateGroupState()
    object Loading : CreateGroupState()
    data class Success(val chatId: String) : CreateGroupState()
    data class Error(val msg: String) : CreateGroupState()
}

@Immutable
data class GroupUiState(
    val createState: CreateGroupState = CreateGroupState.Idle,
    val contacts: PersistentList<ContactUiModel> = persistentListOf(),
    val selectedIds: PersistentSet<String> = persistentSetOf(),
    val searchQuery: String = ""
)

sealed class GroupSideEffect {
    data class NavigateToChat(val chatId: String) : GroupSideEffect()
    data class ShowError(val msg: String) : GroupSideEffect()
}

@HiltViewModel
class GroupViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val contactDao: ContactDao,
    private val dispatchers: AppDispatchers
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupUiState())
    val uiState: StateFlow<GroupUiState> = _uiState.asStateFlow()

    private val _sideEffects = Channel<GroupSideEffect>(Channel.BUFFERED)
    val sideEffects = _sideEffects.receiveAsFlow()

    init {
        loadContacts()
    }

    fun loadContacts() {
        viewModelScope.launch(dispatchers.default) {
            contactDao.getAllContacts()
                .map { contacts ->
                    contacts.map {
                        val resolvedName = listOfNotNull(it.firstName, it.lastName)
                            .joinToString(" ")
                            .ifEmpty { it.displayName }
                        ContactUiModel(
                            id = it.id,
                            resolvedName = resolvedName,
                            avatarUrl = it.avatarUrl ?: it.avatarPath
                        )
                    }
                }
                .collect { uiModels ->
                    _uiState.update { it.copy(contacts = uiModels.toPersistentList()) }
                }
        }
    }

    fun onToggleMember(userId: String) {
        _uiState.update { state ->
            val current = state.selectedIds
            val next = if (current.contains(userId)) {
                current.remove(userId)
            } else {
                if (current.size < 100) current.add(userId) else current
            }
            state.copy(selectedIds = next)
        }
    }

    fun onSearchContacts(q: String) {
        _uiState.update { it.copy(searchQuery = q) }
    }

    fun createGroup(name: String) {
        val selected = _uiState.value.selectedIds.toList()
        if (selected.isEmpty()) return

        _uiState.update { it.copy(createState = CreateGroupState.Loading) }
        viewModelScope.launch(dispatchers.io) {
            val result = groupRepository.createGroup(name, selected)
            result.onSuccess { chatId ->
                _uiState.update { it.copy(createState = CreateGroupState.Success(chatId)) }
                _sideEffects.send(GroupSideEffect.NavigateToChat(chatId))
            }.onFailure { e ->
                _uiState.update { it.copy(createState = CreateGroupState.Error(e.message ?: "Failed")) }
                _sideEffects.send(GroupSideEffect.ShowError(e.message ?: "Failed to create group"))
            }
        }
    }

    fun groupInfo(groupId: String): Flow<GroupWithMembers?> =
        groupRepository.getGroupWithMembers(groupId).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun isCurrentUserAdmin(groupId: String, myUserId: String): Flow<Boolean> =
        groupInfo(groupId).map { groupInfo ->
            groupInfo?.members?.any { it.userId == myUserId && it.isAdmin } == true
        }

    fun removeMember(groupId: String, userId: String) {
        viewModelScope.launch(dispatchers.io) {
            val result = groupRepository.removeMember(groupId, userId)
            if (result.isFailure) {
                _sideEffects.send(GroupSideEffect.ShowError("Failed to remove member"))
            }
        }
    }

    fun promoteMember(groupId: String, userId: String) {
        viewModelScope.launch(dispatchers.io) {
            val result = groupRepository.changeMemberRole(groupId, userId, "admin")
            if (result.isFailure) {
                _sideEffects.send(GroupSideEffect.ShowError("Failed to promote member"))
            }
        }
    }

    fun exitGroup(groupId: String, myUserId: String) {
        viewModelScope.launch(dispatchers.io) {
            val result = groupRepository.removeMember(groupId, myUserId)
            if (result.isSuccess) {
                _sideEffects.send(GroupSideEffect.NavigateToChat("")) // signals back
            } else {
                _sideEffects.send(GroupSideEffect.ShowError("Failed to exit group"))
            }
        }
    }
}
