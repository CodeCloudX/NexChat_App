package com.nexchat.core.auth

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LogoutEventBusTest {

    @Test
    fun `TestLogoutEventBus_emit_received`() = runTest {
        val bus = LogoutEventBus()
        var eventReceived = false

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            bus.events.first() // Wait for the first event
            eventReceived = true
        }

        bus.triggerLogout()
        
        assertThat(eventReceived).isTrue()
        job.cancel()
    }
}
