package de.bsw.plakatradar

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import de.bsw.plakatradar.core.*
import de.bsw.plakatradar.data.LocalRepository
import de.bsw.plakatradar.sync.NearbySyncManager
import de.bsw.plakatradar.sync.SyncBundleCodec
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableScreenshotProtection()
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { PlakatRadarApp() } }
    }

    private fun enableScreenshotProtection() {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false)
        }
    }
}

data class AppUiState(
    val local: LocalTeamState,
    val syncActive: Boolean = false,
    val lastLog: String = "Bereit.",
    val error: String? = null,
    val pendingPhotoFileName: String? = null
)

class PlakatRadarViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = LocalRepository(app)
    private val codec = SyncBundleCodec(app, repo)
    private var sync: NearbySyncManager? = null

    var ui by mutableStateOf(AppUiState(repo.load()))
        private set

    fun createLeaderTeam(teamName: String, leaderName: String) {
        runCatching { repo.createLeaderTeam(teamName, leaderName) }
            .onSuccess { ui = ui.copy(local = it, lastLog = "Team erstellt. Du bist Teamleiter.") }
            .onFailure { fail(it) }
    }

    fun joinByQr(raw: String, memberName: String) {
        runCatching { repo.joinByInvite(TeamInvite.decode(raw), memberName) }
            .onSuccess { ui = ui.copy(local = it, lastLog = "Beigetreten. Du kannst jetzt Plakate sehen und erfassen.") }
            .onFailure { fail(it) }
    }

    fun inviteText(): String? {
        val s = ui.local
        if (!AccessPolicy.canShowQr(s)) return null
        return TeamInvite(
            teamId = s.teamId ?: return null,
            teamName = s.teamName ?: "Plakat-Team",
            leaderName = s.deviceName,
            leaderDeviceId = s.deviceId,
            teamSecret = s.teamSecret ?: return null
        ).encodeForQr()
    }


    fun startOrStopSync() {
        if (ui.syncActive) {
            sync?.stop()
            ui = ui.copy(syncActive = false, lastLog = "Lokaler Sync aus.")
            return
        }
        if (!AccessPolicy.canSync(ui.local)) return
        sync = NearbySyncManager(
            context = getApplication(),
            repo = repo,
            bundleCodec = codec,
            onLog = { ui = ui.copy(lastLog = it) },
            onIncomingBundle = { file -> importBundle(file) }
        )
        sync?.start(ui.local)
        ui = ui.copy(syncActive = true, lastLog = "Lokaler Sync an. Geräte in der Nähe werden gesucht.")
    }

    private fun importBundle(file: File) {
        runCatching {
            val snapshot = codec.importVerifiedBundle(file, ui.local)
            repo.mergeAndSave(ui.local, snapshot)
        }.onSuccess {
            ui = ui.copy(local = it, lastLog = "Daten mit Teamgerät abgeglichen.")
            sync?.sendCurrentBundleToAll()
        }.onFailure { fail(it) }
    }

    private fun fileProviderAuthority(): String =
        getApplication<Application>().packageName + ".fileprovider"

    fun preparePhotoFile(): Uri {
        val fileName = "poster_${UUID.randomUUID()}.jpg"
        val file = File(repo.photosDir, fileName)
        ui = ui.copy(pendingPhotoFileName = fileName)
        return FileProvider.getUriForFile(getApplication(), fileProviderAuthority(), file)
    }

    fun addPoster(
        lat: Double,
        lng: Double,
        address: String,
        type: PosterType,
        officialNote: String,
        internalNote: String,
        removalDays: Long
    ) {
        val s = ui.local
        val teamId = s.teamId ?: return
        if (!AccessPolicy.canAddPoster(s)) return
        val plannedRemovalAt = Instant.now().plus(removalDays.coerceIn(1, 120), ChronoUnit.DAYS).toEpochMilli()
        val poster = Poster(
            teamId = teamId,
            latitude = lat,
            longitude = lng,
            addressHint = address,
            type = type,
            status = PosterStatus.HANGING,
            localPhotoFileName = ui.pendingPhotoFileName,
            createdByDeviceId = s.deviceId,
            createdByName = s.deviceName,
            plannedRemovalAt = plannedRemovalAt,
            officialNote = officialNote,
            internalNote = internalNote
        )
        val updated = repo.addPoster(s, poster)
        ui = ui.copy(local = updated, pendingPhotoFileName = null, lastLog = "Plakat gespeichert.")
        sync?.sendCurrentBundleToAll()
    }

    fun updateStatus(poster: Poster, status: PosterStatus) {
        val updated = repo.updateStatus(ui.local, poster, status)
        ui = ui.copy(local = updated, lastLog = "Status geändert.")
        sync?.sendCurrentBundleToAll()
    }

    fun exportCsv(context: Context, municipality: String) {
        runCatching {
            val file = File(context.cacheDir, "Plakatliste_${municipality}_${System.currentTimeMillis()}.csv")
            file.writeText(OfficialExport.toCsv(ui.local, municipality))
            val uri = FileProvider.getUriForFile(context, fileProviderAuthority(), file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Plakatliste teilen"))
        }.onFailure { fail(it) }
    }

    fun shareSyncBundle(context: Context) {
        runCatching {
            if (!AccessPolicy.canShareSyncBundle(ui.local)) error("Bitte erst Team-QR-Code nutzen. Ohne Teamzugang gibt es kein Sync-Paket.")
            val file = codec.createBundle(repo.toSnapshot(ui.local), ui.local.teamSecret ?: error("Kein Team-Schlüssel."))
            val uri = FileProvider.getUriForFile(context, fileProviderAuthority(), file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "PlakatRadar Sync-Paket")
                putExtra(Intent.EXTRA_TEXT, "Verschlüsseltes PlakatRadar Sync-Paket für ${ui.local.teamName ?: "das Team"}. Bitte in PlakatRadar importieren.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Sync-Paket teilen"))
            ui = ui.copy(lastLog = "Verschlüsseltes Sync-Paket wurde zum Teilen vorbereitet.")
        }.onFailure { fail(it) }
    }

    fun importSharedSyncBundle(uri: Uri) {
        runCatching {
            if (!AccessPolicy.canSync(ui.local)) error("Bitte erst Team-QR-Code vom Teamleiter scannen.")
            val file = codec.copyIncomingUriToBundle(uri)
            val snapshot = codec.importVerifiedBundle(file, ui.local)
            repo.mergeAndSave(ui.local, snapshot)
        }.onSuccess {
            ui = ui.copy(local = it, lastLog = "Sync-Paket importiert. Daten wurden zusammengeführt.")
            sync?.sendCurrentBundleToAll()
        }.onFailure { fail(it) }
    }

    fun showGoogleServiceNotImplemented() {
        ui = ui.copy(
            lastLog = "Google-Service-Sync ist vorgesehen, aber noch nicht implementiert.",
            error = "Google-Service-Sync ist vorgesehen, aber noch nicht implementiert. Der Schalter bleibt deshalb ausgeschaltet. Aktuell bitte lokalen Sync oder verschlüsselte Messenger-Sync-Pakete nutzen."
        )
    }

    fun clearError() { ui = ui.copy(error = null) }
    private fun fail(t: Throwable) { ui = ui.copy(error = t.message ?: t.toString()) }
}

@Composable
fun PlakatRadarApp(vm: PlakatRadarViewModel = viewModel()) {
    val s = vm.ui
    Surface(Modifier.fillMaxSize()) {
        when {
            !AccessPolicy.hasTeamAccess(s.local) -> StartScreen(vm)
            else -> DashboardScreen(vm)
        }
    }
    s.error?.let {
        AlertDialog(
            onDismissRequest = vm::clearError,
            confirmButton = { Button(vm::clearError) { Text("OK") } },
            title = { Text("Hinweis") },
            text = { Text(it) }
        )
    }
}

@Composable
fun StartScreen(vm: PlakatRadarViewModel) {
    var mode by remember { mutableStateOf<String?>(null) }
    var teamName by remember { mutableStateOf("BSW Nordsachsen Plakatierung") }
    var myName by remember { mutableStateOf("") }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val code = result.contents
        if (code != null) vm.joinByQr(code, myName.ifBlank { "Teammitglied" })
    }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("PlakatRadar", style = MaterialTheme.typography.headlineLarge)
        Text("Bitte auswählen. Es gibt nur zwei Wege:")
        Button(onClick = { mode = "leader" }, modifier = Modifier.fillMaxWidth().height(72.dp)) { Text("Ich bin Teamleiter") }
        Button(onClick = { mode = "member" }, modifier = Modifier.fillMaxWidth().height(72.dp)) { Text("Ich bin Teammitglied") }
        Divider()
        when (mode) {
            "leader" -> {
                Text("Teamleiter erstellt das Team und zeigt später den QR-Code. Der QR-Code ist jeweils 10 Minuten gültig.")
                OutlinedTextField(teamName, { teamName = it }, label = { Text("Teamname") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(myName, { myName = it }, label = { Text("Dein Name") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { vm.createLeaderTeam(teamName, myName.ifBlank { "Teamleiter" }) }, modifier = Modifier.fillMaxWidth().height(64.dp)) { Text("Team erstellen") }
            }
            "member" -> {
                Text("Teammitglied scannt ausschließlich den QR-Code vom Teamleiter und ist danach direkt im Team.")
                OutlinedTextField(myName, { myName = it }, label = { Text("Dein Name") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { scanner.launch(ScanOptions().setPrompt("QR-Code vom Teamleiter scannen").setBeepEnabled(false)) }, modifier = Modifier.fillMaxWidth().height(64.dp)) { Text("QR-Code scannen") }
            }
        }
    }
}

@Composable
fun DashboardScreen(vm: PlakatRadarViewModel) {
    var tab by remember { mutableStateOf("home") }
    var showPermissionPopup by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val allGranted = grants.values.all { it }
        Toast.makeText(
            context,
            if (allGranted) "Berechtigungen sind aktiv." else "Einige Berechtigungen fehlen noch. Manche Funktionen können eingeschränkt sein.",
            Toast.LENGTH_LONG
        ).show()
        showPermissionPopup = false
    }
    val missingPermissions = remember(showPermissionPopup) { missingAppPermissions(context) }
    if (showPermissionPopup && missingPermissions.isNotEmpty()) {
        PermissionStartupDialog(
            onAllow = { permissionLauncher.launch(missingPermissions) },
            onLater = { showPermissionPopup = false }
        )
    }

    val tabs = listOf("home", "add", "map", "near", "list")
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tabs.indexOf(tab).coerceAtLeast(0)) {
            Tab(tab == "home", { tab = "home" }, text = { Text("Start") })
            Tab(tab == "add", { tab = "add" }, text = { Text("Plakat") })
            Tab(tab == "map", { tab = "map" }, text = { Text("Karte") })
            Tab(tab == "near", { tab = "near" }, text = { Text("Nähe") })
            Tab(tab == "list", { tab = "list" }, text = { Text("Liste") })
        }
        when (tab) {
            "home" -> HomeScreen(vm)
            "add" -> AddPosterScreen(vm)
            "map" -> PosterMapScreen(vm.ui.local.posters)
            "near" -> NearbyPostersScreen(vm)
            "list" -> PosterListScreen(vm)
        }
    }
}

@Composable
fun PermissionStartupDialog(onAllow: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("Berechtigungen für alle Funktionen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Damit PlakatRadar richtig funktioniert, braucht die App einige Berechtigungen:")
                Text("• Kamera: QR-Code scannen und Plakatfoto aufnehmen")
                Text("• Standort: GPS-Punkt des Plakats speichern und Plakate in deiner Nähe anzeigen")
                Text("• Bluetooth/WLAN in der Nähe: lokaler Team-Sync ohne Cloud")
                Text("Du kannst die App auch ohne alles nutzen, aber dann funktionieren manche Dinge nur eingeschränkt.")
            }
        },
        confirmButton = {
            Button(onClick = onAllow) { Text("Berechtigungen erlauben") }
        },
        dismissButton = {
            TextButton(onClick = onLater) { Text("Später") }
        }
    )
}

@Composable
fun HomeScreen(vm: PlakatRadarViewModel) {
    val context = LocalContext.current
    val s = vm.ui.local
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    val syncImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importSharedSyncBundle(uri)
    }

    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text(s.teamName ?: "Plakat-Team", style = MaterialTheme.typography.headlineMedium)
            Text(if (s.role == MemberRole.LEADER) "Du bist Teamleiter." else "Du bist Teammitglied.")
            Text("Letzte Meldung: ${vm.ui.lastLog}")
        }

        item { RemovalReminderCard(s) }

        item {
            Button(onClick = { permissions.launch(nearbyAndAppPermissions()) }, modifier = Modifier.fillMaxWidth().height(60.dp)) { Text("Berechtigungen erlauben") }
        }
        item {
            Button(onClick = vm::startOrStopSync, modifier = Modifier.fillMaxWidth().height(60.dp)) { Text(if (vm.ui.syncActive) "Lokalen Sync stoppen" else "Lokalen Sync starten") }
            Text("Der lokale Sync funktioniert, wenn Teamgeräte in der Nähe sind, z.B. im selben Raum, Büro oder WLAN-Umfeld.")
        }

        item {
            Divider()
            Text("Falls ihr nicht nebeneinander steht: verschlüsseltes Sync-Paket über Messenger teilen und beim anderen Handy importieren.")
            Button(onClick = { vm.shareSyncBundle(context) }, modifier = Modifier.fillMaxWidth().height(60.dp)) { Text("Sync-Paket teilen") }
            Spacer(Modifier.height(6.dp))
            Button(onClick = { syncImportLauncher.launch(arrayOf("application/octet-stream", "application/zip", "*/*")) }, modifier = Modifier.fillMaxWidth().height(60.dp)) { Text("Sync-Paket importieren") }
        }

        item {
            GoogleServicePlaceholderCard(vm)
        }

        if (AccessPolicy.canShowQr(s)) {
            item {
                Divider()
                Text("Team-QR-Code. Nur du als Teamleiter stellst ihn bereit. Wer ihn scannt, ist direkt im Team. Der QR-Code ist 10 Minuten gültig.")
                vm.inviteText()?.let { QrCodeImage(it) }
            }
            item { TeamMembersCard(s) }
        }

        if (AccessPolicy.canExportForAuthority(s)) {
            item {
                Button(onClick = { vm.exportCsv(context, "Eilenburg") }, modifier = Modifier.fillMaxWidth().height(60.dp)) { Text("Liste für Stadtverwaltung teilen") }
            }
        }
    }
}

@Composable
fun GoogleServicePlaceholderCard(vm: PlakatRadarViewModel) {
    var checked by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Google-Service-Sync", style = MaterialTheme.typography.titleMedium)
            Text("Vorgesehen für eine spätere Online-Teilen-Funktion. Aktuell noch nicht aktiv.")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Google-Service nutzen")
                Switch(
                    checked = checked,
                    onCheckedChange = { wantsOn ->
                        if (wantsOn) {
                            checked = false
                            vm.showGoogleServiceNotImplemented()
                        }
                    }
                )
            }
            Text("Der Schalter geht wieder aus, bis der Dienst implementiert ist.")
        }
    }
}

@Composable
fun TeamMembersCard(s: LocalTeamState) {
    val members = s.devices.filter { it.role == MemberRole.MEMBER && !it.blocked }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Teammitglieder", style = MaterialTheme.typography.titleMedium)
            if (members.isEmpty()) {
                Text("Noch keine Teammitglieder synchronisiert.")
            } else {
                members.forEach { device -> Text(device.displayName) }
            }
        }
    }
}

@Composable
fun RemovalReminderCard(s: LocalTeamState) {
    val now = System.currentTimeMillis()
    val active = s.posters.filter { it.status != PosterStatus.REMOVED }
    val due = active.filter { it.plannedRemovalAt != null && it.plannedRemovalAt <= now }
    val soon = active.filter { it.plannedRemovalAt != null && it.plannedRemovalAt in (now + 1)..(now + 3L * 24L * 60L * 60L * 1000L) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Abnahme-Erinnerung", style = MaterialTheme.typography.titleMedium)
            Text("Noch nicht entfernt: ${active.size}")
            if (due.isNotEmpty()) Text("Überfällig: ${due.size} Plakate")
            if (soon.isNotEmpty()) Text("Bald fällig: ${soon.size} Plakate in den nächsten 3 Tagen")
            if (due.isEmpty() && soon.isEmpty()) Text("Aktuell keine dringende Abnahme offen.")
        }
    }
}

@Composable
fun AddPosterScreen(vm: PlakatRadarViewModel) {
    val context = LocalContext.current
    var address by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var internal by remember { mutableStateOf("") }
    var removalDaysText by remember { mutableStateOf("14") }
    var type by remember { mutableStateOf(PosterType.LAMP_POST) }
    var photoTaken by remember { mutableStateOf(false) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> photoTaken = ok }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Plakat hinzufügen", style = MaterialTheme.typography.headlineMedium)
        Button(onClick = { camera.launch(vm.preparePhotoFile()) }, modifier = Modifier.fillMaxWidth().height(60.dp)) { Text(if (photoTaken) "Foto neu aufnehmen" else "Foto aufnehmen") }
        OutlinedTextField(address, { address = it }, label = { Text("Standort / Straße") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(note, { note = it }, label = { Text("Bemerkung für Stadtverwaltung") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(internal, { internal = it }, label = { Text("Interne Notiz") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(removalDaysText, { removalDaysText = it.filter { ch -> ch.isDigit() }.take(3) }, label = { Text("Abnahme in Tagen") }, modifier = Modifier.fillMaxWidth())
        Dropdown(type, { type = it })
        Button(onClick = {
            val fused = LocationServices.getFusedLocationProviderClient(context)
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fused.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) {
                        vm.addPoster(loc.latitude, loc.longitude, address, type, note, internal, removalDaysText.toLongOrNull() ?: 14L)
                    } else {
                        Toast.makeText(context, "Standort konnte nicht ermittelt werden. Bitte kurz nach draußen gehen oder GPS aktivieren.", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                Toast.makeText(context, "Bitte zuerst die Standort-Berechtigung erlauben.", Toast.LENGTH_LONG).show()
            }
        }, modifier = Modifier.fillMaxWidth().height(64.dp)) { Text("Speichern") }
    }
}

@Composable
fun Dropdown(value: PosterType, onChange: (PosterType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Button(onClick = { open = true }) { Text("Art: ${value.name}") }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            PosterType.values().forEach { item ->
                DropdownMenuItem(text = { Text(item.name) }, onClick = { onChange(item); open = false })
            }
        }
    }
}

@Composable
fun PosterListScreen(vm: PlakatRadarViewModel) {
    val s = vm.ui.local
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(s.posters) { p ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(p.addressHint.ifBlank { "Standort ohne Text" }, style = MaterialTheme.typography.titleMedium)
                    Text("Status: ${statusText(p.status)} · von ${p.createdByName}")
                    Text("GPS: ${p.latitude}, ${p.longitude}")
                    Text("Abnahme bis: ${formatDate(p.plannedRemovalAt)}")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button({ vm.updateStatus(p, PosterStatus.CHECKED) }) { Text("OK") }
                        Button({ vm.updateStatus(p, PosterStatus.DAMAGED) }) { Text("Kaputt") }
                        Button({ vm.updateStatus(p, PosterStatus.REMOVED) }) { Text("Entfernt") }
                    }
                    Button({ openNavigation(context, p.latitude, p.longitude, p.addressHint.ifBlank { "Plakat" }) }) {
                        Text("Weg dorthin")
                    }
                }
            }
        }
    }
}

@Composable
fun NearbyPostersScreen(vm: PlakatRadarViewModel) {
    val context = LocalContext.current
    var currentLat by remember { mutableStateOf<Double?>(null) }
    var currentLng by remember { mutableStateOf<Double?>(null) }

    val sorted = remember(currentLat, currentLng, vm.ui.local.posters) {
        val lat = currentLat
        val lng = currentLng
        if (lat == null || lng == null) emptyList()
        else vm.ui.local.posters
            .filter { it.status != PosterStatus.REMOVED }
            .map { it to distanceMeters(lat, lng, it.latitude, it.longitude) }
            .sortedBy { it.second }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Plakate in meiner Nähe", style = MaterialTheme.typography.headlineMedium)
        Button(onClick = {
            val fused = LocationServices.getFusedLocationProviderClient(context)
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fused.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) {
                        currentLat = loc.latitude
                        currentLng = loc.longitude
                    } else {
                        Toast.makeText(context, "Standort konnte nicht ermittelt werden.", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                Toast.makeText(context, "Bitte zuerst Standort-Berechtigung erlauben.", Toast.LENGTH_LONG).show()
            }
        }, modifier = Modifier.fillMaxWidth().height(60.dp)) { Text("Meinen Standort aktualisieren") }

        if (currentLat == null || currentLng == null) {
            Text("Tippe auf den Button, dann zeigt die App die nächstgelegenen Plakate.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(sorted) { item ->
                    val p = item.first
                    val meters = item.second
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${meters.roundToInt()} m · ${p.addressHint.ifBlank { "Standort ohne Text" }}", style = MaterialTheme.typography.titleMedium)
                            Text("Status: ${statusText(p.status)}")
                            Button({ openNavigation(context, p.latitude, p.longitude, p.addressHint.ifBlank { "Plakat" }) }) {
                                Text("Weg dorthin")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PosterMapScreen(posters: List<Poster>) {
    val context = LocalContext.current
    AndroidView(modifier = Modifier.fillMaxSize(), factory = {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(14.0)
            controller.setCenter(GeoPoint(posters.firstOrNull()?.latitude ?: 51.4592, posters.firstOrNull()?.longitude ?: 12.6331))
        }
    }, update = { map ->
        map.overlays.clear()
        posters.forEach { p ->
            Marker(map).apply {
                position = GeoPoint(p.latitude, p.longitude)
                title = p.addressHint.ifBlank { statusText(p.status) }
                snippet = "${statusText(p.status)} · ${p.createdByName} · Tippen: Weg dorthin"
                icon = statusMarkerDrawable(context, p.status)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setOnMarkerClickListener { marker, _ ->
                    marker.showInfoWindow()
                    openNavigation(
                        context = context,
                        latitude = p.latitude,
                        longitude = p.longitude,
                        label = p.addressHint.ifBlank { "Plakat ${statusText(p.status)}" }
                    )
                    true
                }
                map.overlays.add(this)
            }
        }
        map.invalidate()
    })
}

fun statusMarkerDrawable(context: Context, status: PosterStatus): Drawable {
    val drawable = GradientDrawable()
    drawable.shape = GradientDrawable.OVAL
    drawable.setColor(statusColor(status))
    drawable.setStroke(4, Color.WHITE)
    drawable.setSize(44, 44)
    return drawable
}

fun statusColor(status: PosterStatus): Int = when (status) {
    PosterStatus.HANGING -> Color.rgb(46, 160, 67)
    PosterStatus.CHECKED -> Color.rgb(31, 111, 235)
    PosterStatus.DAMAGED, PosterStatus.MISSING -> Color.rgb(218, 54, 51)
    PosterStatus.REPLACED -> Color.rgb(227, 179, 65)
    PosterStatus.REMOVED -> Color.rgb(120, 124, 130)
}

fun statusText(status: PosterStatus): String = when (status) {
    PosterStatus.HANGING -> "hängt"
    PosterStatus.CHECKED -> "kontrolliert"
    PosterStatus.DAMAGED -> "beschädigt"
    PosterStatus.MISSING -> "fehlt"
    PosterStatus.REPLACED -> "ersetzt"
    PosterStatus.REMOVED -> "entfernt"
}

fun openNavigation(context: Context, latitude: Double, longitude: Double, label: String) {
    val encodedLabel = Uri.encode(label)
    val geoUri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($encodedLabel)")
    val geoIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    runCatching {
        context.startActivity(geoIntent)
    }.recoverCatching {
        val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$latitude,$longitude")
        val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(webIntent)
    }.onFailure {
        Toast.makeText(context, "Keine Karten-App gefunden.", Toast.LENGTH_LONG).show()
    }
}

fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val earthRadius = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2.0) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2.0)
    return earthRadius * 2 * atan2(sqrt(a), sqrt(1 - a))
}

fun formatDate(value: Long?): String {
    if (value == null) return "nicht gesetzt"
    return SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date(value))
}

@Composable
fun QrCodeImage(text: String) {
    val bitmap = remember(text) { qrBitmap(text, 640, 640) }
    Image(bitmap.asImageBitmap(), contentDescription = "Team-QR-Code", modifier = Modifier.size(280.dp))
}

fun qrBitmap(text: String, width: Int, height: Int): Bitmap {
    val matrix: BitMatrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, width, height)
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
    for (x in 0 until width) for (y in 0 until height) bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
    return bmp
}

fun nearbyAndAppPermissions(): Array<String> = buildList {
    add(Manifest.permission.CAMERA)
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    if (Build.VERSION.SDK_INT >= 31) {
        add(Manifest.permission.BLUETOOTH_ADVERTISE)
        add(Manifest.permission.BLUETOOTH_CONNECT)
        add(Manifest.permission.BLUETOOTH_SCAN)
    }
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
}.toTypedArray()

fun missingAppPermissions(context: Context): Array<String> =
    nearbyAndAppPermissions()
        .filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        .toTypedArray()
