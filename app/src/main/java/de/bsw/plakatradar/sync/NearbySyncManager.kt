package de.bsw.plakatradar.sync

import android.content.Context
import android.net.Uri
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import de.bsw.plakatradar.core.AccessPolicy
import de.bsw.plakatradar.core.LocalTeamState
import de.bsw.plakatradar.core.constantTimeEqualsHex
import de.bsw.plakatradar.core.hmacSha256Hex
import de.bsw.plakatradar.core.randomNonceHex
import de.bsw.plakatradar.data.LocalRepository
import org.json.JSONObject
import java.io.File

class NearbySyncManager(
    private val context: Context,
    private val repo: LocalRepository,
    private val bundleCodec: SyncBundleCodec,
    private val onLog: (String) -> Unit,
    private val onIncomingBundle: (File) -> Unit
) {
    private val client: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val connectedEndpoints = linkedSetOf<String>()
    private val authorizedEndpoints = linkedSetOf<String>()
    private val remoteAcceptedUs = linkedSetOf<String>()
    private val bundleSentEndpoints = linkedSetOf<String>()
    private val incomingFiles = linkedMapOf<Long, Payload>()
    private val pendingChallenges = linkedMapOf<String, String>()
    private val endpointDeviceIds = linkedMapOf<String, String>()
    private var state: LocalTeamState? = null

    fun start(state: LocalTeamState) {
        this.state = state
        if (state.teamId.isNullOrBlank() || state.teamSecret.isNullOrBlank()) {
            onLog("Kein Team aktiv.")
            return
        }

        val endpointName = "PlakatRadar|${state.deviceName}|${state.deviceId.take(8)}"
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()

        client.startAdvertising(endpointName, SERVICE_ID, lifecycleCallback, options)
            .addOnSuccessListener { onLog("Lokaler Sync: Dieses Handy ist sichtbar.") }
            .addOnFailureListener { onLog("Sichtbarkeit fehlgeschlagen: ${it.message}") }

        client.startDiscovery(SERVICE_ID, discoveryCallback, discoveryOptions)
            .addOnSuccessListener { onLog("Lokaler Sync: Suche nach Teamgeräten läuft.") }
            .addOnFailureListener { onLog("Suche fehlgeschlagen: ${it.message}") }
    }

    fun stop() {
        client.stopAdvertising()
        client.stopDiscovery()
        connectedEndpoints.forEach { client.disconnectFromEndpoint(it) }
        connectedEndpoints.clear()
        authorizedEndpoints.clear()
        remoteAcceptedUs.clear()
        bundleSentEndpoints.clear()
        incomingFiles.clear()
        pendingChallenges.clear()
        endpointDeviceIds.clear()
        onLog("Lokaler Sync gestoppt.")
    }

    fun sendCurrentBundleToAll() {
        authorizedEndpoints.intersect(remoteAcceptedUs).forEach { endpoint ->
            sendBundleIfReady(endpoint, force = true)
        }
    }

    private fun sendBundle(endpointId: String, file: File) {
        val payload = Payload.fromFile(file)
        client.sendPayload(endpointId, payload)
            .addOnSuccessListener { onLog("Sync-Paket gesendet.") }
            .addOnFailureListener { onLog("Senden fehlgeschlagen: ${it.message}") }
    }

    private fun sendChallenge(endpointId: String) {
        val s = state ?: return
        val nonce = randomNonceHex()
        pendingChallenges[endpointId] = nonce
        val msg = JSONObject()
            .put("kind", "AUTH_CHALLENGE")
            .put("teamId", s.teamId)
            .put("nonce", nonce)
            .put("senderDeviceId", s.deviceId)
            .put("senderName", s.deviceName)
        sendControlMessage(endpointId, msg)
    }

    private fun sendControlMessage(endpointId: String, msg: JSONObject) {
        client.sendPayload(endpointId, Payload.fromBytes(msg.toString().toByteArray(Charsets.UTF_8)))
            .addOnFailureListener { onLog("Prüfnachricht konnte nicht gesendet werden: ${it.message}") }
    }

    private fun handleControlMessage(endpointId: String, payload: Payload) {
        val raw = payload.asBytes()?.toString(Charsets.UTF_8) ?: return
        val msg = runCatching { JSONObject(raw) }.getOrNull() ?: return
        when (msg.optString("kind")) {
            "AUTH_CHALLENGE" -> handleChallenge(endpointId, msg)
            "AUTH_RESPONSE" -> handleResponse(endpointId, msg)
            "AUTH_OK" -> handleAuthOk(endpointId, msg)
        }
    }

    private fun handleChallenge(endpointId: String, msg: JSONObject) {
        val s = state ?: return
        if (msg.optString("teamId") != s.teamId) {
            client.disconnectFromEndpoint(endpointId)
            onLog("Fremdes Team abgelehnt.")
            return
        }
        msg.optString("senderDeviceId").takeIf { it.isNotBlank() }?.let { endpointDeviceIds[endpointId] = it }
        val secret = s.teamSecret ?: return
        val nonce = msg.optString("nonce")
        if (nonce.isBlank()) return
        val response = JSONObject()
            .put("kind", "AUTH_RESPONSE")
            .put("teamId", s.teamId)
            .put("nonce", nonce)
            .put("proof", hmacSha256Hex(secret, nonce))
            .put("senderDeviceId", s.deviceId)
            .put("senderName", s.deviceName)
        sendControlMessage(endpointId, response)
    }

    private fun handleResponse(endpointId: String, msg: JSONObject) {
        val s = state ?: return
        if (msg.optString("teamId") != s.teamId) return
        msg.optString("senderDeviceId").takeIf { it.isNotBlank() }?.let { endpointDeviceIds[endpointId] = it }
        val nonce = pendingChallenges.remove(endpointId) ?: return
        val expected = hmacSha256Hex(s.teamSecret ?: return, nonce)
        if (constantTimeEqualsHex(msg.optString("proof"), expected) && msg.optString("nonce") == nonce) {
            authorizedEndpoints.add(endpointId)
            sendAuthOk(endpointId)
            onLog("Teamgerät geprüft.")
            sendBundleIfReady(endpointId)
        } else {
            client.disconnectFromEndpoint(endpointId)
            onLog("Gerät abgelehnt: Team-Schlüssel passt nicht.")
        }
    }

    private fun sendAuthOk(endpointId: String) {
        val s = state ?: return
        val msg = JSONObject()
            .put("kind", "AUTH_OK")
            .put("teamId", s.teamId)
            .put("senderDeviceId", s.deviceId)
            .put("senderName", s.deviceName)
        sendControlMessage(endpointId, msg)
    }

    private fun handleAuthOk(endpointId: String, msg: JSONObject) {
        val s = state ?: return
        if (msg.optString("teamId") != s.teamId) return
        msg.optString("senderDeviceId").takeIf { it.isNotBlank() }?.let { endpointDeviceIds[endpointId] = it }
        remoteAcceptedUs.add(endpointId)
        sendBundleIfReady(endpointId)
    }

    private fun sendBundleIfReady(endpointId: String, force: Boolean = false) {
        if (!authorizedEndpoints.contains(endpointId) || !remoteAcceptedUs.contains(endpointId)) return
        if (!force && !bundleSentEndpoints.add(endpointId)) return
        val s = state ?: return
        val secret = s.teamSecret ?: return
        val remoteDeviceId = endpointDeviceIds[endpointId]

        val includePosterData = when {
            !AccessPolicy.isSelfApproved(s) -> false
            AccessPolicy.isLeader(s) && remoteDeviceId != null -> AccessPolicy.isDeviceApproved(s, remoteDeviceId)
            else -> true
        }

        val snapshot = repo.toSnapshot(s, includePosterData = includePosterData)
        sendBundle(endpointId, bundleCodec.createBundle(snapshot, secret))
    }

    private val lifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            if (info.endpointName.startsWith("PlakatRadar|")) {
                client.acceptConnection(endpointId, payloadCallback)
                onLog("PlakatRadar-Gerät gefunden. Prüfe Team-Schlüssel…")
            } else {
                client.rejectConnection(endpointId)
                onLog("Fremdes Gerät abgelehnt.")
            }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connectedEndpoints.add(endpointId)
                onLog("Verbunden. Prüfe Team-Zugang…")
                sendChallenge(endpointId)
            } else {
                onLog("Verbindung nicht möglich: ${result.status.statusMessage ?: result.status.statusCode}")
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpoints.remove(endpointId)
            authorizedEndpoints.remove(endpointId)
            remoteAcceptedUs.remove(endpointId)
            bundleSentEndpoints.remove(endpointId)
            pendingChallenges.remove(endpointId)
            endpointDeviceIds.remove(endpointId)
            onLog("Teamgerät getrennt.")
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.endpointName.startsWith("PlakatRadar|")) {
                val s = state ?: return
                val localName = "PlakatRadar|${s.deviceName}|${s.deviceId.take(8)}"
                client.requestConnection(localName, endpointId, lifecycleCallback)
                onLog("Verbindung zu PlakatRadar-Gerät wird aufgebaut.")
            }
        }

        override fun onEndpointLost(endpointId: String) {
            connectedEndpoints.remove(endpointId)
            authorizedEndpoints.remove(endpointId)
            remoteAcceptedUs.remove(endpointId)
            bundleSentEndpoints.remove(endpointId)
            pendingChallenges.remove(endpointId)
            endpointDeviceIds.remove(endpointId)
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> handleControlMessage(endpointId, payload)
                Payload.Type.FILE -> {
                    if (!authorizedEndpoints.contains(endpointId) || !remoteAcceptedUs.contains(endpointId)) {
                        client.disconnectFromEndpoint(endpointId)
                        onLog("Ungeprüfte Sync-Datei abgelehnt.")
                        return
                    }
                    incomingFiles[payload.id] = payload
                    onLog("Sync-Datei wird empfangen…")
                }
                else -> Unit
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                val payload = incomingFiles.remove(update.payloadId) ?: return
                val uri: Uri = payload.asFile()?.asUri() ?: return
                val bundle = bundleCodec.copyIncomingUriToBundle(uri)
                onIncomingBundle(bundle)
                onLog("Sync-Paket empfangen und verarbeitet.")
            } else if (update.status == PayloadTransferUpdate.Status.FAILURE) {
                incomingFiles.remove(update.payloadId)
                onLog("Sync-Übertragung fehlgeschlagen.")
            }
        }
    }

    companion object {
        private const val SERVICE_ID = "de.bsw.plakatradar.LOCAL_SYNC"
        private val STRATEGY: Strategy = Strategy.P2P_CLUSTER
    }
}
