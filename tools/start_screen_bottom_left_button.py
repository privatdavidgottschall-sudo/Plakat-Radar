from pathlib import Path

path = Path("app/src/main/java/de/bsw/plakatradar/MainActivity.kt")
text = path.read_text(encoding="utf-8")

# Ensure imports for keyboard-safe scrolling on the start screen.
if "import androidx.compose.foundation.rememberScrollState" not in text:
    text = text.replace(
        "import androidx.compose.foundation.Image\n",
        "import androidx.compose.foundation.Image\nimport androidx.compose.foundation.rememberScrollState\nimport androidx.compose.foundation.verticalScroll\n"
    )

# Let a local no-QR member enter the dashboard. Real team/share access still needs QR.
text = text.replace(
    "!AccessPolicy.hasTeamAccess(s.local) -> StartScreen(vm)",
    "s.local.role == null -> StartScreen(vm)"
)

# Add ViewModel helpers for no-QR mode.
if "fun enterWithoutQr" not in text:
    join_block = '''    fun joinByQr(raw: String, memberName: String) {
        runCatching { repo.joinByInvite(TeamInvite.decode(raw), memberName) }
            .onSuccess { ui = ui.copy(local = it, lastLog = "Beigetreten. Du kannst jetzt Plakate sehen und erfassen.") }
            .onFailure { fail(it) }
    }
'''
    helper_block = join_block + '''
    fun enterWithoutQr(memberName: String) {
        val name = memberName.ifBlank { "Teammitglied" }
        val current = ui.local
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
        ui = ui.copy(local = updated, lastLog = "Ohne Teamleiter-QR gestartet. Team-/Share-Funktionen sind gesperrt.")
    }

    fun requireTeamQr() {
        ui = ui.copy(error = "Bitte Teamleiter-QR-Code scannen.")
    }
'''
    if join_block not in text:
        raise SystemExit("joinByQr block not found")
    text = text.replace(join_block, helper_block)

start_marker = "@Composable\nfun StartScreen(vm: PlakatRadarViewModel)"
dash_marker = "@Composable\nfun DashboardScreen(vm: PlakatRadarViewModel)"

if start_marker not in text or dash_marker not in text:
    raise SystemExit("StartScreen or DashboardScreen marker not found")

before = text.split(start_marker, 1)[0]
rest = text.split(start_marker, 1)[1]
old_start_body, after = rest.split(dash_marker, 1)

new_start = '''@Composable
fun StartScreen(vm: PlakatRadarViewModel) {
    var mode by remember { mutableStateOf<String?>(null) }
    var showQrChoice by remember { mutableStateOf(false) }
    var teamName by remember { mutableStateOf("BSW Nordsachsen Plakatierung") }
    var myName by remember { mutableStateOf("") }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    fun closeKeyboard() { focusManager.clearFocus(force = true) }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val code = result.contents
        if (code != null) vm.joinByQr(code, myName.ifBlank { "Teammitglied" })
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .padding(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("PlakatRadar", style = MaterialTheme.typography.headlineLarge)
            Text("Bitte auswählen. Es gibt nur zwei Wege:")
            Button(onClick = { mode = "leader" }, modifier = Modifier.fillMaxWidth().height(72.dp)) { Text("Ich bin Teamleiter") }
            Button(onClick = { mode = "member" }, modifier = Modifier.fillMaxWidth().height(72.dp)) { Text("Ich bin Teammitglied") }
            Divider()
            when (mode) {
                "leader" -> {
                    Text("Teamleiter erstellt das Team und zeigt später den QR-Code.")
                    OutlinedTextField(teamName, { teamName = it }, label = { Text("Teamname") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(myName, { myName = it }, label = { Text("Dein Name") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { closeKeyboard(); vm.createLeaderTeam(teamName, myName.ifBlank { "Teamleiter" }) }, modifier = Modifier.fillMaxWidth().height(64.dp)) { Text("Team erstellen") }
                }
                "member" -> {
                    Text("Bitte zuerst deinen Namen eintragen.")
                    OutlinedTextField(myName, { myName = it }, label = { Text("Dein Name") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { closeKeyboard(); showQrChoice = true }, modifier = Modifier.fillMaxWidth().height(64.dp)) { Text("Weiter") }
                }
            }
        }
        Button(
            onClick = { openAppSettings(context) },
            modifier = Modifier.align(Alignment.BottomStart).padding(24.dp).height(56.dp)
        ) { Text("Deinstallieren") }
    }

    if (showQrChoice) {
        AlertDialog(
            onDismissRequest = { showQrChoice = false },
            title = { Text("Teamleiter-QR-Code scannen?") },
            text = { Text("Möchtest du jetzt den QR-Code vom Teamleiter scannen? Ohne QR kannst du die App öffnen, aber Team-/Share-Funktionen bleiben gesperrt.") },
            confirmButton = {
                Button(onClick = {
                    showQrChoice = false
                    scanner.launch(ScanOptions().setPrompt("QR-Code vom Teamleiter scannen").setBeepEnabled(false))
                }) { Text("Ja, scannen") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showQrChoice = false
                    vm.enterWithoutQr(myName.ifBlank { "Teammitglied" })
                }) { Text("Nein, ohne QR") }
            }
        )
    }
}

'''

text = before + new_start + dash_marker + after

if "fun openAppSettings(context: Context)" not in text:
    helper = '''fun openAppSettings(context: Context) {
    val uri = Uri.parse("package:${context.packageName}")
    val intent = Intent("android.settings.APPLICATION_DETAILS_SETTINGS", uri)
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "App-Einstellungen konnten nicht geöffnet werden.", Toast.LENGTH_LONG).show()
    }
}

'''
    if "fun openUpdatePage(context: Context)" in text:
        text = text.replace("fun openUpdatePage(context: Context) {", helper + "fun openUpdatePage(context: Context) {", 1)
    else:
        text = text.replace("fun openNavigation(context: Context, latitude: Double, longitude: Double, label: String) {", helper + "fun openNavigation(context: Context, latitude: Double, longitude: Double, label: String) {", 1)

path.write_text(text, encoding="utf-8")

# Local no-QR users may save posters locally. Sync/share still needs teamSecret via hasTeamAccess.
access_path = Path("app/src/main/java/de/bsw/plakatradar/core/AccessPolicy.kt")
access_text = access_path.read_text(encoding="utf-8")
access_text = access_text.replace(
    "fun canAddPoster(state: LocalTeamState): Boolean = isSelfApproved(state)",
    "fun canAddPoster(state: LocalTeamState): Boolean = state.role != null && !state.teamId.isNullOrBlank()"
)
access_path.write_text(access_text, encoding="utf-8")

print("no-QR member entry applied")
