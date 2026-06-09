package de.bsw.plakatradar.data

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Speichert die lokale Team-Datei verschlüsselt im privaten App-Speicher.
 *
 * Ziel: TeamSecret, Plakatdaten, Notizen und Geräteinformationen liegen nicht mehr
 * als lesbares JSON in state.json. Android legt den AES-Schlüssel im Keystore ab.
 */
class SecureStateStore(context: Context) {
    private val encryptedFile = File(context.filesDir, "state.enc")
    private val legacyPlainFile = File(context.filesDir, "state.json")
    private val keyAlias = "plakatradar_state_key_v1"

    fun readTextOrNull(): String? {
        if (encryptedFile.exists()) {
            return decrypt(encryptedFile.readBytes()).toString(Charsets.UTF_8)
        }

        // Sanfte Migration alter Builds: bisheriges Klartext-JSON einmalig verschlüsseln.
        if (legacyPlainFile.exists()) {
            val plain = legacyPlainFile.readText()
            writeText(plain)
            legacyPlainFile.delete()
            return plain
        }

        return null
    }

    fun writeText(text: String) {
        encryptedFile.writeBytes(encrypt(text.toByteArray(Charsets.UTF_8)))
        if (legacyPlainFile.exists()) legacyPlainFile.delete()
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plain)
        return iv + encrypted
    }

    private fun decrypt(data: ByteArray): ByteArray {
        require(data.size > 12) { "Lokale Datendatei ist beschädigt." }
        val iv = data.copyOfRange(0, 12)
        val encrypted = data.copyOfRange(12, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}
