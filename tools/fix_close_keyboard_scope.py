from pathlib import Path

path = Path("app/src/main/java/de/bsw/plakatradar/MainActivity.kt")
text = path.read_text(encoding="utf-8")

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

path.write_text(text, encoding="utf-8")
print("closeKeyboard scope fix applied")
