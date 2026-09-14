package com.ziro.anindo.core.provider.decryptor

import android.util.Log
import com.ziro.anindo.core.network.NetworkClient
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object PutarinDecryptor {
    private val DEFAULT_KEY = "47696c616e672047616e74656e672042616e67657420416e6a696e6721212121"
        .chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()

    private fun safeBase64Decode(str: String): ByteArray? {
        val clean = str.trim()
        if (clean.isEmpty()) return null
        return try {
            java.util.Base64.getDecoder().decode(clean)
        } catch (e: Throwable) {
            try {
                java.util.Base64.getUrlDecoder().decode(clean)
            } catch (e2: Throwable) {
                try {
                    android.util.Base64.decode(clean, android.util.Base64.DEFAULT)
                } catch (e3: Throwable) {
                    null
                }
            }
        }
    }

    /**
     * Decrypt AES-256-GCM encrypted payload from Putarin HLS server or resolve embed URL.
     */
    fun decrypt(input: String): String? {
        if (input.isBlank()) return null

        if (input.startsWith("http://") || input.startsWith("https://")) {
            return resolveEmbedUrl(input)
        }

        return decryptPayload(input, DEFAULT_KEY)
    }

    /**
     * Decrypt raw base64 payload containing IV (12 bytes) + ciphertext using the provided key.
     */
    fun decryptPayload(base64Payload: String, keyBytes: ByteArray = DEFAULT_KEY): String? {
        return try {
            val raw = safeBase64Decode(base64Payload) ?: return null
            if (raw.size <= 12) return null

            val iv = raw.copyOfRange(0, 12)
            val ciphertext = raw.copyOfRange(12, raw.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(keyBytes, "AES")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            val decrypted = cipher.doFinal(ciphertext)
            String(decrypted, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Resolve embed URL by scraping __PX config and fetching PK if available.
     */
    fun resolveEmbedUrl(embedUrl: String): String? {
        return try {
            Log.d("AnindoStream", "Resolving Putarin embed: $embedUrl")
            val uri = URI(embedUrl)
            val host = uri.host ?: return null
            val html = NetworkClient.get(embedUrl, referer = "https://$host/")

            val pxMatch = Regex("""window\.__PX\s*=\s*(\{.*?\});""").find(html)
            if (pxMatch != null) {
                val jsonStr = pxMatch.groupValues[1]
                val pxJson = JSONObject(jsonStr)
                val nVal = pxJson.optString("n", "")
                val dB64 = pxJson.optString("d", "")

                var key = DEFAULT_KEY
                if (nVal.isNotBlank()) {
                    val pkUrl = "https://$host/api/pk?n=" + URLEncoder.encode(nVal, "UTF-8")
                    try {
                        val kHex = NetworkClient.get(pkUrl, referer = embedUrl).trim()
                        if (kHex.length >= 64) {
                            key = kHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                        }
                    } catch (e: Exception) {
                        Log.w("AnindoStream", "Failed to fetch dynamic Putarin PK, using default key: ${e.message}")
                    }
                }

                if (dB64.isNotBlank()) {
                    val decryptedText = decryptPayload(dB64, key)
                    if (decryptedText != null) {
                        val cfg = JSONObject(decryptedText)
                        var filePath = cfg.optString("file", "")
                        if (filePath.isNotBlank()) {
                            if (filePath.startsWith("/")) {
                                filePath = "https://$host$filePath"
                            }
                            Log.d("AnindoStream", "Putarin stream decrypted successfully: $filePath")
                            return filePath
                        }
                    } else {
                        Log.w("AnindoStream", "Failed to decrypt Putarin payload (decryptPayload returned null)")
                    }
                }
            } else {
                Log.w("AnindoStream", "No window.__PX found in Putarin page: $embedUrl")
            }
            null
        } catch (e: Exception) {
            Log.e("AnindoStream", "Error resolving Putarin embed: ${e.message}", e)
            null
        }
    }

    /**
     * Decrypt AES-256-GCM encrypted payload from Putarin HLS server with separate parameters.
     */
    fun decryptAesGcm(ciphertextBase64: String, keyBytes: ByteArray, ivBytes: ByteArray): String {
        val encryptedData = safeBase64Decode(ciphertextBase64) ?: throw IllegalArgumentException("Invalid Base64")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(keyBytes, "AES")
        val gcmSpec = GCMParameterSpec(128, ivBytes)

        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        val decrypted = cipher.doFinal(encryptedData)
        return String(decrypted, StandardCharsets.UTF_8)
    }
}
