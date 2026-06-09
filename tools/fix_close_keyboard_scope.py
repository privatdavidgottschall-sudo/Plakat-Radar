from pathlib import Path

path = Path("app/src/main/java/de/bsw/plakatradar/MainActivity.kt")
text = path.read_text(encoding="utf-8")

# Fix: closeKeyboard was inserted into NearbyPostersScreen by the broad location-button patch.
old = '''@Composable
fun NearbyPostersScreen(vm: PlakatRadarViewModel) {
    val context = LocalContext.current
    var currentLat by remember { mutableStateOf<Double?>(null) }'''

new = '''@Composable
fun NearbyPostersScreen(vm: PlakatRadarViewModel) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    fun closeKeyboard() { focusManager.clearFocus(force = true) }
    var currentLat by remember { mutableStateOf<Double?>(null) }'''

if old in text and "fun NearbyPostersScreen(vm: PlakatRadarViewModel) {\n    val context = LocalContext.current\n    val focusManager = LocalFocusManager.current" not in text:
    text = text.replace(old, new)

# Fix: the QR timer patch must also catch the real wording in MainActivity.kt.
qr_variants = [
    '''        if (AccessPolicy.canShowQr(s)) {
            item {
                Divider()
                Text("Team-QR-Code. Nur du als Teamleiter stellst ihn bereit. Wer ihn scannt, ist direkt im Team. Der QR-Code ist 10 Minuten gültig.")
                vm.inviteText()?.let { QrCodeImage(it) }
            }
            item { TeamMembersCard(s) }
        }
''',
    '''        if (AccessPolicy.canShowQr(s)) {
            item {
                Divider()
                Text("Team-QR-Code. Nur du als Teamleiter stellst ihn bereit. Wer ihn scannt, ist direkt im Team. Der QR-Code ist jeweils 10 Minuten gültig.")
                vm.inviteText()?.let { QrCodeImage(it) }
            }
            item { TeamMembersCard(s) }
        }
'''
]

qr_new = '''        if (AccessPolicy.canShowQr(s)) {
            item { TeamInviteQrCard(vm) }
            item { TeamMembersCard(s) }
        }
'''

for qr_old in qr_variants:
    if qr_old in text:
        text = text.replace(qr_old, qr_new)

if "fun TeamInviteQrCard" not in text:
    text = text.replace(
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
fun TeamMembersCard(s: LocalTeamState) {'''
    )

# Add a floating update button at the bottom right of the dashboard.
if "openUpdatePage(context)" not in text:
    text = text.replace(
        '''    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tabs.indexOf(tab).coerceAtLeast(0)) {''',
        '''    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = tabs.indexOf(tab).coerceAtLeast(0)) {'''
    )
    text = text.replace(
        '''        when (tab) {
            "home" -> HomeScreen(vm)
            "add" -> AddPosterScreen(vm)
            "map" -> PosterMapScreen(vm.ui.local.posters)
            "near" -> NearbyPostersScreen(vm)
            "list" -> PosterListScreen(vm)
        }
    }
}''',
        '''            when (tab) {
                "home" -> HomeScreen(vm)
                "add" -> AddPosterScreen(vm)
                "map" -> PosterMapScreen(vm.ui.local.posters)
                "near" -> NearbyPostersScreen(vm)
                "list" -> PosterListScreen(vm)
            }
        }
        FloatingActionButton(
            onClick = { openUpdatePage(context) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Text("Update") }
    }
}'''
    )

# Add helper that opens the GitHub APK workflow page in the browser.
if "fun openUpdatePage(context: Context)" not in text:
    text = text.replace(
        '''fun openNavigation(context: Context, latitude: Double, longitude: Double, label: String) {''',
        '''fun openUpdatePage(context: Context) {
    val url = "https://github.com/privatdavidgottschall-sudo/Plakat-Radar/actions/workflows/android-debug-apk.yml"
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.onFailure {
        Toast.makeText(context, "Update-Seite konnte nicht geöffnet werden.", Toast.LENGTH_LONG).show()
    }
}

fun openNavigation(context: Context, latitude: Double, longitude: Double, label: String) {'''
    )

path.write_text(text, encoding="utf-8")
print("scope, QR timer and update button fixes applied")
