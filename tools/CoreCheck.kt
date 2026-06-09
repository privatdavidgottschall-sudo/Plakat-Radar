import de.bsw.plakatradar.core.*

fun main() {
    val invite = TeamInvite.create("BSW Nordsachsen", "David")
    val decoded = TeamInvite.decode(invite.encodeForQr())
    check(decoded.teamId == invite.teamId)
    check(decoded.teamSecret == invite.teamSecret)

    val leaderState = LocalTeamState(
        deviceId = "leader-phone",
        deviceName = "David",
        role = MemberRole.LEADER,
        teamId = invite.teamId,
        teamName = invite.teamName,
        teamSecret = invite.teamSecret,
        devices = listOf(DeviceRecord("leader-phone", "David", MemberRole.LEADER)),
        posters = listOf(Poster(teamId = invite.teamId, latitude = 51.45, longitude = 12.63, createdByDeviceId = "leader-phone", createdByName = "David"))
    )
    val memberState = LocalTeamState(
        deviceId = "member-phone",
        deviceName = "Patrick",
        role = MemberRole.MEMBER,
        teamId = invite.teamId,
        teamName = invite.teamName,
        teamSecret = invite.teamSecret,
        devices = listOf(DeviceRecord("member-phone", "Patrick", MemberRole.MEMBER)),
        posters = emptyList()
    )
    val snap = SyncSnapshot(
        teamId = invite.teamId,
        teamName = invite.teamName,
        senderDeviceId = leaderState.deviceId,
        senderName = leaderState.deviceName,
        teamSecretHash = sha256Hex(invite.teamSecret),
        devices = leaderState.devices,
        posters = leaderState.posters,
        events = leaderState.events
    )
    val merged = SyncMerge.merge(memberState, snap)
    check(merged.posters.size == 1)
    check(AccessPolicy.canShowQr(leaderState))
    check(!AccessPolicy.canShowQr(memberState))
    val csv = OfficialExport.toCsv(leaderState, "Eilenburg")
    check(csv.contains("Eilenburg"))
    println("OK: P2P core checks passed")
}
