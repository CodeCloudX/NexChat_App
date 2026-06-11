package com.nexchat.core.db

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DatabaseKeyManagerTest {

    private val testIkm = ByteArray(32) { it.toByte() }
    private val testInfo = "nexchat_sqlcipher_v1".toByteArray(Charsets.UTF_8)

    @Test
    fun `TestPassphrase_Length`() {
        val result = hkdfDerive(ikm = testIkm, info = testInfo, outputLength = 32)
        assertThat(result).hasLength(32)
    }

    @Test
    fun `TestPassphrase_Deterministic`() {
        val first = hkdfDerive(ikm = testIkm, info = testInfo, outputLength = 32)
        val second = hkdfDerive(ikm = testIkm, info = testInfo, outputLength = 32)
        assertThat(first).isEqualTo(second)
    }

    @Test
    fun `TestPassphrase_DifferentIkm_ProducesDifferentOutput`() {
        val ikmA = ByteArray(32) { 0xAA.toByte() }
        val ikmB = ByteArray(32) { 0xBB.toByte() }
        val outA = hkdfDerive(ikm = ikmA, info = testInfo, outputLength = 32)
        val outB = hkdfDerive(ikm = ikmB, info = testInfo, outputLength = 32)
        assertThat(outA).isNotEqualTo(outB)
    }
}
