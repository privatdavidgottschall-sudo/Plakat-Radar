package de.bsw.plakatradar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {
    private val teamId = "team-1"
    private val teamSecret = "secret-1"

    private fun localState(deviceId: String = "member-phone"): LocalTeamState = LocalTeamState(
        deviceId = deviceId,
        deviceName = "Mitglied",
        role = MemberRole.MEMBER,
        teamId = teamId,
        teamName = "BSW Nordsachsen",
        teamSecret = teamSecret,
        devices = listOf(
            DeviceRecord(deviceId = deviceId, displayName = "Mitglied", role = MemberRole.MEMBER),
            DeviceRecord(deviceId = "leader-phone", displayName = "David", role = MemberRole.LEADER)
        )
    )

    @Test
    fun verifyAcceptsMatchingTeamAndSecret() {
        val snapshot = SyncSnapshot(
            teamId = teamId,
            teamName = "BSW Nordsachsen",
            senderDeviceId = "leader-phone",
            senderName = "David",
            teamSecretHash = sha256Hex(teamSecret),
            devices = emptyList(),
            posters = emptyList(),
            events = emptyList()
        )

        assertTrue(SyncMerge.verify(snapshot, localState()))
    }

    @Test
    fun verifyRejectsWrongSecret() {
        val snapshot = SyncSnapshot(
            teamId = teamId,
            teamName = "BSW Nordsachsen",
            senderDeviceId = "leader-phone",
            senderName = "David",
            teamSecretHash = sha256Hex("wrong-secret"),
            devices = emptyList(),
            posters = emptyList(),
            events = emptyList()
        )

        assertFalse(SyncMerge.verify(snapshot, localState()))
    }

    @Test
    fun verifyRejectsBlockedSender() {
        val local = localState().copy(
            devices = listOf(
                DeviceRecord(deviceId = "member-phone", displayName = "Mitglied", role = MemberRole.MEMBER),
                DeviceRecord(deviceId = "old-phone", displayName = "Ausgeschieden", role = MemberRole.MEMBER, blocked = true)
            )
        )
        val snapshot = SyncSnapshot(
            teamId = teamId,
            teamName = "BSW Nordsachsen",
            senderDeviceId = "old-phone",
            senderName = "Ausgeschieden",
            teamSecretHash = sha256Hex(teamSecret),
            devices = emptyList(),
            posters = emptyList(),
            events = emptyList()
        )

        assertFalse(SyncMerge.verify(snapshot, local))
    }

    @Test
    fun mergeKeepsNewestPosterVersion() {
        val posterId = "poster-1"
        val localPoster = Poster(
            id = posterId,
            teamId = teamId,
            latitude = 51.0,
            longitude = 12.0,
            addressHint = "Alt",
            createdByDeviceId = "member-phone",
            createdByName = "Mitglied",
            updatedAt = 1000L
        )
        val incomingPoster = localPoster.copy(
            addressHint = "Neu",
            updatedAt = 2000L
        )
        val local = localState().copy(posters = listOf(localPoster))
        val snapshot = SyncSnapshot(
            teamId = teamId,
            teamName = "BSW Nordsachsen",
            senderDeviceId = "leader-phone",
            senderName = "David",
            teamSecretHash = sha256Hex(teamSecret),
            devices = listOf(DeviceRecord(deviceId = "leader-phone", displayName = "David", role = MemberRole.LEADER)),
            posters = listOf(incomingPoster),
            events = emptyList()
        )

        val merged = SyncMerge.merge(local, snapshot)

        assertEquals(1, merged.posters.size)
        assertEquals("Neu", merged.posters.single().addressHint)
    }
}
