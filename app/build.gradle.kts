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
        versionCode = 29
        versionName = "0.10.19-central-share-menu"
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

            val homeScreenRegex = Regex(
                """@Composable\s+fun HomeScreen\(vm: PlakatRadarViewModel\).*?\n\n@Composable\s+fun NoQrCard""",
                setOf(RegexOption.DOT_MATCHES_ALL)
            )
            val homeScreenWithShareMenu = """
@Composable
fun HomeScreen(vm: PlakatRadarViewModel) {
    val context = LocalContext.current
    val s = vm.ui.local
    val hasTeamQr = AccessPolicy.hasTeamAccess(s)
    var showShareMenu by remember { mutableStateOf(false) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    val syncImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importSharedSyncBundle(uri) }
    val qrScanner = rememberLauncherForActivityResult(ScanContract()) { result -> result.contents?.let { vm.joinByQr(it, s.deviceName.ifBlank { "Teammitglied" }) } }

    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text(s.teamName ?: "Plakat-Team", style = MaterialTheme.typography.headlineMedium)
            Text(if (s.role == MemberRole.LEADER) "Du bist Teamleiter." else "Du bist Teammitglied.")
            Text("Letzte Meldung: ${'$'}{vm.ui.lastLog}")
        }
        if (!hasTeamQr) item { NoQrCard { qrScanner.launch(ScanOptions().setPrompt("QR-Code vom Teamleiter scannen").setBeepEnabled(false)) } }
        item { RemovalReminderCard(s) }
        item { Button(onClick = { permissions.launch(nearbyAndAppPermissions()) }, modifier = Modifier.fillMaxWidth().height(60.dp)) { Text("Berechtigungen erlauben") } }
        item {
            if (hasTeamQr) Button(onClick = vm::startOrStopSync, modifier = Modifier.fillMaxWidth().height(60.dp)) { Text(if (vm.ui.syncActive) "Lokalen Sync stoppen" else "Lokalen Sync starten") } else LockedTeamButton("Lokalen Sync starten", vm)
            Text("Der lokale Sync funktioniert, wenn Teamgeräte in der Nähe sind, z.B. im selben Raum, Büro oder WLAN-Umfeld.")
        }
        item {
            Divider()
            Text("Dateien teilen und exportieren")
            Button(onClick = { showShareMenu = true }, modifier = Modifier.fillMaxWidth().height(64.dp)) { Text("Teilen / Exportieren") }
            Text("Stadtverwaltungs-ZIP, Sync-Paket teilen und Sync-Paket importieren liegen jetzt an einer Stelle.")
        }
        item { if (hasTeamQr) GoogleServicePlaceholderCard(vm) else LockedTeamButton("Google-Service nutzen", vm) }
        if (AccessPolicy.canShowQr(s)) { item { TeamInviteQrCard(vm) }; item { TeamMembersCard(s) } }
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
                Text("Alle Datei-Funktionen sind jetzt gebündelt.")
                Button(onClick = onAuthorityExport, enabled = canExportAuthority, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Stadtverwaltungs-ZIP teilen") }
                Button(onClick = onShareSync, enabled = hasTeamQr, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Sync-Paket teilen") }
                Button(onClick = onImportSync, enabled = hasTeamQr, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Sync-Paket importieren") }
                if (!hasTeamQr) Text("Sync-Pakete brauchen den Teamleiter-QR. Die Stadtverwaltungs-ZIP geht auch ohne QR-Code.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } }
    )
}

@Composable
fun NoQrCard""".trimIndent()
            fixed = homeScreenRegex.replace(fixed, Regex.escapeReplacement(homeScreenWithShareMenu))

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
