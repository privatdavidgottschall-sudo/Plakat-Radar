package de.bsw.plakatradar.core

object AccessPolicy {
    fun hasTeamAccess(state: LocalTeamState): Boolean =
        state.role != null && !state.teamId.isNullOrBlank() && !state.teamSecret.isNullOrBlank()

    fun isLeader(state: LocalTeamState): Boolean = state.role == MemberRole.LEADER

    fun selfRecord(state: LocalTeamState): DeviceRecord? =
        state.devices.firstOrNull { it.deviceId == state.deviceId }

    fun isSelfApproved(state: LocalTeamState): Boolean {
        if (!hasTeamAccess(state)) return false
        if (isLeader(state)) return true
        val self = selfRecord(state) ?: return false
        return self.approved && !self.blocked
    }

    fun isDeviceApproved(state: LocalTeamState, deviceId: String): Boolean =
        state.devices.any { it.deviceId == deviceId && it.approved && !it.blocked }

    fun canShowQr(state: LocalTeamState): Boolean = isLeader(state) && isSelfApproved(state)

    fun canAddPoster(state: LocalTeamState): Boolean = isSelfApproved(state)

    fun canSync(state: LocalTeamState): Boolean = hasTeamAccess(state)

    fun canShareSyncBundle(state: LocalTeamState): Boolean = hasTeamAccess(state)

    fun canExportForAuthority(state: LocalTeamState): Boolean = isLeader(state) && isSelfApproved(state)
}
