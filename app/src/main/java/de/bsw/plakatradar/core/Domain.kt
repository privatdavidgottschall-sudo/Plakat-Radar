package de.bsw.plakatradar.core

import java.time.Instant
import java.util.UUID

enum class MemberRole { LEADER, MEMBER }
enum class PosterStatus { HANGING, CHECKED, DAMAGED, MISSING, REPLACED, REMOVED }
enum class PosterType { LAMP_POST, FENCE, BANNER, TRIANGLE_STAND, LARGE_FORMAT, OTHER }

data class DeviceRecord(
    val deviceId: String,
    val displayName: String,
    val role: MemberRole,
    val joinedAt: Long = Instant.now().toEpochMilli(),
    val approved: Boolean = true,
    val blocked: Boolean = false
)

data class Poster(
    val id: String = UUID.randomUUID().toString(),
    val teamId: String,
    val latitude: Double,
    val longitude: Double,
    val addressHint: String = "",
    val type: PosterType = PosterType.LAMP_POST,
    val status: PosterStatus = PosterStatus.HANGING,
    val localPhotoFileName: String? = null,
    val createdByDeviceId: String,
    val createdByName: String,
    val createdAt: Long = Instant.now().toEpochMilli(),
    val updatedAt: Long = Instant.now().toEpochMilli(),
    val plannedRemovalAt: Long? = null,
    val officialNote: String = "",
    val internalNote: String = ""
)

data class PosterEvent(
    val id: String = UUID.randomUUID().toString(),
    val posterId: String,
    val teamId: String,
    val actorDeviceId: String,
    val actorName: String,
    val action: String,
    val createdAt: Long = Instant.now().toEpochMilli()
)

data class LocalTeamState(
    val deviceId: String,
    val deviceName: String,
    val role: MemberRole? = null,
    val teamId: String? = null,
    val teamName: String? = null,
    val teamSecret: String? = null,
    val devices: List<DeviceRecord> = emptyList(),
    val posters: List<Poster> = emptyList(),
    val events: List<PosterEvent> = emptyList()
)

data class SyncSnapshot(
    val teamId: String,
    val teamName: String,
    val senderDeviceId: String,
    val senderName: String,
    val teamSecretHash: String,
    val devices: List<DeviceRecord>,
    val posters: List<Poster>,
    val events: List<PosterEvent>,
    val createdAt: Long = Instant.now().toEpochMilli()
)
