package com.nexchat.feature.contacts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nexchat.feature.chat.repository.ChatRepository
import com.nexchat.feature.contacts.viewmodel.ContactsSideEffect
import com.nexchat.feature.contacts.viewmodel.ContactsViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexchat.core.common.AppDispatchers
import com.nexchat.core.common.Resource
import com.nexchat.core.db.dao.ContactDao
import com.nexchat.core.db.entity.ContactEntity
import com.nexchat.feature.contacts.repository.ContactsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

// Accent colors match WhatsApp-style palette, offered as personalisation per contact.
private val accentColors = listOf(
    Color(0xFF25D366),
    Color(0xFF128C7E),
    Color(0xFF075E54),
    Color(0xFF34B7F1),
    Color(0xFFECE5DD),
    Color(0xFFFFD700),
    Color(0xFFFF6B6B),
    Color(0xFFA29BFE),
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val contactDao: ContactDao,
    private val chatRepository: ChatRepository,
    private val contactsRepository: ContactsRepository,
    private val dispatchers: AppDispatchers,
) : ViewModel() {

    private val _isCreatingChat = MutableStateFlow(false)
    val isCreatingChat: StateFlow<Boolean> = _isCreatingChat.asStateFlow()

    fun getContactFlow(userId: String): Flow<ContactEntity?> = contactDao.getAllContacts()
        .map { list -> list.firstOrNull { it.id == userId } }

    fun createDirectChat(userId: String, onResult: (Resource<String>) -> Unit) {
        viewModelScope.launch(dispatchers.io) {
            _isCreatingChat.value = true
            val result = chatRepository.createDirectChat(userId)
            _isCreatingChat.value = false
            onResult(result)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileInfoScreen(
    userId: String,
    onNavigateToChat: (chatId: String) -> Unit,
    onBack: () -> Unit,
    contactsViewModel: ContactsViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel(),
) {
    val contact by profileViewModel.getContactFlow(userId)
        .collectAsStateWithLifecycle(initialValue = null)

    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var showEditNameSheet by remember { mutableStateOf(false) }
    var showBlockDialog by remember { mutableStateOf(false) }
    var editFirstName by rememberSaveable { mutableStateOf("") }
    var editLastName by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(userId) {
        contactsViewModel.refreshProfile(userId)
    }

    LaunchedEffect(Unit) {
        contactsViewModel.sideEffects.collect { effect ->
            when (effect) {
                is ContactsSideEffect.NavigateToChat -> onNavigateToChat(effect.chatId)
                is ContactsSideEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.msg)
            }
        }
    }

    // Pre-fill edit fields when contact loads
    LaunchedEffect(contact) {
        if (contact != null) {
            editFirstName = contact!!.firstName ?: ""
            editLastName = contact!!.lastName ?: ""
        }
    }

    val resolvedName = remember(contact) {
        val c = contact ?: return@remember "Loading…"
        val joined = listOfNotNull(c.firstName, c.lastName).joinToString(" ")
        joined.ifEmpty { c.displayName }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))

            AsyncImage(
                model = contact?.avatarUrl,
                contentDescription = "Avatar of $resolvedName",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = resolvedName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            if (contact != null && contact!!.displayName != resolvedName) {
                Text(
                    text = contact!!.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(4.dp))

            val identifier = contact?.phone ?: contact?.email ?: ""
            if (identifier.isNotEmpty()) {
                Text(
                    text = identifier,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))

            val isCreatingChat by profileViewModel.isCreatingChat.collectAsStateWithLifecycle()

            Button(
                onClick = {
                    profileViewModel.createDirectChat(userId) { result ->
                        when (result) {
                            is Resource.Success -> contactsViewModel.emitNavigateToChat(result.data)
                            is Resource.Error -> contactsViewModel.emitSnackbar("Could not open chat. Try again.")
                            Resource.Loading -> Unit
                        }
                    }
                },
                enabled = !isCreatingChat,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isCreatingChat) "Opening…" else "Message")
            }

            Spacer(Modifier.height(24.dp))

            // Chat accent color picker
            Text(
                text = "Chat Color",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val currentAccent = contact?.accentColor
                accentColors.forEach { color ->
                    val hexColor = "#%06X".format(color.value.toLong() and 0xFFFFFF)
                    val isSelected = currentAccent == hexColor
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(
                                if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                else Modifier
                            )
                            .clickable {
                                contactsViewModel.onUpdateAccentColor(userId, hexColor)
                            },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            TextButton(
                onClick = {
                    showEditNameSheet = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Edit Name")
            }

            TextButton(
                onClick = { showBlockDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Block", color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showEditNameSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEditNameSheet = false },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    "Edit Name",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = editFirstName,
                    onValueChange = { editFirstName = it },
                    label = { Text("First Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = editLastName,
                    onValueChange = { editLastName = it },
                    label = { Text("Last Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        contactsViewModel.onUpdateName(
                            userId = userId,
                            firstName = editFirstName.trim(),
                            lastName = editLastName.trim().ifEmpty { null },
                        )
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showEditNameSheet = false
                        }
                    },
                    enabled = editFirstName.trim().isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showBlockDialog) {
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            title = { Text("Block Contact?") },
            text = { Text("$resolvedName will no longer be able to message you.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        contactsViewModel.onBlockContact(userId)
                        showBlockDialog = false
                        onBack()
                    },
                ) {
                    Text("Block", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
