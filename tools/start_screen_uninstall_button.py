from pathlib import Path

path = Path("app/src/main/java/de/bsw/plakatradar/MainActivity.kt")
text = path.read_text(encoding="utf-8")

# StartScreen needs a context so it can open Android's app info page.
if "fun StartScreen(vm: PlakatRadarViewModel)" in text:
    start_part = text.split("fun StartScreen(vm: PlakatRadarViewModel)", 1)[1].split("fun DashboardScreen", 1)[0]
    if "val context = LocalContext.current" not in start_part:
        text = text.replace(
            '    fun closeKeyboard() { focusManager.clearFocus(force = true) }\n    val scanner = rememberLauncherForActivityResult',
            '    fun closeKeyboard() { focusManager.clearFocus(force = true) }\n    val context = LocalContext.current\n    val scanner = rememberLauncherForActivityResult'
        )
        text = text.replace(
            '    var myName by remember { mutableStateOf("") }\n    val scanner = rememberLauncherForActivityResult',
            '    var myName by remember { mutableStateOf("") }\n    val context = LocalContext.current\n    val scanner = rememberLauncherForActivityResult'
        )

# Remove the old inline uninstall button if it exists under the team selection.
text = text.replace(
    '        Button(onClick = { openAppSettings(context) }, modifier = Modifier.fillMaxWidth().height(60.dp)) { Text("Deinstallieren") }\n        Divider()',
    '        Divider()'
)

# Wrap StartScreen content in a Box and add the uninstall button at the bottom left.
if "openAppSettings(context)" not in text.split("fun StartScreen(vm: PlakatRadarViewModel)", 1)[1].split("fun DashboardScreen", 1)[0]:
    text = text.replace(
        '    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {',
        '    Box(Modifier.fillMaxSize()) {\n        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {'
    )
    text = text.replace(
        '''        when (mode) {
            "leader" -> {
                Text("Teamleiter erstellt das Team und zeigt später den QR-Code. Der QR-Code ist jeweils 10 Minuten gültig.")
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
}''',
        '''        when (mode) {
            "leader" -> {
                Text("Teamleiter erstellt das Team und zeigt später den QR-Code. Der QR-Code ist jeweils 10 Minuten gültig.")
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
}'''
    )

# Fallback for the raw source before keyboard patch has changed button calls.
if "openAppSettings(context)" not in text.split("fun StartScreen(vm: PlakatRadarViewModel)", 1)[1].split("fun DashboardScreen", 1)[0]:
    text = text.replace(
        '''        when (mode) {
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
}''',
        '''        when (mode) {
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
        Button(
            onClick = { openAppSettings(context) },
            modifier = Modifier.align(Alignment.BottomStart).padding(24.dp).height(56.dp)
        ) { Text("Deinstallieren") }
    }
}'''
    )

# Ensure helper exists even when the dashboard management patch did not add it yet.
if "fun openAppSettings(context: Context)" not in text:
    text = text.replace(
        '''fun openNavigation(context: Context, latitude: Double, longitude: Double, label: String) {''',
        '''fun openAppSettings(context: Context) {
    val uri = Uri.parse("package:${context.packageName}")
    val intent = Intent("android.settings.APPLICATION_DETAILS_SETTINGS", uri)
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "App-Einstellungen konnten nicht geöffnet werden.", Toast.LENGTH_LONG).show()
    }
}

fun openNavigation(context: Context, latitude: Double, longitude: Double, label: String) {'''
    )

path.write_text(text, encoding="utf-8")
print("start screen bottom-left uninstall button applied")
