# Release-Checkliste PlakatRadar

Diese Datei ist die kurze Vorab-Prüfung, bevor eine APK an das interne Team verteilt wird.

## Vor jedem Release

- [ ] GitHub-Actions-Build ist grün.
- [ ] APK-Artefakt `PlakatRadar-debug-apk` wurde heruntergeladen.
- [ ] App auf mindestens einem echten Android-Handy installieren.
- [ ] Teamleiter-Team erstellen.
- [ ] Teammitglied per QR-Code verbinden.
- [ ] Plakat mit Foto und GPS erfassen.
- [ ] Status ändern: Hängt, Kontrolliert, Beschädigt, Fehlt, Entfernt.
- [ ] Karte öffnen und Marker prüfen.
- [ ] Verwaltungs-Export teilen.
- [ ] ZIP öffnen und prüfen:
  - `plakatliste.csv` vorhanden
  - Fotos vorhanden, soweit erfasst
  - Umlaute lesbar
  - Koordinaten vorhanden
  - keine internen Notizen versehentlich im Verwaltungs-Export
- [ ] Sync-Paket teilen und auf zweitem Gerät importieren.

## Release-Regel

Nur verteilen, wenn Build und Handy-Test erfolgreich sind.

## Aktueller Stand

Interne Testversion für Plakat-Teams. Keine öffentliche App-Store-Version.
