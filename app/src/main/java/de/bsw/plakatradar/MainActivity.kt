package de.bsw.plakatradar

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import de.bsw.plakatradar.core.*
import de.bsw.plakatradar.data.LocalRepository
import de.bsw.plakatradar.sync.SyncBundleCodec
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { PlakatRadarApp() } }
    }
}

data class AppUiState(
    val local: LocalTeamState,
    val lastLog: String = "Bereit.",
    val error: String? = null,
    val pendingPhotoFileName: String? = null
)

class PlakatRadarViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = LocalRepository(app)
    private val codec = SyncBundleCodec(app, repo)

    var ui by mutableStateOf(AppUiState(repo.load()))
        private set

    private fun authority(): String = getApplication<Application>().packageName + ".fileprovider"

    fun createLeaderTeam(teamName: String, leaderName: String) {
        runCatching { repo.createLeaderTeam(teamName.ifBlank { "Plakat-Team" }, leaderName.ifBlank { "Teamleiter" }) }
            .onSuccess { ui = ui.copy(local = it, lastLog = "Team erstellt. Du bist Teamleiter.") }
            .onFailure { fail(it) }
    }

    fun enterWithoutQr(memberName: String) {
        val current = ui.local
        val name = memberName.ifBlank { "Teammitglied" }
        val member = DeviceRecord(current.deviceId, name, MemberRole.MEMBER, approved = true)
        val updated = current.copy(
            deviceName = name,
            role = MemberRole.MEMBER,
            teamId = "offline-${current.deviceId}",
            teamName = "Ohne Team-QR",
            teamSecret = null,
            devices = listOf(member)
        )
        repo.save(updated)
        ui = ui.copy(local = updated, lastLog = "Ohne QR gestartet. Export ist möglich, Sync braucht später Team-QR.")
    }

    fun joinByQr(raw: String, memberName: String) {
        runCatching { repo.joinByInvite(TeamInvite.decode(raw), memberName.ifBlank { "Teammitglied" }) }
            .onSuccess { ui = ui.copy(local = it, lastLog = "Teamleiter-QR gescannt. Team-Zugang aktiv.") }
            .onFailure { fail(it) }
    }

    fun inviteText(locked: Boolean = false): String? {
        val s = ui.local
        if (!AccessPolicy.canShowQr(s)) return null
        val expiresAt = if (locked) {
            Instant.now().plus(3650, ChronoUnit.DAYS).toEpochMilli()
        } else {
            Instant.now().plusSeconds(TeamInvite.DEFAULT_TTL_SECONDS).toEpochMilli()
        }
        return TeamInvite(
            teamId = s.teamId ?: return null,
            teamName = s.teamName ?: "Plakat-Team",
            leaderName = s.deviceName,
            leaderDeviceId = s.deviceId,
            teamSecret = s.teamSecret ?: return null,
            expiresAt = expiresAt
        ).encodeForQr()
    }

    fun preparePhotoFile(): Uri {
        val fileName = "poster_${UUID.randomUUID()}.jpg"
        val file = File(repo.photosDir, fileName)
        ui = ui.copy(pendingPhotoFileName = fileName)
        return FileProvider.getUriForFile(getApplication(), authority(), file)
    }

    fun addPoster(lat: Double, lng: Double, address: String, type: PosterType, officialNote: String, internalNote: String, removalDays: Long) {
        val s = ui.local
        val teamId = s.teamId ?: return failText("Bitte zuerst Team erstellen, QR scannen oder ohne QR starten.")
        if (!AccessPolicy.canAddPoster(s)) return failText("Dieses Gerät darf noch keine Plakate erfassen.")
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
            plannedRemovalAt = Instant.now().plus(removalDays.coerceIn(1, 120), ChronoUnit.DAYS).toEpochMilli(),
            officialNote = officialNote,
            internalNote = internalNote
        )
        val updated = repo.addPoster(s, poster)
        ui = ui.copy(local = updated, pendingPhotoFileName = null, lastLog = "Plakat gespeichert.")
    }

    fun updateStatus(poster: Poster, status: PosterStatus) {
        val updated = repo.updateStatus(ui.local, poster, status)
        ui = ui.copy(local = updated, lastLog = "Status geändert: ${statusText(status)}")
    }

    fun deletePoster(poster: Poster) {
        val updated = repo.deletePoster(ui.local, poster)
        ui = ui.copy(local = updated, lastLog = "Plakat gelöscht.")
    }

    fun exportAuthorityZip(context: Context, municipality: String) {
        runCatching {
            if (!AccessPolicy.canExportForAuthority(ui.local)) error("Bitte zuerst Team erstellen, QR scannen oder ohne QR starten.")
            val cleanName = municipality.ifBlank { "Kommune" }.replace(Regex("[^A-Za-z0-9ÄÖÜäöüß_-]"), "_")
            val file = File(context.cacheDir, "PlakatRadar_Verwaltung_${cleanName}_${System.currentTimeMillis()}.zip")
            OfficialExport.writeZip(
                state = ui.local,
                municipality = municipality.ifBlank { "Kommune" },
                photosDir = repo.photosDir,
                outputFile = file
            )
            val uri = FileProvider.getUriForFile(context, authority(), file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "PlakatRadar Verwaltungs-Export")
                putExtra(Intent.EXTRA_TEXT, "ZIP-Datei mit Plakatliste und Fotos.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Verwaltungs-Export teilen"))
            ui = ui.copy(lastLog = "Verwaltungs-ZIP wurde vorbereitet.")
        }.onFailure { fail(it) }
    }

    fun shareSyncBundle(context: Context) {
        runCatching {
            if (!AccessPolicy.canShareSyncBundle(ui.local)) error("Sync-Pakete brauchen den Teamleiter-QR.")
            val file = codec.createBundle(repo.toSnapshot(ui.local), ui.local.teamSecret ?: error("Kein Team-Schlüssel."))
            val uri = FileProvider.getUriForFile(context, authority(), file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "PlakatRadar Sync-Paket")
                putExtra(Intent.EXTRA_TEXT, "Verschlüsseltes PlakatRadar Sync-Paket für ${ui.local.teamName ?: "das Team"}.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Sync-Paket teilen"))
            ui = ui.copy(lastLog = "Sync-Paket wurde vorbereitet.")
        }.onFailure { fail(it) }
    }

    fun importSyncBundle(uri: Uri) {
        runCatching {
            if (!AccessPolicy.canSync(ui.local)) error("Import braucht den Teamleiter-QR.")
            val file = codec.copyIncomingUriToBundle(uri)
            repo.mergeAndSave(ui.local, codec.importVerifiedBundle(file, ui.local))
        }.onSuccess {
            ui = ui.copy(local = it, lastLog = "Sync-Paket importiert.")
        }.onFailure { fail(it) }
    }

    fun requireTeamQr() = failText("Diese Funktion braucht den Teamleiter-QR.")
    fun clearError() { ui = ui.copy(error = null) }
    private fun failText(text: String) { ui = ui.copy(error = text) }
    private fun fail(t: Throwable) { ui = ui.copy(error = t.message ?: t.toString()) }
}

@Composable
fun PlakatRadarApp(vm: PlakatRadarViewModel = viewModel()) {
    val state = vm.ui
    Surface(Modifier.fillMaxSize()) {
        if (state.local.role == null) SetupScreen(vm) else DashboardScreen(vm)
    }
    state.error?.let {
        AlertDialog(
            onDismissRequest = vm::clearError,
            confirmButton = { Button(onClick = vm::clearError) { Text("OK") } },
            title = { Text("Hinweis") },
            text = { Text(it) }
        )
    }
}

@Composable
fun SetupScreen(vm: PlakatRadarViewModel) {
    var mode by remember { mutableStateOf<String?>(null) }
    var teamName by remember { mutableStateOf("BSW Nordsachsen Plakatierung") }
    var myName by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { vm.joinByQr(it, myName.ifBlank { "Teammitglied" }) }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("PlakatRadar", style = MaterialTheme.typography.headlineLarge)
        Text("Für Plakate erfassen, kontrollieren, Karte und Export.")
        Button(onClick = { mode = "leader" }, modifier = Modifier.fillMaxWidth().height(64.dp)) { Text("Ich bin Teamleiter") }
        Button(onClick = { mode = "member" }, modifier = Modifier.fillMaxWidth().height(64.dp)) { Text("Ich bin Teammitglied") }
        Divider()
        when (mode) {
            "leader" -> {
                OneLineField(teamName, { teamName = it }, "Teamname")
                OneLineField(myName, { myName = it }, "Dein Name")
                Button(onClick = { focus.clearFocus(); vm.createLeaderTeam(teamName, myName) }, modifier = Modifier.fillMaxWidth().height(60.dp)) { Text("Team erstellen") }
            }
            "member" -> {
                OneLineField(myName, { myName = it }, "Dein Name")
                Button(onClick = { focus.clearFocus(); scanner.launch(ScanOptions().setPrompt("Teamleiter-QR scannen").setBeepEnabled(false)) }, modifier = Modifier.fillMaxWidth().height(60.dp)) { Text("Teamleiter-QR scannen") }
                OutlinedButton(onClick = { focus.clearFocus(); vm.enterWithoutQr(myName) }, modifier = Modifier.fillMaxWidth().height(60.dp)) { Text("Ohne QR lokal starten") }
            }
        }
    }
}

@Composable
fun DashboardScreen(vm: PlakatRadarViewModel) {
    var tab by remember { mutableStateOf("home") }
    val context = LocalContext.current
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    LaunchedEffect(Unit) {
        val missing = missingPermissions(context)
        if (missing.isNotEmpty()) permissions.launch(missing)
    }
    Scaffold(bottomBar = { BottomNav(tab) { tab = it } }) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (tab) {
                "home" -> HomeScreen(vm) { tab = it }
                "add" -> AddPosterScreen(vm)
                "list" -> PosterListScreen(vm)
                "map" -> PosterMapScreen(vm.ui.local.posters)
                "more" -> MoreScreen(vm) { tab = it }
            }
        }
    }
}

@Composable
fun BottomNav(tab: String, onTab: (String) -> Unit) {
    NavigationBar {
        NavigationBarItem(tab == "home", { onTab("home") }, icon = { Text("⌂") }, label = { Text("Start") })
        NavigationBarItem(tab == "add", { onTab("add") }, icon = { Text("+") }, label = { Text("Erfassen") })
        NavigationBarItem(tab == "list", { onTab("list") }, icon = { Text("≡") }, label = { Text("Liste") })
        NavigationBarItem(tab == "map", { onTab("map") }, icon = { Text("⌖") }, label = { Text("Karte") })
        NavigationBarItem(tab == "more", { onTab("more") }, icon = { Text("…") }, label = { Text("Mehr") })
    }
}

@Composable
fun HomeScreen(vm: PlakatRadarViewModel, onNavigate: (String) -> Unit) {
    val s = vm.ui.local
    val active = s.posters.count { it.status != PosterStatus.REMOVED }
    val problems = s.posters.count { it.status == PosterStatus.DAMAGED || it.status == PosterStatus.MISSING }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("PlakatRadar", style = MaterialTheme.typography.headlineMedium)
                    Text(s.teamName ?: "Ohne Team")
                    Text(if (s.role == MemberRole.LEADER) "Teamleiter" else "Teammitglied")
                    Text("Aktive Plakate: $active")
                    Text("Probleme: $problems")
                    Text("Meldung: ${vm.ui.lastLog}")
                }
            }
        }
        item { ActionCard("Plakat erfassen", "Foto, GPS, Art und Notiz speichern.", "Erfassen") { onNavigate("add") } }
        item { ActionCard("Liste öffnen", "Plakate kontrollieren und Status ändern.", "Liste") { onNavigate("list") } }
        item { ActionCard("Karte öffnen", "Alle Standorte sehen.", "Karte") { onNavigate("map") } }
        item { RemovalCard(s) }
    }
}

@Composable
fun MoreScreen(vm: PlakatRadarViewModel, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val s = vm.ui.local
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result -> result.contents?.let { vm.joinByQr(it, s.deviceName.ifBlank { "Teammitglied" }) } }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importSyncBundle(uri) }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Mehr", style = MaterialTheme.typography.headlineMedium) }
        item { ActionCard("Verwaltungs-Export", "ZIP mit plakatliste.csv und Fotos erstellen.", "ZIP teilen", AccessPolicy.canExportForAuthority(s)) { vm.exportAuthorityZip(context, "Eilenburg") } }
        item { ActionCard("Sync-Paket teilen", "Verschlüsseltes Paket für Teamgeräte teilen.", "Teilen", AccessPolicy.canShareSyncBundle(s)) { vm.shareSyncBundle(context) } }
        item { ActionCard("Sync-Paket importieren", "Erhaltenes Paket auswählen.", "Importieren", AccessPolicy.canSync(s)) { importLauncher.launch(arrayOf("application/octet-stream", "application/zip", "*/*")) } }
        if (!AccessPolicy.hasTeamAccess(s)) item { ActionCard("Teamleiter-QR scannen", "Aktiviert Team-Sync und Teamfunktionen.", "QR scannen") { scanner.launch(ScanOptions().setPrompt("Teamleiter-QR scannen").setBeepEnabled(false)) } }
        if (AccessPolicy.canShowQr(s)) item { TeamQrCard(vm) }
        item { ActionCard("Plakate in der Nähe", "Liste nach Entfernung öffnen.", "Nähe") { onNavigate("list") } }
        item { AppCard(context) }
    }
}

@Composable
fun ActionCard(title: String, text: String, button: String, enabled: Boolean = true, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(text)
            Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(button) }
        }
    }
}

@Composable
fun OneLineField(value: String, onChange: (String) -> Unit, label: String) {
    val focus = LocalFocusManager.current
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
}

@Composable
fun MultiLineField(value: String, onChange: (String) -> Unit, label: String) {
    val focus = LocalFocusManager.current
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
}

@Composable
fun AddPosterScreen(vm: PlakatRadarViewModel) {
    val context = LocalContext.current
    var official by remember { mutableStateOf("") }
    var internal by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("14") }
    var type by remember { mutableStateOf(PosterType.LAMP_POST) }
    var photoTaken by remember { mutableStateOf(false) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { photoTaken = it }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Plakat erfassen", style = MaterialTheme.typography.headlineMedium)
        Button(onClick = { camera.launch(vm.preparePhotoFile()) }, modifier = Modifier.fillMaxWidth().height(60.dp)) { Text(if (photoTaken) "Foto neu aufnehmen" else "Foto aufnehmen") }
        Text("Der Standort wird per GPS übernommen.")
        MultiLineField(official, { official = it }, "Bemerkung für Verwaltung")
        MultiLineField(internal, { internal = it }, "Interne Notiz")
        OneLineField(days, { days = it.filter { ch -> ch.isDigit() }.take(3) }, "Abnahme in Tagen")
        PosterTypePicker(type) { type = it }
        Button(onClick = {
            val fused = LocationServices.getFusedLocationProviderClient(context)
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fused.lastLocation.addOnSuccessListener { loc ->
                    if (loc == null) Toast.makeText(context, "Standort konnte nicht ermittelt werden.", Toast.LENGTH_LONG).show() else {
                        vm.addPoster(loc.latitude, loc.longitude, reverseGeocodeAddress(context, loc.latitude, loc.longitude), type, official, internal, days.toLongOrNull() ?: 14L)
                        official = ""; internal = ""; photoTaken = false
                    }
                }
            } else Toast.makeText(context, "Bitte Standort-Berechtigung erlauben.", Toast.LENGTH_LONG).show()
        }, modifier = Modifier.fillMaxWidth().height(64.dp)) { Text("Speichern") }
    }
}

@Composable
fun PosterTypePicker(value: PosterType, onChange: (PosterType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Button(onClick = { open = true }) { Text("Art: ${typeText(value)}") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            PosterType.values().forEach { item -> DropdownMenuItem(text = { Text(typeText(item)) }, onClick = { onChange(item); open = false }) }
        }
    }
}

@Composable
fun PosterListScreen(vm: PlakatRadarViewModel) {
    val context = LocalContext.current
    val posters = vm.ui.local.posters
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (posters.isEmpty()) item { Text("Noch keine Plakate erfasst.") }
        items(posters, key = { it.id }) { poster -> PosterCard(poster, vm, context) }
    }
}

@Composable
fun PosterCard(poster: Poster, vm: PlakatRadarViewModel, context: Context) {
    val photo = remember(poster.localPhotoFileName) { poster.localPhotoFileName?.let { File(context.filesDir, "photos/$it") }?.takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.absolutePath) } }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(poster.addressHint.ifBlank { "Standort ohne Adresse" }, style = MaterialTheme.typography.titleMedium)
            Text("${typeText(poster.type)} · ${statusText(poster.status)}")
            Text("GPS: ${"%.5f".format(Locale.GERMANY, poster.latitude)}, ${"%.5f".format(Locale.GERMANY, poster.longitude)}")
            if (poster.officialNote.isNotBlank()) Text("Verwaltung: ${poster.officialNote}")
            if (poster.internalNote.isNotBlank()) Text("Intern: ${poster.internalNote}")
            photo?.let { Image(bitmap = it.asImageBitmap(), contentDescription = "Plakatfoto", modifier = Modifier.fillMaxWidth().height(180.dp)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallButton("OK") { vm.updateStatus(poster, PosterStatus.CHECKED) }
                SmallButton("Kaputt") { vm.updateStatus(poster, PosterStatus.DAMAGED) }
                SmallButton("Fehlt") { vm.updateStatus(poster, PosterStatus.MISSING) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallButton("Entfernt") { vm.updateStatus(poster, PosterStatus.REMOVED) }
                SmallButton("Weg") { openMaps(context, poster.latitude, poster.longitude) }
                SmallButton("Löschen") { vm.deletePoster(poster) }
            }
        }
    }
}

@Composable
fun RowScope.SmallButton(text: String, onClick: () -> Unit) { OutlinedButton(onClick = onClick, modifier = Modifier.weight(1f)) { Text(text) } }

@Composable
fun PosterMapScreen(posters: List<Poster>) {
    val context = LocalContext.current
    AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
        Configuration.getInstance().userAgentValue = ctx.packageName
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(14.0)
            controller.setCenter(posters.firstOrNull()?.let { GeoPoint(it.latitude, it.longitude) } ?: GeoPoint(51.459, 12.633))
        }
    }, update = { map ->
        map.overlays.clear()
        posters.forEach { poster ->
            Marker(map).apply {
                position = GeoPoint(poster.latitude, poster.longitude)
                title = poster.addressHint.ifBlank { statusText(poster.status) }
                snippet = "${typeText(poster.type)} · ${statusText(poster.status)}"
                icon = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(markerColor(poster.status)); setStroke(3, Color.WHITE); setSize(44, 44) }
                setOnMarkerClickListener { _, _ -> openMaps(context, poster.latitude, poster.longitude); true }
            }.also { map.overlays.add(it) }
        }
        map.invalidate()
    })
}

@Composable
fun RemovalCard(s: LocalTeamState) {
    val now = System.currentTimeMillis()
    val active = s.posters.filter { it.status != PosterStatus.REMOVED }
    val due = active.count { it.plannedRemovalAt != null && it.plannedRemovalAt <= now }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("Abnahme", style = MaterialTheme.typography.titleMedium); Text("Noch nicht entfernt: ${active.size}"); Text(if (due > 0) "Überfällig: $due" else "Keine überfällige Abnahme.") } }
}

@Composable
fun TeamQrCard(vm: PlakatRadarViewModel) {
    var locked by remember { mutableStateOf(false) }
    val qr = remember(locked, vm.ui.local.teamId, vm.ui.local.teamSecret) { vm.inviteText(locked) }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Team-QR", style = MaterialTheme.typography.titleMedium); Row(verticalAlignment = Alignment.CenterVertically) { Text(if (locked) "QR bleibt aktiv" else "QR läuft nach kurzer Zeit ab", modifier = Modifier.weight(1f)); Switch(checked = locked, onCheckedChange = { locked = it }) }; qr?.let { QrCodeImage(it) } } }
}

@Composable
fun QrCodeImage(text: String) {
    val bitmap = remember(text) { qrBitmap(text) }
    Image(bitmap = bitmap.asImageBitmap(), contentDescription = "QR-Code", modifier = Modifier.size(240.dp))
}

@Composable
fun AppCard(context: Context) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("App verwalten", style = MaterialTheme.typography.titleMedium); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { openAppSettings(context) }, modifier = Modifier.weight(1f)) { Text("Einstellungen") }; Button(onClick = { openUpdatePage(context) }, modifier = Modifier.weight(1f)) { Text("Update") } } } }
}

fun missingPermissions(context: Context): Array<String> = buildList {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.CAMERA)
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.ACCESS_FINE_LOCATION)
    if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.BLUETOOTH_SCAN)
    if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.BLUETOOTH_CONNECT)
    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.NEARBY_WIFI_DEVICES)
}.toTypedArray()

fun reverseGeocodeAddress(context: Context, lat: Double, lng: Double): String = runCatching {
    @Suppress("DEPRECATION")
    Geocoder(context, Locale.GERMANY).getFromLocation(lat, lng, 1)?.firstOrNull()?.let { addr -> listOfNotNull(addr.thoroughfare, addr.subThoroughfare, addr.postalCode, addr.locality).filter { it.isNotBlank() }.joinToString(" ") }
}.getOrNull().orEmpty()

fun qrBitmap(text: String): Bitmap {
    val matrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, 512, 512)
    val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
    for (x in 0 until 512) for (y in 0 until 512) bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
    return bitmap
}

fun openMaps(context: Context, lat: Double, lng: Double) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$lat,$lng"))) }
fun openAppSettings(context: Context) { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
fun openUpdatePage(context: Context) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/privatdavidgottschall-sudo/Plakat-Radar/actions/workflows/android-debug-apk.yml"))) }

fun statusText(status: PosterStatus): String = when (status) {
    PosterStatus.HANGING -> "Hängt"
    PosterStatus.CHECKED -> "Kontrolliert"
    PosterStatus.DAMAGED -> "Beschädigt"
    PosterStatus.MISSING -> "Fehlt"
    PosterStatus.REPLACED -> "Ersetzt"
    PosterStatus.REMOVED -> "Entfernt"
}

fun typeText(type: PosterType): String = when (type) {
    PosterType.LAMP_POST -> "Laternenmast"
    PosterType.FENCE -> "Zaun"
    PosterType.BANNER -> "Banner"
    PosterType.TRIANGLE_STAND -> "Dreieckständer"
    PosterType.LARGE_FORMAT -> "Großformat"
    PosterType.OTHER -> "Sonstiges"
}

fun markerColor(status: PosterStatus): Int = when (status) {
    PosterStatus.HANGING -> Color.rgb(46, 125, 50)
    PosterStatus.CHECKED -> Color.rgb(25, 118, 210)
    PosterStatus.DAMAGED, PosterStatus.MISSING -> Color.rgb(198, 40, 40)
    PosterStatus.REPLACED -> Color.rgb(249, 168, 37)
    PosterStatus.REMOVED -> Color.rgb(117, 117, 117)
}
