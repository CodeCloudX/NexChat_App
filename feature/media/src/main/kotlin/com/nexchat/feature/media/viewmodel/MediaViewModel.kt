package com.nexchat.feature.media.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexchat.core.common.AppDispatchers
import com.nexchat.core.db.dao.MediaDao
import com.nexchat.core.db.entity.MediaEntity
import com.nexchat.core.storage.FileManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

// ─── UI State ─────────────────────────────────────────────────────────────────

@Immutable
sealed interface MediaUiState {
    data object Loading : MediaUiState
    data class Success(val entity: MediaEntity?) : MediaUiState
    data class Error(val msg: String) : MediaUiState
}

// ─── Side Effects ──────────────────────────────────────────────────────────────

sealed interface MediaSideEffect {
    data object SavedToGallery : MediaSideEffect
    data class SaveError(val msg: String) : MediaSideEffect
}

// ─── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class MediaViewModel @Inject constructor(
    private val mediaDao: MediaDao,
    private val fileManager: FileManager,
    private val dispatchers: AppDispatchers,
) : ViewModel() {

    private val _state = MutableStateFlow<MediaUiState>(MediaUiState.Loading)
    val state: StateFlow<MediaUiState> = _state.asStateFlow()

    private val _sideEffects = MutableSharedFlow<MediaSideEffect>(extraBufferCapacity = 8)
    val sideEffects: SharedFlow<MediaSideEffect> = _sideEffects.asSharedFlow()

    /**
     * MediaDao.getByMessageId is a one-shot suspend query, not a Room Flow query.
     * We wrap it in a cold [flow] so callers get a reactive stream they can
     * collectAsStateWithLifecycle — and re-subscribe to trigger a fresh DB read
     * when the messageId changes (e.g. after a download updates localPath).
     */
    fun getMedia(messageId: String): Flow<MediaEntity?> = flow {
        try {
            val entity = withContext(dispatchers.io) {
                mediaDao.getByMessageId(messageId)
            }
            emit(entity)
        } catch (e: Exception) {
            Timber.e(e, "getMedia failed for messageId=$messageId")
            emit(null)
        }
    }

    fun loadMedia(messageId: String) {
        viewModelScope.launch(dispatchers.io) {
            _state.value = MediaUiState.Loading
            try {
                val entity = mediaDao.getByMessageId(messageId)
                _state.value = MediaUiState.Success(entity)
            } catch (e: Exception) {
                Timber.e(e, "loadMedia failed for messageId=$messageId")
                _state.value = MediaUiState.Error(e.message ?: "Failed to load media")
            }
        }
    }

    /**
     * Saves the media file at [localPath] to the device gallery via MediaStore.
     * We first resolve the entity to get both the path and mimeType, ensuring
     * we never attempt a gallery write with a null path (would produce a misleading
     * FileNotFound rather than a clear "not downloaded" error).
     */
    fun saveToGallery(messageId: String) {
        viewModelScope.launch(dispatchers.io) {
            try {
                val entity = mediaDao.getByMessageId(messageId)
                val path = entity?.localPath
                val mimeType = entity?.mimeType

                if (path == null) {
                    Timber.w("saveToGallery: no local file for messageId=$messageId")
                    _sideEffects.emit(MediaSideEffect.SaveError("File not downloaded yet"))
                    return@launch
                }

                if (mimeType == null) {
                    Timber.w("saveToGallery: missing mimeType for messageId=$messageId")
                    _sideEffects.emit(MediaSideEffect.SaveError("Unknown file type"))
                    return@launch
                }

                fileManager.saveToGallery(path, mimeType)
                    .onSuccess {
                        Timber.d("saveToGallery succeeded for $messageId")
                        _sideEffects.emit(MediaSideEffect.SavedToGallery)
                    }
                    .onFailure { e ->
                        Timber.e(e, "saveToGallery failed for $messageId")
                        _sideEffects.emit(MediaSideEffect.SaveError(e.message ?: "Save failed"))
                    }
            } catch (e: Exception) {
                Timber.e(e, "saveToGallery: unexpected error for $messageId")
                _sideEffects.emit(MediaSideEffect.SaveError(e.message ?: "Unknown error"))
            }
        }
    }
}
