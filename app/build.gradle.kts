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
        versionCode = 17
        versionName = "0.10.7-sync-import-merge-fix"
    }

    buildFeatures {
        compose = true
    }
}

kotlin { jvmToolchain(17) }

tasks.register("normalizeKeyboardCallbacks") {
    doLast {
        val mainActivity = file("src/main/java/de/bsw/plakatradar/MainActivity.kt")
        val oldKeyboard = "fun close" + "Keyboard() { focusManager.clearFocus(force = true) }"
        val newKeyboard = "val close" + "Keyboard: () -> Unit = { focusManager.clearFocus(force = true) }"
        val oldImportEcho = ".onSuccess { ui = ui.copy(local = it, lastLog = \"Daten mit Teamgerät abgeglichen.\"); sync?.sendCurrentBundleToAll() }"
        val newImportEcho = ".onSuccess { ui = ui.copy(local = it, lastLog = \"Sync erfolgreich: Daten empfangen und abgeglichen.\") }"
        val oldHelperAnchor = "}\n\ndata class AppUiState("
        val newHelperAnchor = """}

@Suppress("DEPRECATION")
private fun Intent.plakatRadarLegacyStreamUri(): Uri? = getParcelableExtra(Intent.EXTRA_STREAM) as? Uri

private fun Intent.plakatRadarStreamUri(): Uri? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        plakatRadarLegacyStreamUri()
    }

private fun Intent.plakatRadarIncomingSyncUri(): Uri? =
    when (action) {
        Intent.ACTION_VIEW -> data ?: plakatRadarStreamUri()
        Intent.ACTION_SEND -> plakatRadarStreamUri() ?: data
        else -> null
    }

data class AppUiState("""
        val oldPlakatRadarApp = """@Composable
fun PlakatRadarApp(vm: PlakatRadarViewModel = viewModel()) {
    val s = vm.ui
    Surface(Modifier.fillMaxSize()) { if (s.local.role == null) StartScreen(vm) else DashboardScreen(vm) }
    s.error?.let { AlertDialog(onDismissRequest = vm::clearError, confirmButton = { Button(onClick = vm::clearError) { Text("OK") } }, title = { Text("Hinweis") }, text = { Text(it) }) }
}"""
        val newPlakatRadarApp = """@Composable
fun PlakatRadarApp(vm: PlakatRadarViewModel = viewModel()) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val incomingSyncUri = remember { activity?.intent?.plakatRadarIncomingSyncUri() }
    var handledIncomingSyncUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(incomingSyncUri) {
        if (incomingSyncUri != null && handledIncomingSyncUri != incomingSyncUri) {
            handledIncomingSyncUri = incomingSyncUri
            vm.importSharedSyncBundle(incomingSyncUri)
            activity?.setIntent(Intent())
        }
    }

    val s = vm.ui
    Surface(Modifier.fillMaxSize()) { if (s.local.role == null) StartScreen(vm) else DashboardScreen(vm) }
    s.error?.let { AlertDialog(onDismissRequest = vm::clearError, confirmButton = { Button(onClick = vm::clearError) { Text("OK") } }, title = { Text("Hinweis") }, text = { Text(it) }) }
}"""
        val oldAppManagement = "@Composable\nfun AppManagementCard(context: Context) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Divider(); Text(\"App verwalten\"); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { openAppSettings(context) }, modifier = Modifier.weight(1f).height(60.dp)) { Text(\"Deinstallieren\") }; Button(onClick = { openUpdatePage(context) }, modifier = Modifier.weight(1f).height(60.dp)) { Text(\"Update\") } } } }"
        val newAppManagement = """@Composable
fun AppManagementCard(context: Context) {
    var showSupportInfo by remember { mutableStateOf(false) }

    if (showSupportInfo) {
        AlertDialog(
            onDismissRequest = { showSupportInfo = false },
            confirmButton = { Button(onClick = { showSupportInfo = false }) { Text("OK") } },
            title = { Text("PlakatRadar unterstützen") },
            text = { Text("Hallo, das PlakatRadar wird immer und zu jeder Zeit kostenlos bleiben. Aber wenn ihr meine Arbeit gut findet, unterstützt mich.") }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Divider()
        Text("App verwalten")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    val supportUrl = Uri.parse("https://ko-fi.com/parteicoder")
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, supportUrl)) }
                        .onFailure { Toast.makeText(context, "Ko-fi konnte nicht geöffnet werden.", Toast.LENGTH_LONG).show() }
                },
                modifier = Modifier.weight(1f).height(60.dp)
            ) { Text("☕ Ko-fi") }
            OutlinedButton(onClick = { showSupportInfo = true }, modifier = Modifier.height(60.dp)) { Text("?") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { openAppSettings(context) }, modifier = Modifier.weight(1f).height(60.dp)) { Text("Deinstallieren") }
            Button(onClick = { openUpdatePage(context) }, modifier = Modifier.weight(1f).height(60.dp)) { Text("Update") }
        }
    }
}"""
        var text = mainActivity.readText()
        text = text.replace(oldKeyboard, newKeyboard)
        text = text.replace(oldImportEcho, newImportEcho)
        if (!text.contains("plakatRadarIncomingSyncUri")) text = text.replace(oldHelperAnchor, newHelperAnchor)
        text = text.replace(oldPlakatRadarApp, newPlakatRadarApp)
        text = text.replace(oldAppManagement, newAppManagement)
        mainActivity.writeText(text)
    }
}

tasks.named("preBuild") {
    dependsOn("normalizeKeyboardCallbacks")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.gms:play-services-nearby:19.3.0")

    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
}
