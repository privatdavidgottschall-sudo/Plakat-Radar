package de.bsw.plakatradar.core

object SyncMerge {
    fun verify(snapshot: SyncSnapshot, local: LocalTeamState): Boolean {
        val localTeamId = local.teamId ?: return false
        val localSecret = local.teamSecret ?: return false
        val senderBlocked = local.devices.any { it.deviceId == snapshot.senderDeviceId && it.blocked }
        if (senderBlocked) return false
        return snapshot.teamId == localTeamId && constantTimeEqualsHex(snapshot.teamSecretHash, sha256Hex(localSecret))
    }

    fun merge(local: LocalTeamState, incoming: SyncSnapshot): LocalTeamState {
        require(verify(incoming, local)) { "Fremdes oder ungültiges Team-Paket." }

        val incomingSenderIsKnownLeader = local.devices.any {
            it.deviceId == incoming.senderDeviceId && it.role == MemberRole.LEADER && it.approved && !it.blocked
        } || (local.role == MemberRole.LEADER && incoming.senderDeviceId == local.deviceId)

        val deviceMap = linkedMapOf<String, DeviceRecord>()
        local.devices.forEach { deviceMap[it.deviceId] = it }

        incoming.devices.forEach { incomingDevice ->
            val old = deviceMap[incomingDevice.deviceId]
            val safeIncoming = when {
                local.role == MemberRole.LEADER && old == null ->
                    incomingDevice.copy(approved = incomingDevice.role == MemberRole.LEADER && incomingDevice.deviceId == local.deviceId)

                local.role == MemberRole.LEADER && old != null ->
                    incomingDevice.copy(approved = old.approved, blocked = old.blocked)

                incomingSenderIsKnownLeader ->
                    incomingDevice

                old == null ->
                    incomingDevice.copy(approved = false)

                else ->
                    old
            }

            val finalOld = deviceMap[safeIncoming.deviceId]
            deviceMap[safeIncoming.deviceId] = when {
                finalOld == null -> safeIncoming
                safeIncoming.joinedAt >= finalOld.joinedAt || incomingSenderIsKnownLeader -> safeIncoming
                else -> finalOld
            }
        }

        val incomingSenderApprovedByLocal = local.devices.any {
            it.deviceId == incoming.senderDeviceId && it.approved && !it.blocked
        } || incoming.senderDeviceId == local.deviceId

        val incomingMayContainData = incomingSenderIsKnownLeader || incomingSenderApprovedByLocal

        val posterMap = linkedMapOf<String, Poster>()
        local.posters.forEach { posterMap[it.id] = it }
        if (incomingMayContainData) {
            incoming.posters.forEach { current ->
                val old = posterMap[current.id]
                posterMap[current.id] = when {
                    old == null -> current
                    current.updatedAt >= old.updatedAt -> current
                    else -> old
                }
            }
        }

        val eventMap = linkedMapOf<String, PosterEvent>()
        local.events.forEach { eventMap[it.id] = it }
        if (incomingMayContainData) incoming.events.forEach { eventMap[it.id] = it }

        return local.copy(
            teamName = local.teamName ?: incoming.teamName,
            devices = deviceMap.values.sortedWith(compareBy<DeviceRecord> { it.role != MemberRole.LEADER }.thenBy { it.displayName }),
            posters = posterMap.values.sortedByDescending { it.updatedAt },
            events = eventMap.values.sortedByDescending { it.createdAt }
        )
    }
}
