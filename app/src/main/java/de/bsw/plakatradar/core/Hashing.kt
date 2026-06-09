package de.bsw.plakatradar.core

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

fun sha256Hex(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return bytes.toHex()
}

fun hmacSha256Hex(secret: String, message: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    return mac.doFinal(message.toByteArray(Charsets.UTF_8)).toHex()
}

fun constantTimeEqualsHex(a: String, b: String): Boolean =
    MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

fun randomNonceHex(bytes: Int = 32): String {
    val buffer = ByteArray(bytes)
    SecureRandom().nextBytes(buffer)
    return buffer.toHex()
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
