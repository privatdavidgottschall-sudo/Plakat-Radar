# PlakatRadar P2P LokalSync

Android/Kotlin-MVP für eine Plakat-Tracking-App ohne Cloud-Datenbank. Mit lokalem Nearby-Sync und Messenger-Sync-Paketen.

## Grundprinzip

- Es gibt genau zwei Einstiege: **Teamleiter** und **Teammitglied**.
- Nur der Teamleiter erstellt das Team.
- Nur der Teamleiter zeigt den offiziellen Team-QR-Code.
- Teammitglieder scannen ausschließlich diesen Teamleiter-QR-Code.
- Nach dem Scan arbeiten alle mit demselben lokalen Team-Schlüssel.
- Wenn Teamgeräte in der Nähe sind, synchronisieren sie lokal über Nearby Connections.
- Es wird keine Firebase- oder Google-Cloud-Datenbank benötigt.
- Zusätzlich kann ein Sync-Paket als ZIP über Messenger, E-Mail, Signal, WhatsApp oder Nearby Share geteilt und auf einem anderen Gerät importiert werden.

## Was funktioniert im MVP

- Plakat mit Foto, GPS, Standorttext, Typ und Notiz erfassen
- Plakatliste anzeigen
- Kartenansicht mit OpenStreetMap/osmdroid
- Status ändern: OK, beschädigt, entfernt
- Teamleiter-QR-Code
- lokaler Team-Sync in der Nähe über Nearby Connections
- Behördenexport als CSV
- Sync-Paket als ZIP mit `snapshot.json` und Fotos
- Sync-Paket über Messenger teilen
- Sync-Paket aus Messenger/Datei-App importieren

## Einschränkungen

- Nahbereich-Sync: Geräte müssen in der Nähe sein, zum Beispiel im gleichen Raum, Büro, Auto, WLAN-Umfeld oder bei einem Stammtisch.
- Messenger-Sync: Geräte müssen nicht in der Nähe sein, aber jemand muss das Sync-ZIP aktiv teilen und der andere importiert es.
- Kein echter Internet-Sync über weite Entfernung.
- Kartenkacheln von OpenStreetMap brauchen Internet, sofern sie nicht vorher gecacht wurden.
- Eine echte APK-Prüfung braucht Android Studio oder GitHub Actions mit Android SDK.

## Wichtige Dateien

- `app/src/main/java/de/bsw/plakatradar/MainActivity.kt`
- `app/src/main/java/de/bsw/plakatradar/core/TeamInvite.kt`
- `app/src/main/java/de/bsw/plakatradar/core/SyncMerge.kt`
- `app/src/main/java/de/bsw/plakatradar/data/LocalRepository.kt`
- `app/src/main/java/de/bsw/plakatradar/sync/NearbySyncManager.kt`
- `app/src/main/java/de/bsw/plakatradar/sync/SyncBundleCodec.kt`

## Bedienung für Helfer

1. Teamleiter öffnet die App.
2. Teamleiter tippt auf „Ich bin Teamleiter“.
3. Teamleiter erstellt das Team.
4. App zeigt dem Teamleiter einen QR-Code.
5. Helfer tippt auf „Ich bin Teammitglied“.
6. Helfer scannt den QR-Code vom Teamleiter.
7. Danach kann der Helfer Plakate erfassen und mit anderen Teamgeräten lokal synchronisieren.

## Datenschutzgedanke

Die App fragt keine Parteizugehörigkeit ab. Der Zugang läuft über den Teamleiter-QR-Code. Ohne QR-Code bleibt das Gerät ohne Teamdaten.


## Messenger-Sync

Die Funktion „Sync-Paket teilen“ erzeugt eine ZIP-Datei mit:

- `snapshot.json` für Plakatdaten, Geräte, Status und Ereignisse
- `photos/` mit den vorhandenen Plakatfotos

Diese ZIP-Datei kann über Signal, WhatsApp, Telegram, E-Mail, Nearby Share oder andere Messenger geteilt werden. Auf dem anderen Gerät wird „Sync-Paket importieren“ gewählt. Die App prüft den Team-Schlüssel-Hash und führt nur Pakete aus demselben Team zusammen.

Wichtig: Messenger-Sync ist kein automatischer Live-Sync. Es ist ein sicherer manueller Austauschweg, wenn Nearby-Sync gerade nicht möglich ist.


## Top-5-Ausbau

Siehe `TOP5_FEATURES_DIESE_VERSION.md`.
