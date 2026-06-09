from pathlib import Path


def replace(text: str, old: str, new: str) -> str:
    if old in text:
        return text.replace(old, new)
    return text


main_path = Path("app/src/main/java/de/bsw/plakatradar/MainActivity.kt")
text = main_path.read_text(encoding="utf-8")

text = replace(
    text,
    "        enableScreenshotProtection()\n        super.onCreate(savedInstanceState)",
    "        // enableScreenshotProtection() // Vorläufig deaktiviert, Funktion bleibt erhalten.\n        super.onCreate(savedInstanceState)",
)

if "import android.location.Geocoder" not in text:
    text = replace(
        text,
        "import android.graphics.drawable.GradientDrawable\n",
        "import android.graphics.drawable.GradientDrawable\nimport android.location.Geocoder\n",
    )

if "import androidx.compose.foundation.text.KeyboardActions" not in text:
    text = replace(
        text,
        "import androidx.compose.foundation.Image\n",
        "import androidx.compose.foundation.Image\nimport androidx.compose.foundation.text.KeyboardActions\nimport androidx.compose.foundation.text.KeyboardOptions\n",
    )

if "import androidx.compose.ui.platform.LocalFocusManager" not in text:
    text = replace(
        text,
        "import androidx.compose.ui.platform.LocalContext\n",
        "import androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.platform.LocalFocusManager\nimport androidx.compose.ui.text.input.ImeAction\n",
    )

old_invite = '''    fun inviteText(): String? {
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
'''
new_invite = '''    fun inviteText(locked: Boolean = false): String? {
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
'''
text = replace(text, old_invite, new_invite)

text = replace(
    text,
    '    var myName by remember { mutableStateOf("") }\n    val scanner = rememberLauncherForActivityResult',
    '    var myName by remember { mutableStateOf("") }\n    val focusManager = LocalFocusManager.current\n    fun closeKeyboard() { focusManager.clearFocus(force = true) }\n    val scanner = rememberLauncherForActivityResult',
)
text = replace(
    text,
    '                OutlinedTextField(teamName, { teamName = it }, label = { Text("Teamname") }, modifier = Modifier.fillMaxWidth())',
    '                OutlinedTextField(\n                    value = teamName,\n                    onValueChange = { teamName = it },\n                    label = { Text("Teamname") },\n                    modifier = Modifier.fillMaxWidth(),\n                    singleLine = true,\n                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),\n                    keyboardActions = KeyboardActions(onDone = { closeKeyboard() })\n                )',
)
text = replace(
    text,
    '                OutlinedTextField(myName, { myName = it }, label = { Text("Dein Name") }, modifier = Modifier.fillMaxWidth())',
    '                OutlinedTextField(\n                    value = myName,\n                    onValueChange = { myName = it },\n                    label = { Text("Dein Name") },\n                    modifier = Modifier.fillMaxWidth(),\n                    singleLine = true,\n                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),\n                    keyboardActions = KeyboardActions(onDone = { closeKeyboard() })\n                )',
)
text = replace(
    text,
    '                Button(onClick = { vm.createLeaderTeam(teamName, myName.ifBlank { "Teamleiter" }) }, modifier = Modifier.fillMaxWidth().height(64.dp)) { Text("Team erstellen") }',
    '                Button(onClick = { closeKeyboard(); vm.createLeaderTeam(teamName, myName.ifBlank { "Teamleiter" }) }, modifier = Modifier.fillMaxWidth().height(64.dp)) { Text("Team erstellen") }',
)
text = replace(
    text,
    '                Button(onClick = { scanner.launch(ScanOptions().setPrompt("QR-Code vom Teamleiter scannen").setBeepEnabled(false)) }, modifier = Modifier.fillMaxWidth().height(64.dp)) { Text("QR-Code scannen") }',
    '                Button(onClick = { closeKeyboard(); scanner.launch(ScanOptions().setPrompt("QR-Code vom Teamleiter scannen").setBeepEnabled(false)) }, modifier = Modifier.fillMaxWidth().height(64.dp)) { Text("QR-Code scannen") }',
)

text = replace(
    text,
    '    val context = LocalContext.current\n    var address by remember { mutableStateOf("") }',
    '    val context = LocalContext.current\n    val focusManager = LocalFocusManager.current\n    fun closeKeyboard() { focusManager.clearFocus(force = true) }',
)
text = replace(
    text,
    '        OutlinedTextField(address, { address = it }, label = { Text("Standort / Straße") }, modifier = Modifier.fillMaxWidth())',
    '        Text("Standort wird automatisch per GPS ermittelt und als Straße gespeichert.")',
)
text = replace(
    text,
    '        OutlinedTextField(note, { note = it }, label = { Text("Bemerkung für Stadtverwaltung") }, modifier = Modifier.fillMaxWidth())',
    '        OutlinedTextField(\n            value = note,\n            onValueChange = { note = it },\n            label = { Text("Bemerkung für Stadtverwaltung") },\n            modifier = Modifier.fillMaxWidth(),\n            minLines = 2,\n            maxLines = 4,\n            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),\n            keyboardActions = KeyboardActions(onDone = { closeKeyboard() })\n        )',
)
text = replace(
    text,
    '        OutlinedTextField(internal, { internal = it }, label = { Text("Interne Notiz") }, modifier = Modifier.fillMaxWidth())',
    '        OutlinedTextField(\n            value = internal,\n            onValueChange = { internal = it },\n            label = { Text("Interne Notiz") },\n            modifier = Modifier.fillMaxWidth(),\n            minLines = 2,\n            maxLines = 4,\n            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),\n            keyboardActions = KeyboardActions(onDone = { closeKeyboard() })\n        )',
)
text = replace(
    text,
    '        OutlinedTextField(removalDaysText, { removalDaysText = it.filter { ch -> ch.isDigit() }.take(3) }, label = { Text("Abnahme in Tagen") }, modifier = Modifier.fillMaxWidth())',
    '        OutlinedTextField(\n            value = removalDaysText,\n            onValueChange = { removalDaysText = it.filter { ch -> ch.isDigit() }.take(3) },\n            label = { Text("Abnahme in Tagen") },\n            modifier = Modifier.fillMaxWidth(),\n            singleLine = true,\n            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),\n            keyboardActions = KeyboardActions(onDone = { closeKeyboard() })\n        )',
)
text = replace(
    text,
    '        Button(onClick = {\n            val fused = LocationServices.getFusedLocationProviderClient(context)',
    '        Button(onClick = {\n            closeKeyboard()\n            val fused = LocationServices.getFusedLocationProviderClient(context)',
)
text = replace(
    text,
    '                        vm.addPoster(loc.latitude, loc.longitude, address, type, note, internal, removalDaysText.toLongOrNull() ?: 14L)',
    '                        val detectedAddress = reverseGeocodeAddress(context, loc.latitude, loc.longitude)\n                        vm.addPoster(loc.latitude, loc.longitude, detectedAddress, type, note, internal, removalDaysText.toLongOrNull() ?: 14L)',
)

if "fun deletePoster(poster: Poster)" not in text:
    text = replace(
        text,
        '''    fun updateStatus(poster: Poster, status: PosterStatus) {
        val updated = repo.updateStatus(ui.local, poster, status)
        ui = ui.copy(local = updated, lastLog = "Status geändert.")
        sync?.sendCurrentBundleToAll()
    }
''',
        '''    fun updateStatus(poster: Poster, status: PosterStatus) {
        val updated = repo.updateStatus(ui.local, poster, status)
        ui = ui.copy(local = updated, lastLog = "Status geändert.")
        sync?.sendCurrentBundleToAll()
    }

    fun deletePoster(poster: Poster) {
        val updated = repo.deletePoster(ui.local, poster)
        ui = ui.copy(local = updated, lastLog = "Plakat aus der Liste entfernt.")
        sync?.sendCurrentBundleToAll()
    }
''',
    )

if 'Text("Aus Liste entfernen")' not in text:
    text = replace(
        text,
        '''                        Button({ vm.updateStatus(p, PosterStatus.REMOVED) }) { Text("Entfernt") }
                    }
                    Button({ openNavigation(context, p.latitude, p.longitude, p.addressHint.ifBlank { "Plakat" }) }) {''',
        '''                        Button({ vm.updateStatus(p, PosterStatus.REMOVED) }) { Text("Entfernt") }
                    }
                    Button({ vm.deletePoster(p) }, modifier = Modifier.fillMaxWidth()) { Text("Aus Liste entfernen") }
                    Button({ openNavigation(context, p.latitude, p.longitude, p.addressHint.ifBlank { "Plakat" }) }) {''',
    )

text = replace(
    text,
    '''        if (AccessPolicy.canShowQr(s)) {
            item {
                Divider()
                Text("Team-QR-Code. Nur du als Teamleiter stellst ihn bereit. Wer ihn scannt, ist direkt im Team. Der QR-Code ist jeweils 10 Minuten gültig.")
                vm.inviteText()?.let { QrCodeImage(it) }
            }
            item { TeamMembersCard(s) }
        }
''',
    '''        if (AccessPolicy.canShowQr(s)) {
            item { TeamInviteQrCard(vm) }
            item { TeamMembersCard(s) }
        }
''',
)

if "fun TeamInviteQrCard" not in text:
    text = replace(
        text,
        '''@Composable
fun TeamMembersCard(s: LocalTeamState) {''',
        '''@Composable
fun TeamInviteQrCard(vm: PlakatRadarViewModel) {
    var locked by remember { mutableStateOf(false) }
    var refreshSeed by remember { mutableStateOf(0) }
    var remaining by remember { mutableStateOf(TeamInvite.DEFAULT_TTL_SECONDS.toInt()) }

    LaunchedEffect(locked, refreshSeed) {
        remaining = TeamInvite.DEFAULT_TTL_SECONDS.toInt()
        if (!locked) {
            while (remaining > 0) {
                kotlinx.coroutines.delay(1000)
                remaining -= 1
            }
            refreshSeed += 1
        }
    }

    val qrText = remember(locked, refreshSeed, vm.ui.local.teamId, vm.ui.local.teamSecret, vm.ui.local.deviceName) {
        vm.inviteText(locked)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Divider()
        Text("Team-QR-Code. Nur du als Teamleiter stellst ihn bereit.")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (locked) "🔒 QR bleibt bestehen" else "🔓 Neuer QR in ${remaining}s")
            Switch(checked = locked, onCheckedChange = { locked = it; refreshSeed += 1 })
        }
        Text(if (locked) "Schloss aktiv: Der QR-Code bleibt dauerhaft nutzbar." else "Ohne Schloss ist der QR-Code 1 Minute gültig und aktualisiert sich automatisch.")
        qrText?.let { QrCodeImage(it) }
    }
}

@Composable
fun TeamMembersCard(s: LocalTeamState) {''',
    )

if "fun reverseGeocodeAddress" not in text:
    text = replace(
        text,
        '''fun statusText(status: PosterStatus): String = when (status) {
    PosterStatus.HANGING -> "hängt"
    PosterStatus.CHECKED -> "kontrolliert"
    PosterStatus.DAMAGED -> "beschädigt"
    PosterStatus.MISSING -> "fehlt"
    PosterStatus.REPLACED -> "ersetzt"
    PosterStatus.REMOVED -> "entfernt"
}

fun openNavigation(context: Context, latitude: Double, longitude: Double, label: String) {''',
        '''fun statusText(status: PosterStatus): String = when (status) {
    PosterStatus.HANGING -> "hängt"
    PosterStatus.CHECKED -> "kontrolliert"
    PosterStatus.DAMAGED -> "beschädigt"
    PosterStatus.MISSING -> "fehlt"
    PosterStatus.REPLACED -> "ersetzt"
    PosterStatus.REMOVED -> "entfernt"
}

fun reverseGeocodeAddress(context: Context, latitude: Double, longitude: Double): String {
    val fallback = "GPS: %.6f, %.6f".format(Locale.US, latitude, longitude)
    return runCatching {
        @Suppress("DEPRECATION")
        val found = Geocoder(context, Locale.GERMANY).getFromLocation(latitude, longitude, 1)
        val address = found?.firstOrNull()
        val street = address?.thoroughfare?.takeIf { it.isNotBlank() }?.let { streetName ->
            val number = address.subThoroughfare?.takeIf { it.isNotBlank() }
            if (number != null) "$streetName $number" else streetName
        }
        val city = address?.locality?.takeIf { it.isNotBlank() }
            ?: address?.subAdminArea?.takeIf { it.isNotBlank() }
        listOfNotNull(street, address?.postalCode?.takeIf { it.isNotBlank() }, city)
            .joinToString(", ")
            .ifBlank { address?.getAddressLine(0).orEmpty() }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: fallback
}

fun openNavigation(context: Context, latitude: Double, longitude: Double, label: String) {''',
    )

main_path.write_text(text, encoding="utf-8")

export_path = Path("app/src/main/java/de/bsw/plakatradar/core/OfficialExport.kt")
export_text = export_path.read_text(encoding="utf-8")
export_text = replace(
    export_text,
    'poster.addressHint, poster.latitude.toString(), poster.longitude.toString(),',
    'poster.addressHint.ifBlank { "GPS: ${poster.latitude}, ${poster.longitude}" }, poster.latitude.toString(), poster.longitude.toString(),',
)
export_path.write_text(export_text, encoding="utf-8")

repo_path = Path("app/src/main/java/de/bsw/plakatradar/data/LocalRepository.kt")
repo_text = repo_path.read_text(encoding="utf-8")
if "fun deletePoster" not in repo_text:
    repo_text = replace(
        repo_text,
        '    fun mergeAndSave(local: LocalTeamState, snapshot: SyncSnapshot): LocalTeamState = SyncMerge.merge(local, snapshot).also(::save)',
        '''    fun deletePoster(state: LocalTeamState, poster: Poster): LocalTeamState {
        val updated = state.posters.filterNot { it.id == poster.id }
        val event = PosterEvent(
            posterId = poster.id,
            teamId = poster.teamId,
            actorDeviceId = state.deviceId,
            actorName = state.deviceName,
            action = "Plakat aus der Liste entfernt"
        )
        return state.copy(posters = updated, events = listOf(event) + state.events).also(::save)
    }

    fun mergeAndSave(local: LocalTeamState, snapshot: SyncSnapshot): LocalTeamState = SyncMerge.merge(local, snapshot).also(::save)''',
    )
repo_path.write_text(repo_text, encoding="utf-8")

invite_path = Path("app/src/main/java/de/bsw/plakatradar/core/TeamInvite.kt")
invite_text = invite_path.read_text(encoding="utf-8")
invite_text = replace(invite_text, "const val DEFAULT_TTL_SECONDS: Long = 10 * 60", "const val DEFAULT_TTL_SECONDS: Long = 60")
invite_path.write_text(invite_text, encoding="utf-8")
print("App fixes applied")
