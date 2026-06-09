package de.bsw.plakatradar.data

import android.content.Context
import de.bsw.plakatradar.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID

class LocalRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("plakatradar_p2p", Context.MODE_PRIVATE)
    private val stateStore = SecureStateStore(context)
    val photosDir: File = File(context.filesDir, "photos").apply { mkdirs() }
    val syncDir: File = File(context.cacheDir, "sync").apply { mkdirs() }

    fun load(): LocalTeamState {
        val deviceId = prefs.getString("deviceId", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("deviceId", it).apply()
        }
        val fallbackName = prefs.getString("deviceName", null) ?: "Dieses Handy"
        val stateText = stateStore.readTextOrNull() ?: return LocalTeamState(deviceId = deviceId, deviceName = fallbackName)
        val root = JSONObject(stateText)
        return LocalTeamState(
            deviceId = deviceId,
            deviceName = root.optString("deviceName", fallbackName),
            role = root.optString("role", "").takeIf { it.isNotBlank() }?.let { MemberRole.valueOf(it) },
            teamId = root.optString("teamId", "").takeIf { it.isNotBlank() },
            teamName = root.optString("teamName", "").takeIf { it.isNotBlank() },
            teamSecret = root.optString("teamSecret", "").takeIf { it.isNotBlank() },
            devices = root.optJSONArray("devices")?.let(::devicesFromJson) ?: emptyList(),
            posters = root.optJSONArray("posters")?.let(::postersFromJson) ?: emptyList(),
            events = root.optJSONArray("events")?.let(::eventsFromJson) ?: emptyList()
        )
    }

    fun save(state: LocalTeamState) {
        prefs.edit().putString("deviceName", state.deviceName).apply()
        val root = JSONObject()
            .put("deviceName", state.deviceName)
            .put("role", state.role?.name ?: "")
            .put("teamId", state.teamId ?: "")
            .put("teamName", state.teamName ?: "")
            .put("teamSecret", state.teamSecret ?: "")
            .put("devices", devicesToJson(state.devices))
            .put("posters", postersToJson(state.posters))
            .put("events", eventsToJson(state.events))
        stateStore.writeText(root.toString(2))
    }

    fun createLeaderTeam(teamName: String, leaderName: String): LocalTeamState {
        val current = load()
        val invite = TeamInvite.create(teamName, leaderName, current.deviceId)
        val leader = DeviceRecord(current.deviceId, leaderName.ifBlank { "Teamleiter" }, MemberRole.LEADER, approved = true)
        return current.copy(
            deviceName = leader.displayName,
            role = MemberRole.LEADER,
            teamId = invite.teamId,
            teamName = invite.teamName,
            teamSecret = invite.teamSecret,
            devices = listOf(leader),
            posters = emptyList(),
            events = listOf(
                PosterEvent(
                    teamId = invite.teamId,
                    posterId = "TEAM",
                    actorDeviceId = current.deviceId,
                    actorName = leader.displayName,
                    action = "Team erstellt"
                )
            )
        ).also(::save)
    }

    fun joinByInvite(invite: TeamInvite, memberName: String): LocalTeamState {
        invite.requireStillValid()
        val current = load()
        val leader = DeviceRecord(invite.leaderDeviceId, invite.leaderName, MemberRole.LEADER, approved = true)
        val member = DeviceRecord(current.deviceId, memberName.ifBlank { "Teammitglied" }, MemberRole.MEMBER, approved = true)
        return current.copy(
            deviceName = member.displayName,
            role = MemberRole.MEMBER,
            teamId = invite.teamId,
            teamName = invite.teamName,
            teamSecret = invite.teamSecret,
            devices = listOf(leader, member),
            posters = emptyList(),
            events = listOf(
                PosterEvent(
                    teamId = invite.teamId,
                    posterId = "TEAM",
                    actorDeviceId = current.deviceId,
                    actorName = member.displayName,
                    action = "Per Teamleiter-QR beigetreten"
                )
            )
        ).also(::save)
    }

    fun addPoster(state: LocalTeamState, poster: Poster): LocalTeamState {
        val event = PosterEvent(
            posterId = poster.id,
            teamId = poster.teamId,
            actorDeviceId = state.deviceId,
            actorName = state.deviceName,
            action = "Plakat erfasst"
        )
        return state.copy(posters = listOf(poster) + state.posters, events = listOf(event) + state.events).also(::save)
    }

    fun updateStatus(state: LocalTeamState, poster: Poster, newStatus: PosterStatus): LocalTeamState {
        val changed = poster.copy(status = newStatus, updatedAt = Instant.now().toEpochMilli())
        val updated = state.posters.map { if (it.id == poster.id) changed else it }
        val event = PosterEvent(
            posterId = poster.id,
            teamId = poster.teamId,
            actorDeviceId = state.deviceId,
            actorName = state.deviceName,
            action = "Status geändert zu ${newStatus.name}"
        )
        return state.copy(posters = updated, events = listOf(event) + state.events).also(::save)
    }

    fun mergeAndSave(local: LocalTeamState, snapshot: SyncSnapshot): LocalTeamState = SyncMerge.merge(local, snapshot).also(::save)

    fun toSnapshot(state: LocalTeamState, includePosterData: Boolean = AccessPolicy.isSelfApproved(state)): SyncSnapshot {
        val teamId = state.teamId ?: error("Kein Team aktiv")
        val teamName = state.teamName ?: "Plakat-Team"
        val secret = state.teamSecret ?: error("Kein Team-Schlüssel")
        val self = DeviceRecord(
            deviceId = state.deviceId,
            displayName = state.deviceName,
            role = state.role ?: MemberRole.MEMBER,
            approved = AccessPolicy.isSelfApproved(state)
        )
        val devices = (state.devices + self).distinctBy { it.deviceId }
        return SyncSnapshot(
            teamId = teamId,
            teamName = teamName,
            senderDeviceId = state.deviceId,
            senderName = state.deviceName,
            teamSecretHash = sha256Hex(secret),
            devices = devices,
            posters = if (includePosterData) state.posters else emptyList(),
            events = if (includePosterData) state.events else state.events.filter { it.posterId == "TEAM" }
        )
    }

    private fun devicesToJson(items: List<DeviceRecord>) = JSONArray().also { arr ->
        items.forEach {
            arr.put(
                JSONObject()
                    .put("deviceId", it.deviceId)
                    .put("displayName", it.displayName)
                    .put("role", it.role.name)
                    .put("joinedAt", it.joinedAt)
                    .put("approved", it.approved)
                    .put("blocked", it.blocked)
            )
        }
    }

    private fun devicesFromJson(arr: JSONArray) = (0 until arr.length()).map { arr.getJSONObject(it) }.map {
        val role = MemberRole.valueOf(it.getString("role"))
        DeviceRecord(
            deviceId = it.getString("deviceId"),
            displayName = it.getString("displayName"),
            role = role,
            joinedAt = it.optLong("joinedAt", Instant.now().toEpochMilli()),
            approved = it.optBoolean("approved", role == MemberRole.LEADER),
            blocked = it.optBoolean("blocked", false)
        )
    }

    private fun postersToJson(items: List<Poster>) = JSONArray().also { arr -> items.forEach { p -> arr.put(posterToJson(p)) } }
    private fun postersFromJson(arr: JSONArray) = (0 until arr.length()).map { posterFromJson(arr.getJSONObject(it)) }

    private fun posterToJson(p: Poster) = JSONObject()
        .put("id", p.id).put("teamId", p.teamId).put("latitude", p.latitude).put("longitude", p.longitude)
        .put("addressHint", p.addressHint).put("type", p.type.name).put("status", p.status.name)
        .put("localPhotoFileName", p.localPhotoFileName ?: "").put("createdByDeviceId", p.createdByDeviceId)
        .put("createdByName", p.createdByName).put("createdAt", p.createdAt).put("updatedAt", p.updatedAt)
        .put("plannedRemovalAt", p.plannedRemovalAt ?: JSONObject.NULL).put("officialNote", p.officialNote).put("internalNote", p.internalNote)

    private fun posterFromJson(o: JSONObject) = Poster(
        id = o.getString("id"),
        teamId = o.getString("teamId"),
        latitude = o.getDouble("latitude"),
        longitude = o.getDouble("longitude"),
        addressHint = o.optString("addressHint"),
        type = PosterType.valueOf(o.optString("type", PosterType.LAMP_POST.name)),
        status = PosterStatus.valueOf(o.optString("status", PosterStatus.HANGING.name)),
        localPhotoFileName = o.optString("localPhotoFileName", "").takeIf { it.isNotBlank() },
        createdByDeviceId = o.optString("createdByDeviceId"),
        createdByName = o.optString("createdByName"),
        createdAt = o.optLong("createdAt"),
        updatedAt = o.optLong("updatedAt"),
        plannedRemovalAt = if (o.isNull("plannedRemovalAt")) null else o.optLong("plannedRemovalAt"),
        officialNote = o.optString("officialNote"),
        internalNote = o.optString("internalNote")
    )

    private fun eventsToJson(items: List<PosterEvent>) = JSONArray().also { arr ->
        items.forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("posterId", e.posterId)
                    .put("teamId", e.teamId)
                    .put("actorDeviceId", e.actorDeviceId)
                    .put("actorName", e.actorName)
                    .put("action", e.action)
                    .put("createdAt", e.createdAt)
            )
        }
    }

    private fun eventsFromJson(arr: JSONArray) = (0 until arr.length()).map { arr.getJSONObject(it) }.map {
        PosterEvent(
            id = it.getString("id"),
            posterId = it.getString("posterId"),
            teamId = it.getString("teamId"),
            actorDeviceId = it.getString("actorDeviceId"),
            actorName = it.getString("actorName"),
            action = it.getString("action"),
            createdAt = it.optLong("createdAt")
        )
    }

    fun snapshotToJson(snapshot: SyncSnapshot): String = JSONObject()
        .put("teamId", snapshot.teamId)
        .put("teamName", snapshot.teamName)
        .put("senderDeviceId", snapshot.senderDeviceId)
        .put("senderName", snapshot.senderName)
        .put("teamSecretHash", snapshot.teamSecretHash)
        .put("devices", devicesToJson(snapshot.devices))
        .put("posters", postersToJson(snapshot.posters))
        .put("events", eventsToJson(snapshot.events))
        .put("createdAt", snapshot.createdAt)
        .toString(2)

    fun snapshotFromJson(raw: String): SyncSnapshot {
        val root = JSONObject(raw)
        return SyncSnapshot(
            teamId = root.getString("teamId"),
            teamName = root.getString("teamName"),
            senderDeviceId = root.getString("senderDeviceId"),
            senderName = root.getString("senderName"),
            teamSecretHash = root.getString("teamSecretHash"),
            devices = devicesFromJson(root.getJSONArray("devices")),
            posters = postersFromJson(root.getJSONArray("posters")),
            events = eventsFromJson(root.getJSONArray("events")),
            createdAt = root.optLong("createdAt")
        )
    }
}
