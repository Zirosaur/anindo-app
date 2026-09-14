package com.ziro.anindo.core.provider.decryptor

import android.util.Base64
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object PutarinDecryptor {
    /**
     * Decrypt AES-256-GCM encrypted payload from Putarin HLS server.
     */
    fun decryptAesGcm(ciphertextBase64: String, keyBytes: ByteArray, ivBytes: ByteArray): String {
        val encryptedData = Base64.decode(ciphertextBase64, Base64.DEFAULT)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(keyBytes, "AES")
        val gcmSpec = GCMParameterSpec(128, ivBytes)

        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        val decrypted = cipher.doFinal(encryptedData)
        return String(decrypted, StandardCharsets.UTF_8)
    }
}
