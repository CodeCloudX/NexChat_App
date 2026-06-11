package com.nexchat.feature.chat.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import com.nexchat.core.ui.model.MessageStatus
import com.nexchat.core.ui.model.MessageUiModel
import com.nexchat.design.NexChatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MessageBubbleTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val baseMessage = MessageUiModel(
        id = UUID.randomUUID().toString(),
        content = "Hello world",
        type = "text",
        status = MessageStatus.READ,
        createdAt = System.currentTimeMillis(),
        isMine = true,
        reactions = emptyList(),
        isDeleted = false,
        isSystemMessage = false
    )

    @Test
    fun TestMessageBubble_Text_IsDisplayed() {
        composeTestRule.setContent {
            NexChatTheme {
                MessageBubble(
                    message = baseMessage,
                    onSwipeToReply = {},
                    onLongPress = {},
                    onReactionTap = {},
                    onNavigateToMedia = {},
                    onViewSafetyNumber = {}
                )
            }
        }
        composeTestRule.onNodeWithText("Hello world").assertIsDisplayed()
    }

    @Test
    fun TestMessageBubble_SystemMessage_DelegatesToSystemMessageComponent() {
        val systemMsg = baseMessage.copy(isSystemMessage = true, content = "Encryption enabled")
        composeTestRule.setContent {
            NexChatTheme {
                MessageBubble(
                    message = systemMsg,
                    onSwipeToReply = {},
                    onLongPress = {},
                    onReactionTap = {},
                    onNavigateToMedia = {},
                    onViewSafetyNumber = {}
                )
            }
        }
        composeTestRule.onNodeWithText("Encryption enabled").assertIsDisplayed()
    }

    @Test
    fun TestMessageBubble_Deleted_ShowsPlaceholder() {
        val deletedMsg = baseMessage.copy(isDeleted = true)
        composeTestRule.setContent {
            NexChatTheme {
                MessageBubble(
                    message = deletedMsg,
                    onSwipeToReply = {},
                    onLongPress = {},
                    onReactionTap = {},
                    onNavigateToMedia = {},
                    onViewSafetyNumber = {}
                )
            }
        }
        composeTestRule.onNodeWithText("🚫 This message was deleted").assertIsDisplayed()
    }
}
