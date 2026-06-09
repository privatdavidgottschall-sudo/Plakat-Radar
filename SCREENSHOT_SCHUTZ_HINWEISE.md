# Screenshot-Schutz ergänzt

Diese Version aktiviert einen App-weiten Screenshot- und Bildschirmaufnahme-Schutz.

## Technische Änderung

In `MainActivity.kt` wurde ergänzt:

- `WindowManager.LayoutParams.FLAG_SECURE`
- ab Android 13 zusätzlich `setRecentsScreenshotEnabled(false)`

## Wirkung

Android soll dadurch verhindern, dass sensible Inhalte einfach per Screenshot oder Bildschirmaufnahme gespeichert werden, zum Beispiel:

- Teamleiter-QR-Code
- Plakatkarte
- Plakatfotos
- GPS-Standorte
- Sync-Bereich
- Behördenlisten

## Hinweis

Dieser Schutz ist eine wichtige Hürde, aber kein absoluter Geheimschutz. Ein anderes Handy kann den Bildschirm weiterhin abfotografieren. Der Teamleiter-QR-Code sollte deshalb weiterhin nur kurz und kontrolliert gezeigt werden.
