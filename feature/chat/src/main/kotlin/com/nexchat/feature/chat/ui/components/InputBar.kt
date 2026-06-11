package com.nexchat.feature.chat.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.nexchat.design.BottomSheetShape
import com.nexchat.design.InputFieldShape
import com.nexchat.design.InputBarSlide
import com.nexchat.design.NexChatTeal
import com.nexchat.feature.chat.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectTapGestures

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputBar(
    inputText: String,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasText = inputText.isNotBlank()
    var showEmojiSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            IconButton(onClick = {
                scope.launch {
                    if (showEmojiSheet) sheetState.hide() else sheetState.show()
                    showEmojiSheet = !showEmojiSheet
                }
            }) {
                Icon(Icons.Default.SentimentSatisfiedAlt, contentDescription = "Emoji", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChanged,
                modifier = Modifier.weight(1f),
                maxLines = 5,
                shape = InputFieldShape,
                placeholder = { Text("Message", style = MaterialTheme.typography.bodyMedium) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NexChatTeal,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                ),
            )

            Spacer(Modifier.width(6.dp))

            // Animated transition between send and mic+attach+camera icons with 200ms spring.
            AnimatedContent(
                targetState = hasText,
                transitionSpec = { fadeIn(tween(InputBarSlide)) togetherWith fadeOut(tween(InputBarSlide)) },
                label = "InputBarActions",
            ) { typing ->
                if (typing) {
                    FilledIconButton(
                        onClick = onSend,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = NexChatTeal),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", modifier = Modifier.size(18.dp))
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { /* launch file picker */ }) {
                            Icon(Icons.Default.AttachFile, contentDescription = "Attach", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { /* launch camera */ }) {
                            Icon(Icons.Default.Camera, contentDescription = "Camera", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box(
                            modifier = Modifier.pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = { onStartRecording() },
                                    onPress = {
                                        tryAwaitRelease()
                                        onStopRecording()
                                    },
                                )
                            },
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "Record", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }

    if (showEmojiSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEmojiSheet = false },
            sheetState = sheetState,
            shape = BottomSheetShape,
        ) {
            var emojiTab by remember { mutableStateOf(0) }
            PrimaryTabRow(selectedTabIndex = emojiTab) {
                listOf("Emoji", "Stickers", "GIFs").forEachIndexed { i, title ->
                    Tab(selected = emojiTab == i, onClick = { emojiTab = i }, text = { Text(title) })
                }
            }
            Box(modifier = Modifier.padding(16.dp)) {
                when (emojiTab) {
                    0 -> Text("Emoji grid — Phase 2", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    1 -> Text("Sticker grid — Phase 2", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    2 -> Text("GIF grid — Phase 2", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
