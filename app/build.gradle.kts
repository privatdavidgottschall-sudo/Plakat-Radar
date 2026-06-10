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
        versionCode = 26
        versionName = "0.10.16-build-normalizer-fix"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
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
