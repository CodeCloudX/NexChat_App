package com.nexchat.core.network.websocket

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import timber.log.Timber
import javax.inject.Inject

class UnknownEventException(val eventType: String) : Exception("Unknown WebSocket event: $eventType")

@Serializable
data class WsEnvelope(
    @SerialName("event") val event: String,
    @SerialName("payload") val payload: ByteArray? = null
)

@OptIn(ExperimentalSerializationApi::class)
class CborParser @Inject constructor() {
    val cbor = Cbor {
        ignoreUnknownKeys = true
    }

    /**
     * Encodes an outbound event envelope.
     */
    inline fun <reified T> encode(event: String, payload: T): ByteArray {
        val payloadBytes = cbor.encodeToByteArray(payload)
        val envelope = WsEnvelope(event = event, payload = payloadBytes)
        return cbor.encodeToByteArray(envelope)
    }

    /**
     * Encodes an outbound event envelope with no payload.
     */
    fun encodeEmpty(event: String): ByteArray {
        val envelope = WsEnvelope(event = event, payload = null)
        return cbor.encodeToByteArray(envelope)
    }

    /**
     * Decodes an incoming binary WebSocket frame into a strongly-typed WsEvent.
     */
    fun decode(data: ByteArray): WsEvent {
        val envelope = cbor.decodeFromByteArray<WsEnvelope>(data)
        val p = envelope.payload ?: ByteArray(0)

        return try {
            when (envelope.event) {
                "message:receive" -> cbor.decodeFromByteArray<WsEvent.MessageReceive>(p)
                "message:ack:update" -> cbor.decodeFromByteArray<WsEvent.MessageAckUpdate>(p)
                "message:delete" -> cbor.decodeFromByteArray<WsEvent.MessageDelete>(p)
                "reaction:update" -> cbor.decodeFromByteArray<WsEvent.ReactionUpdate>(p)
                "typing:update" -> cbor.decodeFromByteArray<WsEvent.TypingUpdate>(p)
                "user:online" -> cbor.decodeFromByteArray<WsEvent.UserOnline>(p)
                "user:offline" -> cbor.decodeFromByteArray<WsEvent.UserOffline>(p)
                "sync:response" -> cbor.decodeFromByteArray<WsEvent.SyncResponse>(p)
                "key:updated" -> cbor.decodeFromByteArray<WsEvent.KeyUpdated>(p)
                "keys:replenish_otk" -> WsEvent.KeysReplenishOtk
                "degradation:update" -> cbor.decodeFromByteArray<WsEvent.DegradationUpdate>(p)
                else -> throw UnknownEventException(envelope.event)
            }
        } catch (e: Exception) {
            if (e !is UnknownEventException) {
                Timber.e(e, "Failed to decode payload for event: ${envelope.event}")
            }
            throw e
        }
    }
}
