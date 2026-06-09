# Sicherheits- und Fehlerfixes

Diese Version wurde nach einer statischen Prüfung gehärtet.

## Gefixt

1. Lokale Zustandsdatei verschlüsselt
   - Vorher lag `state.json` im privaten App-Speicher als lesbares JSON.
   - Jetzt wird der Zustand als `state.enc` per Android Keystore + AES/GCM gespeichert.
   - Alte `state.json` wird beim ersten Start automatisch migriert und gelöscht.

2. Sicherer ZIP-Import
   - Vorher wurden Fotos aus dem Sync-Paket entpackt, bevor das Team-Paket geprüft war.
   - Jetzt wird zuerst `snapshot.json` gelesen und per Team-ID + Team-Schlüssel-Hash geprüft.
   - Erst danach werden Fotos entpackt.

3. ZIP-Bomben-Schutz
   - Größenlimits ergänzt:
     - Snapshot maximal 2 MB
     - einzelnes Foto maximal 8 MB
     - Fotos insgesamt maximal 250 MB
     - Sync-Paket maximal 300 MB

4. Fotodateien beim Import eingeschränkt
   - Es werden nur sichere Dateinamen akzeptiert.
   - Es werden nur Fotos entpackt, die im geprüften Snapshot referenziert sind.
   - Canonical-Path-Prüfung schützt gegen Pfadtricks.

5. Nearby-Dateiempfang verschärft
   - Sync-Dateien werden nur noch angenommen, wenn beide Seiten den Team-Schlüssel erfolgreich geprüft haben.

6. HMAC/Hash-Vergleich gehärtet
   - Prüfwerte werden jetzt mit konstantem Zeitvergleich verglichen.

7. Navigation robuster
   - Wenn keine Karten-App vorhanden ist, stürzt die App nicht mehr ab.
   - Stattdessen erscheint ein Hinweis.

## Weiterhin wichtig

- Wer den Teamleiter-QR fotografiert oder weitergeleitet bekommt, erhält den Team-Schlüssel.
- Für eine spätere harte Version wäre ein zusätzlicher Freigabemechanismus oder regelmäßig neu erzeugte Team-Schlüssel sinnvoll.
- Eine echte APK-Kompilierung muss über GitHub Actions oder Android Studio erfolgen.


## Zusätzlicher Datenschutzfix

8. Messenger-Sync-Pakete verschlüsselt
   - Vorher war das Sync-Paket technisch eine lesbare ZIP-Datei.
   - Jetzt wird das Paket mit AES/GCM und dem Team-Schlüssel verschlüsselt.
   - Die App entschlüsselt es beim Import automatisch und entpackt danach intern die ZIP-Struktur.
   - Alte Klartext-ZIP-Pakete können zur Migration noch gelesen werden, werden aber ebenfalls erst nach Team-Prüfung ausgewertet.

9. Falsche GPS-Fallback-Koordinaten entfernt
   - Vorher konnte ohne Standortberechtigung versehentlich ein Eilenburg-Standardpunkt gespeichert werden.
   - Jetzt wird ohne echte Standortermittlung nicht gespeichert, sondern ein verständlicher Hinweis gezeigt.


10. Screenshot-Schutz ergänzt
   - `FLAG_SECURE` verhindert Screenshots und Bildschirmaufnahmen in der App.
   - Ab Android 13 wird zusätzlich die Screenshot-Vorschau in der App-Übersicht deaktiviert.
   - Hinweis: Gegen Abfotografieren mit einem zweiten Gerät schützt das nicht.
