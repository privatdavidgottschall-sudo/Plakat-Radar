# Navigation ergänzt

Diese Version ergänzt die Funktion „Weg dorthin“.

## Was neu ist

- Plakat-Marker auf der Karte sind jetzt anklickbar.
- Beim Antippen eines Plakats öffnet Android eine installierte Karten-App.
- Ziel ist die gespeicherte GPS-Position des Plakats.
- Wenn keine Karten-App direkt geöffnet werden kann, wird als Fallback Google Maps im Browser geöffnet.

## Geänderte Datei

- `app/src/main/java/de/bsw/plakatradar/MainActivity.kt`

## Technische Umsetzung

- Neue Funktion `openNavigation(...)`
- Marker-Click-Listener in `PosterMapScreen(...)`
- Intent mit `geo:`-URI
- Browser-Fallback mit Google-Maps-Routenlink
