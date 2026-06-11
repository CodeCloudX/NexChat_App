package com.nexchat.core.network

import com.google.common.truth.Truth.assertThat
import com.nexchat.core.network.websocket.CborParser
import com.nexchat.core.network.websocket.DeltaEntry
import com.nexchat.core.network.websocket.DevicePayload
import com.nexchat.core.network.websocket.UnknownEventException
import com.nexchat.core.network.websocket.WsEvent
import org.junit.Test
import java.util.UUID

class CborParserTest {

    private val parser = CborParser()

    @Test
    fun testEncodeDecode_MessageReceive_FieldsMatch() {
        // Arrange
        val originalEvent = WsEvent.MessageReceive(
            localId = UUID.randomUUID().toString(),
            chatId = UUID.randomUUID().toString(),
            senderId = UUID.randomUUID().toString(),
            type = "text",
            payloads = listOf(DevicePayload("device_1", "base64ciphertext=")),
            createdAt = 1700000000L,
            serverId = UUID.randomUUID().toString(),
            serverTs = 1700000005L
        )

        // Act - Encode using the inline reified function to simulate the server pushing to us
        val bytes = parser.encode("message:receive", originalEvent)
        
        // Act - Decode using our CborParser decode function
        val decodedEvent = parser.decode(bytes)

        // Assert
        assertThat(decodedEvent).isInstanceOf(WsEvent.MessageReceive::class.java)
        val msgReceive = decodedEvent as WsEvent.MessageReceive
        assertThat(msgReceive.localId).isEqualTo(originalEvent.localId)
        assertThat(msgReceive.payloads.first().ciphertext).isEqualTo("base64ciphertext=")
    }

    @Test
    fun testDecodeAllEventTypes() {
        // We will encode one of each type and verify decode doesn't crash and returns the correct class.
        
        val ackEvent = WsEvent.MessageAckUpdate("loc1", "srv1", 123L, "delivered")
        assertThat(parser.decode(parser.encode("message:ack:update", ackEvent)))
            .isInstanceOf(WsEvent.MessageAckUpdate::class.java)

        val deleteEvent = WsEvent.MessageDelete("msg1", "chat1")
        assertThat(parser.decode(parser.encode("message:delete", deleteEvent)))
            .isInstanceOf(WsEvent.MessageDelete::class.java)

        val typingEvent = WsEvent.TypingUpdate("user1", "chat1", true)
        assertThat(parser.decode(parser.encode("typing:update", typingEvent)))
            .isInstanceOf(WsEvent.TypingUpdate::class.java)

        val syncEvent = WsEvent.SyncResponse(listOf(DeltaEntry("message:receive", "blob=")))
        assertThat(parser.decode(parser.encode("sync:response", syncEvent)))
            .isInstanceOf(WsEvent.SyncResponse::class.java)
            
        // Parameterless object event
        val otkEventBytes = parser.encodeEmpty("keys:replenish_otk")
        assertThat(parser.decode(otkEventBytes))
            .isInstanceOf(WsEvent.KeysReplenishOtk::class.java)
    }

    @Test(expected = UnknownEventException::class)
    fun testDecode_UnknownEvent_ThrowsException() {
        val badBytes = parser.encodeEmpty("fake:event:that:does:not:exist")
        parser.decode(badBytes)
    }
}
