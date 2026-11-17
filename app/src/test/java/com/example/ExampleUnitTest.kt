package com.example

import com.example.core.files.WebsiteLogoFetcher
import com.example.core.security.AesGcmEnvelopeCipher
import com.example.core.security.Argon2idKeyDeriver
import com.example.core.security.DatabaseKeyManager
import com.example.core.security.EnvelopeType
import com.example.core.security.KdfParameters
import com.example.core.security.RecoveryKeyManager
import com.example.core.security.SensitiveBytes
import com.example.domain.model.PasswordGenerator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class ExampleUnitTest {

    @Test
    fun aesGcmEnvelopeCipherRoundTripEncryptsAndDecrypts() {
        val cipher = AesGcmEnvelopeCipher()
        val random = SecureRandom()
        val dekBytes = ByteArray(32).also { random.nextBytes(it) }
        val kekBytes = ByteArray(32).also { random.nextBytes(it) }

        val envelope = SensitiveBytes(dekBytes.copyOf()).use { dek ->
            SensitiveBytes(kekBytes.copyOf()).use { kek ->
                cipher.wrapDekWithRawKey(
                    dek = dek,
                    kek = kek,
                    type = EnvelopeType.RECOVERY_KEY,
                    createdAtEpochMillis = 1700000000000L
                )
            }
        }

        SensitiveBytes(kekBytes.copyOf()).use { kek ->
            cipher.unwrapDekWithRawKey(envelope, kek).use { unwrapped ->
                assertArrayEquals(dekBytes, unwrapped.copyBytes())
            }
        }
    }

    @Test
    fun argon2idProducesDeterministicKeyForSameSaltAndDifferentForDifferentSalt() {
        val deriver = Argon2idKeyDeriver()
        val salt1 = deriver.generateSalt()
        val salt2 = deriver.generateSalt()
        val params = KdfParameters()
        val password = "StrongMasterPassword!2026".toCharArray()

        val bytesA = deriver.deriveKey(password.clone(), salt1, params).use { it.copyBytes() }
        val bytesB = deriver.deriveKey(password.clone(), salt1, params).use { it.copyBytes() }
        val bytesC = deriver.deriveKey(password.clone(), salt2, params).use { it.copyBytes() }

        assertArrayEquals(bytesA, bytesB)
        assertFalse(bytesA.contentEquals(bytesC))
    }

    @Test
    fun recoveryKeyManagerGeneratesAndDerivesDeterministicKek() {
        val manager = RecoveryKeyManager()
        val code = manager.generateRecoveryCode()
        assertTrue(String(code).contains("-"))

        val kek1 = manager.deriveKekFromRecoveryCode(code).use { it.copyBytes() }
        val kek2 = manager.deriveKekFromRecoveryCode(
            String(code).lowercase().toCharArray()
        ).use { it.copyBytes() }

        assertArrayEquals(kek1, kek2)
    }

    @Test
    fun masterPasswordMinimumIsEightCharactersAndPasswordGeneratorWorks() {
        assertEquals(8, DatabaseKeyManager.MIN_MASTER_PASSWORD_CHARS)

        val generated = PasswordGenerator.generatePassword(length = 18)
        assertEquals(18, generated.length)
        val strength = PasswordGenerator.estimateStrengthLabel(generated)
        assertTrue(strength == "Strong" || strength == "Vault-Grade")
    }

    @Test
    fun websiteLogoFetcherNormalizesHttpsAndDiscoversBestHtmlIcons() {
        assertEquals(
            "https://github.com/",
            WebsiteLogoFetcher.normalizeAndValidateHttpsUri("github.com").toString()
        )
        assertEquals(
            "https://mail.google.com/mail/u/0",
            WebsiteLogoFetcher.normalizeAndValidateHttpsUri("https://mail.google.com/mail/u/0").toString()
        )

        val sampleHtml = """
            <html>
              <head>
                <link rel="icon" sizes="32x32" href="/favicon-32x32.png">
                <link rel="apple-touch-icon" sizes="180x180" href="/apple-touch-icon.png">
                <link rel="shortcut icon" href="https://cdn.example.com/icon.ico">
              </head>
            </html>
        """.trimIndent()

        val candidates = WebsiteLogoFetcher.discoverIconCandidates(
            basePageUrl = "https://example.com",
            html = sampleHtml
        )

        assertTrue(candidates.isNotEmpty())
        assertEquals("https://example.com/apple-touch-icon.png", candidates.first())
        assertTrue(candidates.contains("https://example.com/favicon-32x32.png"))
        assertTrue(candidates.contains("https://cdn.example.com/icon.ico"))
    }
}
