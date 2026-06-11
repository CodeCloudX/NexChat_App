package com.nexchat.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EventTest {

    @Test
    fun `TestEvent_getIfNotHandled_secondCallReturnsNull`() {
        val event = Event("Payload")
        
        // First call should return the content
        val firstResult = event.getContentIfNotHandled()
        assertThat(firstResult).isEqualTo("Payload")
        assertThat(event.hasBeenHandled).isTrue()
        
        // Second call should return null
        val secondResult = event.getContentIfNotHandled()
        assertThat(secondResult).isNull()
    }
}
