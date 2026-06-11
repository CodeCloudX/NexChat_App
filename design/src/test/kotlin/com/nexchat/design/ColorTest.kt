package com.nexchat.design

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ColorTest {

    @Test
    fun `TestAccentColor_ExactlyEight`() {
        assertThat(AccentColor.entries).hasSize(8)
    }

    @Test
    fun `TestNexChatTeal_CorrectHex`() {
        // Verify via the AccentColor enum's hex string — a stable, semantically meaningful contract
        // that doesn't depend on Compose's internal ULong colorspace encoding.
        assertThat(AccentColor.TEAL.hex).isEqualTo("#0F766E")
        // Verify the Color object matches the canonical brand definition.
        assertThat(NexChatTeal).isEqualTo(Color(0xFF0F766E))
    }

    @Test
    fun `TestAccentColor_AllHexValuesNonEmpty`() {
        AccentColor.entries.forEach { accent ->
            assertThat(accent.hex).isNotEmpty()
            assertThat(accent.hex).startsWith("#")
        }
    }
}
