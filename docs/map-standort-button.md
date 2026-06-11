# Karten-Standortbutton

Geplante saubere Änderung für `MainActivity.kt`.

## Ziel

In der Kartenansicht soll rechts unten ein Standort-Button erscheinen.

Verhalten:

- Beim Öffnen der Karte wird einmalig versucht, auf den eigenen Standort zu springen.
- Wenn kein Standort verfügbar ist, nutzt die Karte als Fallback das BSW-Parteibüro in Berlin.
- Wenn der Nutzer die Karte manuell verschiebt, bleibt die Karte dort und springt nicht automatisch zurück.
- Der Standort-Button rechts unten springt bei Bedarf wieder zum eigenen Standort.
- Der Zoom soll straßennah sein, aber nicht zu stark. Vorgeschlagen: `16.5`.

## Fallback-Koordinaten

```kotlin
val bswBerlinFallback = GeoPoint(52.5119, 13.4116)
```

## Ziel-Zoom

```kotlin
targetMap.controller.setZoom(16.5)
```

## Hinweis

Diese Änderung sollte direkt in `PosterMapScreen(...)` umgesetzt werden, nicht über ein Build-Patch-Skript. Der Quellcode im Repository soll die Wahrheit bleiben.
