plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "de.bsw.plakatradar"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.bsw.plakatradar"
        minSdk = 26
        targetSdk = 35
        versionCode = 30
        versionName = "0.11.0-modern-ui-refresh"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-viewbinding")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.gms:play-services-nearby:19.3.0")

    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}

tasks.register("normalizeKeyboardCallbacks") {
    doLast {
        val mainActivity = file("src/main/java/de/bsw/plakatradar/MainActivity.kt")
        if (mainActivity.isFile) {
            val original = mainActivity.readText()
            var fixed = original

            if (!fixed.contains("import android.graphics.BitmapFactory")) {
                fixed = fixed.replace(
                    "import android.graphics.Bitmap\n",
                    "import android.graphics.Bitmap\nimport android.graphics.BitmapFactory\n"
                )
            }

            fixed = fixed.replace(
                "fun close" + "Keyboard() { focusManager.clearFocus(force = true) }",
                "val close" + "Keyboard: () -> Unit = { focusManager.clearFocus(force = true) }"
            )

            val modernUiRegex = Regex(
                """@Composable\s+fun DashboardScreen\(vm: PlakatRadarViewModel\).*?\n\n@Composable\s+fun NoQrCard""",
                setOf(RegexOption.DOT_MATCHES_ALL)
            )
            val modernUiBlock = """
@Composable
fun DashboardScreen(vm: PlakatRadarViewModel) {
    var tab by remember { mutableStateOf("home") }
    var showPermissionPopup by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        Toast.makeText(
            context,
            if (grants.values.all { it }) "Berechtigungen sind aktiv." else "Einige Berechtigungen fehlen noch. Manche Funktionen können eingeschränkt sein.",
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

    Scaffold(
        bottomBar = { ModernBottomNav(tab) { tab = it } }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
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
fun ModernBottomNav(tab: String, onTab: (String) -> Unit) {
    NavigationBar {
        NavigationBarItem(selected = tab == "home", onClick = { onTab("home") }, icon = { Text("⌂") }, label = { Text("Start") })
        NavigationBarItem(selected = tab == "add", onClick = { onTab("add") }, icon = { Text("+") }, label = { Text("Erfassen") })
        NavigationBarItem(selected = tab == "list", onClick = { onTab("list") }, icon = { Text("≡") }, label = { Text("Liste") })
        NavigationBarItem(selected = tab == "map", onClick = { onTab("map") }, icon = { Text("⌖") }, label = { Text("Karte") })
        NavigationBarItem(selected = tab == "more", onClick = { onTab("more") }, icon = { Text("…") }, label = { Text("Mehr") })
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
        confirmButton = { Button(onClick = onAllow) { Text("Berechtigungen erlauben") } },
        dismissButton = { TextButton(onClick = onLater) { Text("Später") } }
    )
}

@Composable
fun HomeScreen(vm: PlakatRadarViewModel, onNavigate: (String) -> Unit) {
    val s = vm.ui.local
    val hasTeamQr = AccessPolicy.hasTeamAccess(s)
    val qrScanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { vm.joinByQr(it, s.deviceName.ifBlank { "Teammitglied" }) }
    }
    val activePosters = s.posters.count { it.status != PosterStatus.REMOVED }
    val checkedPosters = s.posters.count { it.status == PosterStatus.CHECKED }

    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("PlakatRadar", style = MaterialTheme.typography.headlineMedium)
                    Text(s.teamName ?: "Ohne Team-QR")
                    Text(if (s.role == MemberRole.LEADER) "Teamleiter-Modus" else "Teammitglied-Modus")
                    Text("Aktive Plakate: ${'$'}activePosters · Kontrolliert: ${'$'}checkedPosters")
                    Text("Letzte Meldung: ${'$'}{vm.ui.lastLog}")
                }
            }
        }
        if (!hasTeamQr) item { NoQrCard { qrScanner.launch(ScanOptions().setPrompt("QR-Code vom Teamleiter scannen").setBeepEnabled(false)) } }
        item { ModernActionCard("Plakat erfassen", "Foto aufnehmen, GPS speichern und Notizen ergänzen.", "Jetzt erfassen") { onNavigate("add") } }
        item { ModernActionCard("Plakate ansehen", "Liste mit Status, Foto-Vorschau und Weg dorthin.", "Liste öffnen") { onNavigate("list") } }
        item { ModernActionCard("Karte öffnen", "Alle Plakate auf der Karte sehen.", "Zur Karte") { onNavigate("map") } }
        item { RemovalReminderCard(s) }
        item { ModernActionCard("Teilen / Exportieren", "Stadtverwaltung, Sync-Paket und Import gebündelt an einer Stelle.", "Menü öffnen") { onNavigate("more") } }
    }
}

@Composable
fun MoreScreen(vm: PlakatRadarViewModel, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val s = vm.ui.local
    val hasTeamQr = AccessPolicy.hasTeamAccess(s)
    var showShareMenu by remember { mutableStateOf(false) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    val syncImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importSharedSyncBundle(uri) }
    val qrScanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { vm.joinByQr(it, s.deviceName.ifBlank { "Teammitglied" }) }
    }

    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Mehr", style = MaterialTheme.typography.headlineMedium); Text("Team, Teilen und Einstellungen") }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Teilen / Exportieren", style = MaterialTheme.typography.titleLarge)
                    Text("Alle Datei-Funktionen liegen jetzt an einer Stelle.")
                    Button(onClick = { showShareMenu = true }, modifier = Modifier.fillMaxWidth().height(58.dp)) { Text("Teilen / Exportieren öffnen") }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Team & Sync", style = MaterialTheme.typography.titleLarge)
                    if (hasTeamQr) {
                        Button(onClick = vm::startOrStopSync, modifier = Modifier.fillMaxWidth().height(58.dp)) { Text(if (vm.ui.syncActive) "Lokalen Sync stoppen" else "Lokalen Sync starten") }
                        Text("Lokaler Sync funktioniert mit Teamgeräten in der Nähe.")
                    } else {
                        LockedTeamButton("Lokalen Sync starten", vm)
                        Button(onClick = { qrScanner.launch(ScanOptions().setPrompt("QR-Code vom Teamleiter scannen").setBeepEnabled(false)) }, modifier = Modifier.fillMaxWidth().height(58.dp)) { Text("Teamleiter-QR scannen") }
                    }
                }
            }
        }
        item { if (hasTeamQr) GoogleServicePlaceholderCard(vm) else LockedTeamButton("Google-Service nutzen", vm) }
        if (AccessPolicy.canShowQr(s)) {
            item { TeamInviteQrCard(vm) }
            item { TeamMembersCard(s) }
        }
        item { ModernActionCard("Plakate in meiner Nähe", "Sortiert nach Entfernung zum aktuellen Standort.", "Nähe öffnen") { onNavigate("near") } }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Berechtigungen", style = MaterialTheme.typography.titleLarge)
                    Text("Kamera, Standort und Geräte in der Nähe aktivieren.")
                    Button(onClick = { permissions.launch(nearbyAndAppPermissions()) }, modifier = Modifier.fillMaxWidth().height(58.dp)) { Text("Berechtigungen prüfen") }
                }
            }
        }
        item { AppManagementCard(context) }
    }

    if (showShareMenu) {
        ShareExportDialog(
            hasTeamQr = hasTeamQr,
            canExportAuthority = AccessPolicy.canExportForAuthority(s),
            onDismiss = { showShareMenu = false },
            onAuthorityExport = { showShareMenu = false; vm.exportCsv(context, "Eilenburg") },
            onShareSync = { showShareMenu = false; vm.shareSyncBundle(context) },
            onImportSync = { showShareMenu = false; syncImportLauncher.launch(arrayOf("application/octet-stream", "application/zip", "*/*")) }
        )
    }
}

@Composable
fun ModernActionCard(title: String, subtitle: String, button: String, enabled: Boolean = true, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle)
            Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(button) }
        }
    }
}

@Composable
fun ShareExportDialog(
    hasTeamQr: Boolean,
    canExportAuthority: Boolean,
    onDismiss: () -> Unit,
    onAuthorityExport: () -> Unit,
    onShareSync: () -> Unit,
    onImportSync: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Teilen / Exportieren") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Alle Datei-Funktionen sind gebündelt.")
                Button(onClick = onAuthorityExport, enabled = canExportAuthority, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Stadtverwaltungs-Export teilen") }
                Button(onClick = onShareSync, enabled = hasTeamQr, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Sync-Paket teilen") }
                Button(onClick = onImportSync, enabled = hasTeamQr, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Sync-Paket importieren") }
                if (!hasTeamQr) Text("Sync-Pakete brauchen den Teamleiter-QR. Der Stadtverwaltungs-Export geht auch ohne QR-Code.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } }
    )
}

@Composable
fun NoQrCard""".trimIndent()
            fixed = modernUiRegex.replace(fixed, Regex.escapeReplacement(modernUiBlock))

            val posterListRegex = Regex(
                """@Composable\s+fun PosterListScreen\(vm: PlakatRadarViewModel\).*?\n\n@Composable\s+fun NearbyPostersScreen""",
                setOf(RegexOption.DOT_MATCHES_ALL)
            )
            val posterListWithPhotoPreview = """
@Composable
fun PosterListScreen(vm: PlakatRadarViewModel) {
    val s = vm.ui.local
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(s.posters) { p ->
            val photoFile = p.localPhotoFileName?.let { File(context.filesDir, "photos/${'$'}it") }
            val photoBitmap = remember(p.localPhotoFileName) {
                photoFile?.takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.absolutePath) }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(p.addressHint.ifBlank { "Standort ohne Text" }, style = MaterialTheme.typography.titleMedium)
                    Text("Status: ${'$'}{statusText(p.status)} · von ${'$'}{p.createdByName}")
                    Text("GPS: ${'$'}{p.latitude}, ${'$'}{p.longitude}")
                    Text("Abnahme bis: ${'$'}{formatDate(p.plannedRemovalAt)}")
                    if (photoBitmap != null) {
                        Image(bitmap = photoBitmap.asImageBitmap(), contentDescription = "Plakatfoto", modifier = Modifier.fillMaxWidth().height(180.dp))
                        Text("Foto: ${'$'}{p.localPhotoFileName}")
                    } else {
                        Text("Kein Foto zu diesem Eintrag gespeichert.")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button({ vm.updateStatus(p, PosterStatus.CHECKED) }) { Text("OK") }
                        Button({ vm.updateStatus(p, PosterStatus.DAMAGED) }) { Text("Kaputt") }
                        Button({ vm.updateStatus(p, PosterStatus.REMOVED) }) { Text("Entfernt") }
                    }
                    Button({ vm.deletePoster(p) }, modifier = Modifier.fillMaxWidth()) { Text("Aus Liste entfernen") }
                    Button({ openNavigation(context, p.latitude, p.longitude, p.addressHint.ifBlank { "Plakat" }) }) { Text("Weg dorthin") }
                }
            }
        }
    }
}

@Composable
fun NearbyPostersScreen""".trimIndent()
            fixed = posterListRegex.replace(fixed, Regex.escapeReplacement(posterListWithPhotoPreview))

            if (fixed != original) mainActivity.writeText(fixed)
        }
    }
}

tasks.named("preBuild") {
    dependsOn("normalizeKeyboardCallbacks")
}
