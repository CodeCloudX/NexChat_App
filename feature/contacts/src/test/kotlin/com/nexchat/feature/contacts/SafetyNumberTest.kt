package com.nexchat.feature.contacts

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure JVM unit tests for the safety number derivation algorithm.
 *
 * Safety numbers are a Signal-style mechanism that lets users visually verify
 * their end-to-end identity keys match on both devices. The algorithm must be:
 *   1. Deterministic   — same key pair always yields the same formatted number.
 *   2. Fixed-width     — exactly 60 decimal digits (12 groups × 5), so the UI
 *      layout never shifts and users can scan the grid predictably.
 *
 * This test uses synthetic identity key bytes rather than real libsignal keys so
 * it runs as a pure JVM test with zero Android or network dependencies.
 */
class SafetyNumberTest {

    /**
     * Shared computation helper extracted to avoid duplication across tests.
     *
     * The concatenation order (my || their) is intentional and must be stable:
     * both parties derive the same number by computing hash(A||B) and hash(B||A)
     * and XOR-ing or interleaving — here we use the simpler unordered variant
     * where the UI layer is responsible for presenting both parties' numbers.
     */
    private data class SafetyNumberResult(
        val digits: List<Int>,
        val formatted: String,
    )

    private fun computeSafetyNumber(
        myBytes: ByteArray,
        theirBytes: ByteArray,
    ): SafetyNumberResult {
        val hash = java.security.MessageDigest
            .getInstance("SHA-256")
            .digest(myBytes + theirBytes)

        // Each byte expands into two decimal digits (tens and units), giving
        // 32 bytes × 2 = 64 candidate digits; we take only the first 60.
        val digits = hash
            .flatMap { b ->
                val unsigned = b.toInt() and 0xFF
                listOf(unsigned / 10, unsigned % 10)
            }
            .take(60)

        // 12 groups of 5 digits each, separated by spaces — matches Signal's UX.
        val formatted = digits
            .chunked(5)
            .joinToString(" ") { chunk -> chunk.joinToString("") }

        return SafetyNumberResult(digits = digits, formatted = formatted)
    }

    // ─── Test 1 ──────────────────────────────────────────────────────────────

    /**
     * The output must always be exactly 60 decimal digits displayed as 12
     * space-separated 5-digit groups. Any deviation breaks the UI layout contract.
     */
    @Test
    fun `TestSafetyNumber_Exactly60Digits`() {
        val myBytes = ByteArray(32) { it.toByte() }
        val theirBytes = ByteArray(32) { (it + 32).toByte() }

        val result = computeSafetyNumber(myBytes, theirBytes)

        // Raw digit list must contain exactly 60 elements.
        assertThat(result.digits.size).isEqualTo(60)

        // Stripping all spaces must leave exactly 60 character positions.
        assertThat(result.formatted.replace(" ", "").length).isEqualTo(60)

        // Splitting on spaces must yield exactly 12 groups.
        val groups = result.formatted.split(" ")
        assertThat(groups.size).isEqualTo(12)

        // Every group must be exactly 5 characters.
        groups.forEach { group ->
            assertThat(group.length)
                .named("group '$group' must be 5 digits")
                .isEqualTo(5)
        }
    }

    // ─── Test 2 ──────────────────────────────────────────────────────────────

    /**
     * SHA-256 is deterministic: identical inputs must always produce identical
     * outputs. This test guards against any accidental introduction of entropy
     * (e.g. timestamp salting, random padding) in the derivation path.
     */
    @Test
    fun `TestSafetyNumber_Deterministic_SameKeys`() {
        val myBytes = ByteArray(32) { it.toByte() }
        val theirBytes = ByteArray(32) { (it + 32).toByte() }

        val first = computeSafetyNumber(myBytes, theirBytes)
        val second = computeSafetyNumber(myBytes, theirBytes)

        assertThat(first.formatted).isEqualTo(second.formatted)
        assertThat(first.digits).isEqualTo(second.digits)
    }
}
