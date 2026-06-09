# Fixes in dieser Version

Diese Version wurde nachträglich auf typische Stolpersteine geprüft und angepasst.

## Behoben

1. **Nearby-Sync sendet keine Plakatdaten mehr vor der Teamprüfung**
   - Vorher konnte ein Gerät mit passender Team-ID theoretisch zu früh ein Sync-Paket erhalten.
   - Jetzt gibt es vor jedem Dateiaustausch eine HMAC-Prüfung mit dem geheimen Team-Schlüssel.
   - Erst wenn die Prüfung erfolgreich ist, wird das ZIP-Sync-Paket übertragen.

2. **Zweiseitiger Sync-Handshake eingebaut**
   - Beide Geräte müssen zeigen, dass sie den Team-Schlüssel besitzen.
   - Danach bestätigen sie sich gegenseitig mit `AUTH_OK`.
   - Erst dann wird das eigentliche Sync-ZIP gesendet.
   - Dadurch wird verhindert, dass ein legitimes Gerät ein Paket wegen einer Timing-Reihenfolge im Verbindungsaufbau ablehnt.

3. **Team-ID wird nicht mehr im sichtbaren Nearby-Gerätenamen beworben**
   - Vorher stand die Team-ID im Endpoint-Namen.
   - Jetzt heißt der Endpoint nur noch ungefähr `PlakatRadar|Name|Gerätekennung`.
   - Das reduziert unnötige Datenpreisgabe im Nahbereich.

4. **Fremde Sync-Dateien über Nearby werden abgelehnt**
   - Eine Dateiübertragung wird nur noch akzeptiert, wenn der Absender vorher erfolgreich geprüft wurde.
   - Nicht geprüfte Datei-Payloads führen zur Trennung.

5. **Kryptofunktionen ergänzt**
   - Ergänzt wurden `hmacSha256Hex()` und `randomNonceHex()` in `Hashing.kt`.
   - Diese werden für die Team-Schlüssel-Prüfung im lokalen P2P-Sync genutzt.

## Weiterhin bewusst so gelassen

- Kein Firebase, keine Cloud, keine `google-services.json`.
- Teammitglieder treten durch Scan des Teamleiter-QR-Codes bei.
- Messenger-Sync bleibt manuell: ZIP teilen, auf anderem Gerät importieren.
- Eine echte APK-Prüfung muss in GitHub Actions oder Android Studio laufen.
