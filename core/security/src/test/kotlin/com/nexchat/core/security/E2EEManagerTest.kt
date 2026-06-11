package com.nexchat.core.security

import com.google.common.truth.Truth.assertThat
import com.nexchat.core.common.AppDispatchers
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.state.SignalProtocolStore

class E2EEManagerTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = StandardTestDispatcher(testScheduler)
    private val dispatchers = AppDispatchers(testDispatcher, testDispatcher, testDispatcher)

    private lateinit var protocolStore: SignalProtocolStoreImpl
    private lateinit var e2eeManager: E2EEManager

    @Before
    fun setup() {
        protocolStore = mockk(relaxed = true)
        e2eeManager = E2EEManager(protocolStore, dispatchers)
    }

    @Test
    fun `generateIdentityKeyPair returns a valid key pair and saves it`() = runTest(testDispatcher) {
        val result = e2eeManager.generateIdentityKeyPair()

        assertThat(result.isSuccess).isTrue()
        val keyPair = result.getOrThrow()
        assertThat(keyPair.publicKey).isNotNull()
        assertThat(keyPair.privateKey).isNotNull()
        assertThat(keyPair.publicKey.serialize()).hasLength(33)
    }

    @Test
    fun `generateOneTimePreKeys returns exactly the requested count`() = runTest(testDispatcher) {
        val result = e2eeManager.generateOneTimePreKeys(startId = 1, count = 10)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow()).hasSize(10)
    }

    @Test
    fun `generateOneTimePreKeys assigns sequential IDs starting at startId`() = runTest(testDispatcher) {
        val result = e2eeManager.generateOneTimePreKeys(startId = 5, count = 3)

        val ids = result.getOrThrow().map { it.id }
        assertThat(ids).containsExactly(5, 6, 7).inOrder()
    }

    @Test
    fun `encryptMessage fails gracefully when no session exists`() = runTest(testDispatcher) {
        every { protocolStore.loadSession(any()) } throws Exception("No session")

        val result = e2eeManager.encryptMessage("recipient123", 1, "hello")
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `getIdentityKeyPair propagates store exception as failure`() = runTest(testDispatcher) {
        every { protocolStore.getIdentityKeyPair() } throws IllegalStateException("Not initialized")

        val result = e2eeManager.getIdentityKeyPair()
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
    }
}
