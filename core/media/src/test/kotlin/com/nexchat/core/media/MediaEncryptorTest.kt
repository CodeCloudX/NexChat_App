package com.nexchat.core.media

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MediaEncryptorTest {

    private val encryptor = MediaEncryptor()

    @Test
    fun `encrypt and decrypt returns original data`() {
        val originalData = "test media content".toByteArray()
        val encryptedMedia = encryptor.encrypt(originalData)
        
        assertThat(encryptedMedia.encrypted).isNotEqualTo(originalData)
        assertThat(encryptedMedia.sha256Hex).isNotEmpty()

        val decryptedResult = encryptor.decrypt(encryptedMedia)
        assertThat(decryptedResult.isSuccess).isTrue()
        assertThat(decryptedResult.getOrNull()).isEqualTo(originalData)
    }
}
