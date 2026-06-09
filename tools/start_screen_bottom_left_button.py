from pathlib import Path

path = Path("app/src/main/java/de/bsw/plakatradar/MainActivity.kt")
text = path.read_text(encoding="utf-8")

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
            Modifier.fillMaxSize().padding(24.dp),
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
                    Text("Teammitglied scannt ausschließlich den QR-Code vom Teamleiter und ist danach direkt im Team.")
                    OutlinedTextField(myName, { myName = it }, label = { Text("Dein Name") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { closeKeyboard(); scanner.launch(ScanOptions().setPrompt("QR-Code vom Teamleiter scannen").setBeepEnabled(false)) }, modifier = Modifier.fillMaxWidth().height(64.dp)) { Text("QR-Code scannen") }
                }
            }
        }
        Button(
            onClick = { openAppSettings(context) },
            modifier = Modifier.align(Alignment.BottomStart).padding(24.dp).height(56.dp)
        ) { Text("Deinstallieren") }
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
print("robust start screen bottom-left button applied")
