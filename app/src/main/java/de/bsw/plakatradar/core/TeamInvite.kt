package de.bsw.plakatradar.core

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.UUID

data class TeamInvite(
    val teamId: String,
    val teamName: String,
    val leaderName: String,
    val leaderDeviceId: String,
    val teamSecret: String,
    val createdAt: Long = Instant.now().toEpochMilli(),
    val expiresAt: Long = Instant.now().plusSeconds(DEFAULT_TTL_SECONDS).toEpochMilli()
) {
    fun encodeForQr(): String {
        val fields = listOf(
            "PLAKATRADAR",
            "3",
            teamId,
            teamName,
            leaderName,
            leaderDeviceId,
            teamSecret,
            createdAt.toString(),
            expiresAt.toString()
        )
        return fields.joinToString("|") { it.b64() }
    }

    fun requireStillValid() {
        require(Instant.now().toEpochMilli() <= expiresAt) {
            "Dieser Team-QR-Code ist abgelaufen. Bitte den Teamleiter um einen neuen QR-Code bitten."
        }
    }

    companion object {
        const val DEFAULT_TTL_SECONDS: Long = 60

        fun create(teamName: String, leaderName: String, leaderDeviceId: String): TeamInvite = TeamInvite(
            teamId = UUID.randomUUID().toString(),
            teamName = teamName.ifBlank { "Plakat-Team" },
            leaderName = leaderName.ifBlank { "Teamleiter" },
            leaderDeviceId = leaderDeviceId,
            teamSecret = UUID.randomUUID().toString() + UUID.randomUUID().toString()
        )

        fun decode(raw: String): TeamInvite {
            val parts = raw.split("|").map { it.unb64() }
            require(parts.isNotEmpty()) { "Ungültiger Team-QR-Code." }
            require(parts[0] == "PLAKATRADAR") { "Das ist kein PlakatRadar-QR-Code." }

            return when (parts.getOrNull(1)) {
                "3" -> {
                    require(parts.size == 9) { "Ungültiger Team-QR-Code." }
                    TeamInvite(
                        teamId = parts[2],
                        teamName = parts[3],
                        leaderName = parts[4],
                        leaderDeviceId = parts[5],
                        teamSecret = parts[6],
                        createdAt = parts[7].toLongOrNull() ?: Instant.now().toEpochMilli(),
                        expiresAt = parts[8].toLongOrNull() ?: 0L
                    )
                }
                "2" -> error("Dieser alte Team-QR-Code wird nicht mehr unterstützt. Bitte neuen QR-Code vom Teamleiter scannen.")
                else -> error("QR-Code-Version wird nicht unterstützt.")
            }
        }
    }
}

private fun String.b64(): String = Base64.getUrlEncoder().withoutPadding()
    .encodeToString(toByteArray(StandardCharsets.UTF_8))

private fun String.unb64(): String = String(Base64.getUrlDecoder().decode(this), StandardCharsets.UTF_8)
