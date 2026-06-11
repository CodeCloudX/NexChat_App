package com.nexchat.feature.contacts.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexchat.core.auth.AuthManager
import com.nexchat.core.auth.AuthState
import com.nexchat.core.common.AppDispatchers
import com.nexchat.core.db.entity.ContactEntity
import com.nexchat.core.network.dto.PublicUser
import com.nexchat.feature.chat.repository.ChatRepository
import com.nexchat.feature.contacts.repository.ContactsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@Immutable
data class ContactUiModel(
    val id: String,
    val resolvedName: String,
    val avatarUrl: String?,
    val phone: String?,
    val email: String?,
    val isOnline: Boolean,
    val lastSeen: Long?,
)

@Immutable
data class ContactsUiState(
    val contacts: PersistentList<ContactUiModel> = persistentListOf(),
    val searchQuery: String = "",
    val serverResult: PublicUser? = null,
    val isServerResultSelf: Boolean = false,
    val isSearchingServer: Boolean = false,
    val localSearchResults: PersistentList<ContactUiModel> = persistentListOf(),
)

sealed class ContactsSideEffect {
    data class NavigateToChat(val chatId: String) : ContactsSideEffect()
    data class ShowSnackbar(val msg: String) : ContactsSideEffect()
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val contactsRepository: ContactsRepository,
    private val chatRepository: ChatRepository,
    private val authManager: AuthManager,
    private val dispatchers: AppDispatchers,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    private val _sideEffects = Channel<ContactsSideEffect>(Channel.BUFFERED)
    val sideEffects = _sideEffects.receiveAsFlow()

    // Extra buffer so fast typing never blocks the caller.
    private val searchFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)

    val allContacts: StateFlow<PersistentList<ContactUiModel>> =
        contactsRepository.getAllContacts()
            .map { entities -> entities.map { it.toUiModel() }.toPersistentList() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = persistentListOf(),
            )

    init {
        allContacts.onEach { list ->
            _uiState.update { it.copy(contacts = list) }
        }.launchIn(viewModelScope)

        // Adaptive debounce: emails wait longer (server regex costs more to validate),
        // phone numbers get a tighter window since they're deterministic length checks.
        searchFlow
            .debounce { q ->
                when {
                    q.contains('@') -> 800L
                    q.all { it.isDigit() } && q.length >= 10 -> 500L
                    else -> Long.MAX_VALUE
                }
            }
            .distinctUntilChanged()
            .flatMapLatest { q ->
                flow {
                    _uiState.update { it.copy(isSearchingServer = true) }
                    emit(contactsRepository.searchServer(q))
                }
            }
            .onEach { result ->
                result.fold(
                    onSuccess = { user ->
                        val myUserId = (authManager.authState.value as? AuthState.LoggedIn)?.userId
                        val isSelf = user != null && user.id == myUserId
                        _uiState.update { it.copy(serverResult = user, isServerResultSelf = isSelf, isSearchingServer = false) }
                    },
                    onFailure = { e ->
                        Timber.e(e, "Server search failed")
                        _uiState.update { it.copy(isSearchingServer = false) }
                        _sideEffects.send(ContactsSideEffect.ShowSnackbar("Search failed. Check your connection."))
                    },
                )
            }
            .launchIn(viewModelScope)
    }

    fun onSearch(q: String) {
        _uiState.update { it.copy(searchQuery = q, serverResult = null) }
        searchFlow.tryEmit(q)
        viewModelScope.launch(dispatchers.io) {
            val results = contactsRepository.searchLocal(q)
                .map { it.toUiModel() }
                .toPersistentList()
            _uiState.update { it.copy(localSearchResults = results) }
        }
    }

    fun onSearchCleared() {
        _uiState.update {
            it.copy(
                searchQuery = "",
                serverResult = null,
                isServerResultSelf = false,
                localSearchResults = persistentListOf(),
                isSearchingServer = false,
            )
        }
    }

    fun onStartChat(serverUser: PublicUser) {
        viewModelScope.launch {
            val myUserId = (authManager.authState.value as? AuthState.LoggedIn)?.userId
            val isSelf = serverUser.id == myUserId
            when (val result = chatRepository.createDirectChat(serverUser.id, isSelf)) {
                is com.nexchat.core.common.Resource.Success -> {
                    _sideEffects.send(ContactsSideEffect.NavigateToChat(result.data))
                }
                is com.nexchat.core.common.Resource.Error -> {
                    Timber.e(result.exception, "onStartChat")
                    _sideEffects.send(ContactsSideEffect.ShowSnackbar("Failed to start chat."))
                }
                else -> {}
            }
        }
    }

    fun onAddToContacts(serverUser: PublicUser, firstName: String, lastName: String?) {
        viewModelScope.launch {
            contactsRepository.addContact(
                userId = serverUser.id,
                firstName = firstName,
                lastName = lastName,
                serverUser = serverUser,
            ).onFailure { e ->
                Timber.e(e, "onAddToContacts")
                _sideEffects.send(ContactsSideEffect.ShowSnackbar("Failed to save contact."))
            }
        }
    }

    fun onBlockContact(userId: String) {
        viewModelScope.launch {
            contactsRepository.blockContact(userId).onFailure { e ->
                Timber.e(e, "onBlockContact")
                _sideEffects.send(ContactsSideEffect.ShowSnackbar("Failed to block contact."))
            }
        }
    }

    fun onDismissBanner(userId: String) {
        viewModelScope.launch {
            contactsRepository.dismissBanner(userId).onFailure { e ->
                Timber.e(e, "onDismissBanner")
            }
        }
    }

    fun onUpdateAccentColor(userId: String, color: String) {
        viewModelScope.launch {
            contactsRepository.updateAccentColor(userId, color).onFailure { e ->
                Timber.e(e, "onUpdateAccentColor")
                _sideEffects.send(ContactsSideEffect.ShowSnackbar("Failed to update color."))
            }
        }
    }

    fun onUpdateName(userId: String, firstName: String, lastName: String?) {
        viewModelScope.launch {
            contactsRepository.updateName(userId, firstName, lastName).onFailure { e ->
                Timber.e(e, "onUpdateName")
                _sideEffects.send(ContactsSideEffect.ShowSnackbar("Failed to update name."))
            }
        }
    }

    fun refreshProfile(userId: String) {
        viewModelScope.launch {
            contactsRepository.refreshProfile(userId).onFailure { e ->
                Timber.e(e, "refreshProfile")
            }
        }
    }

    fun emitNavigateToChat(chatId: String) {
        viewModelScope.launch {
            _sideEffects.send(ContactsSideEffect.NavigateToChat(chatId))
        }
    }

    fun emitSnackbar(msg: String) {
        viewModelScope.launch {
            _sideEffects.send(ContactsSideEffect.ShowSnackbar(msg))
        }
    }
}

private fun ContactEntity.toUiModel(): ContactUiModel {
    val resolved = when {
        !firstName.isNullOrBlank() || !lastName.isNullOrBlank() ->
            listOfNotNull(firstName, lastName).joinToString(" ")
        else -> displayName
    }
    // A contact is considered "online" if lastSeen is within the last 5 minutes.
    val ls = lastSeen
    val isOnline = ls != null && (System.currentTimeMillis() - ls) < 5 * 60 * 1_000L
    return ContactUiModel(
        id = id,
        resolvedName = resolved,
        avatarUrl = avatarUrl,
        phone = phone,
        email = email,
        isOnline = isOnline,
        lastSeen = lastSeen,
    )
}
