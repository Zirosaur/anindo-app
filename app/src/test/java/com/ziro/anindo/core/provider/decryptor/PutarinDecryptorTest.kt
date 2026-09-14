package com.ziro.anindo.core.provider.decryptor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class PutarinDecryptorTest {

    @Test
    fun testDecryptEmptyOrInvalidReturnsNull() {
        assertNull(PutarinDecryptor.decrypt(""))
        assertNull(PutarinDecryptor.decrypt("not-base64-content!@#$"))
        assertNull(PutarinDecryptor.decrypt("short"))
    }

    @Test
    fun testDecryptValidPayload() {
        // Test encrypting and decrypting with Putarin AES-256-GCM key
        val keyBytes = "47696c616e672047616e74656e672042616e67657420416e6a696e6721212121"
            .chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()

        val iv = ByteArray(12) { (it + 1).toByte() }
        val plaintext = "https://stream.server.example/video.m3u8"

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(keyBytes, "AES")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // Combined payload: iv + ciphertext
        val combined = iv + ciphertext
        val base64Payload = Base64.getEncoder().encodeToString(combined)

        val decrypted = PutarinDecryptor.decrypt(base64Payload)
        assertNotNull(decrypted)
        assertEquals(plaintext, decrypted)
    }
}
