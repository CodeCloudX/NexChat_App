package com.nexchat.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NexChatNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    companion object {
        const val CHANNEL_ID_MESSAGES = "nexchat_messages"
        const val CHANNEL_ID_CALLS = "nexchat_calls"
        const val NOTIFICATION_ID_BASE = 1000
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val messagesChannel = NotificationChannel(
                CHANNEL_ID_MESSAGES,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "New message notifications"
                enableLights(true)
                enableVibration(true)
            }

            val callsChannel = NotificationChannel(
                CHANNEL_ID_CALLS,
                "Calls",
                NotificationManager.IMPORTANCE_MAX
            ).apply {
                description = "Incoming call notifications"
                enableLights(true)
                enableVibration(true)
            }

            notificationManager.createNotificationChannels(listOf(messagesChannel, callsChannel))
        }
    }

    fun showMessageNotification(
        chatId: String,
        senderName: String,
        messageText: String,
        timestamp: Long,
        isGroup: Boolean = false,
        groupName: String? = null
    ) {
        // Construct the intent to open the chat
        val intent = Intent(Intent.ACTION_VIEW).apply {
            // Use deep link to navigate to specific chat
            data = android.net.Uri.parse("nexchat://chat/$chatId")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            chatId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val person = Person.Builder()
            .setName(senderName)
            .setKey(senderName)
            .build()

        val style = NotificationCompat.MessagingStyle(Person.Builder().setName("Me").build())
        
        if (isGroup && groupName != null) {
            style.conversationTitle = groupName
            style.isGroupConversation = true
        }

        style.addMessage(messageText, timestamp, person)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_email) // Fallback icon, replace with actual app icon later
            .setStyle(style)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        notificationManager.notify(chatId.hashCode(), notification)
    }

    fun cancelNotification(chatId: String) {
        notificationManager.cancel(chatId.hashCode())
    }
}
