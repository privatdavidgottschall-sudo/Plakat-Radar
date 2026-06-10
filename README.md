# PlakatRadar

PlakatRadar ist eine Android/Kotlin-App fuer lokale Teams. Die App hilft dabei, Plakatstandorte mit Foto, GPS, Status und Notiz zu erfassen, im Team abzugleichen und bei Bedarf als Stadtverwaltungs-ZIP zu exportieren.

> Status: MVP in aktiver Entwicklung. Die App ist fuer interne Tests gedacht und noch keine finale Store-Version.

## Schnelllinks

Hier sind die wichtigsten Links gesammelt, damit man nicht suchen muss:

- Projekt auf GitHub: https://github.com/privatdavidgottschall-sudo/Plakat-Radar
- Projekt als ZIP herunterladen: https://github.com/privatdavidgottschall-sudo/Plakat-Radar/archive/refs/heads/main.zip
- GitHub Actions / fertige Test-APKs: https://github.com/privatdavidgottschall-sudo/Plakat-Radar/actions/workflows/android-debug-apk.yml
- Android Studio herunterladen: https://developer.android.com/studio
- Java JDK 17 herunterladen: https://adoptium.net/temurin/releases/?version=17
- Git herunterladen: https://git-scm.com/downloads
- Gradle-Downloads, nur zur Kontrolle: https://services.gradle.org/distributions/

## Grundprinzip

- lokale Datenspeicherung auf dem Geraet
- kein zentraler Cloud-Server
- Teamzugang ueber Teamleiter-QR-Code
- lokaler Nearby-Sync zwischen Teamgeraeten
- Sync-Pakete fuer Messenger, E-Mail oder Nearby Share
- Stadtverwaltungs-ZIP mit `plakatliste.csv` und Fotoordner

## Aktueller Funktionsumfang

- Plakat mit Foto, GPS, Adresse/Standorthinweis, Typ und Notiz erfassen
- Foto-Vorschau direkt im passenden Plakateintrag
- Plakatliste und Kartenansicht
- Plakate in der Naehe anzeigen
- Status aendern und Eintraege entfernen
- Teamleiter-QR-Code erzeugen
- zeitlich begrenzte QR-Einladung
- Team-Schluessel erneuern
- Geraete technisch sperren oder entsperren
- lokaler Nearby-Sync
- Sync-Paket teilen und importieren
- Stadtverwaltungs-ZIP exportieren
- Ohne-QR-Modus fuer lokale Erfassung und Stadtverwaltungs-Export
- moderne Dashboard-Navigation mit Start, Erfassen, Liste, Karte und Mehr

## Ohne-QR-Modus

Ohne QR-Code kann ein Geraet lokal arbeiten. Das ist praktisch, wenn vor Ort kein Teamleiter anwesend ist.

Im Ohne-QR-Modus moeglich:

- Plakate lokal erfassen
- Stadtverwaltungs-ZIP exportieren

Nicht moeglich:

- Team-Sync
- Sync-Paket teilen oder importieren
- Teamleiter-Funktionen

## App auf dem PC bauen, einfache Anleitung

Dieser Abschnitt ist fuer Leute gedacht, die nicht jeden Tag programmieren. Man braucht keine Zaubersprueche, aber ein paar Werkzeuge muessen auf dem PC liegen.

### Was brauche ich?

Am einfachsten ist diese Kombination:

1. **Android Studio**
   - Download: https://developer.android.com/studio
   - Das ist das Hauptprogramm zum Oeffnen und Bauen der App.

2. **Java JDK 17**
   - Download: https://adoptium.net/temurin/releases/?version=17
   - Bei der Auswahl am besten **JDK 17** nehmen, nicht nur JRE.
   - Windows-Nutzer nehmen normalerweise die `.msi`-Datei.

3. **Git**, optional aber hilfreich
   - Download: https://git-scm.com/downloads
   - Git braucht man, wenn man das Projekt direkt von GitHub klonen moechte.
   - Wer Git nicht nutzen will, kann das Projekt auch einfach als ZIP herunterladen.

4. **Das Projekt PlakatRadar**
   - GitHub-Seite: https://github.com/privatdavidgottschall-sudo/Plakat-Radar
   - ZIP direkt herunterladen: https://github.com/privatdavidgottschall-sudo/Plakat-Radar/archive/refs/heads/main.zip

5. **Internetverbindung**
   - Beim ersten Build werden Gradle und Android-Abhaengigkeiten heruntergeladen.

### Variante A: Projekt als ZIP herunterladen

Das ist fuer nicht so technikaffine Nutzer meist der einfachste Weg.

1. Diesen Link oeffnen:
   - https://github.com/privatdavidgottschall-sudo/Plakat-Radar/archive/refs/heads/main.zip
2. ZIP-Datei speichern.
3. ZIP-Datei entpacken.
4. Android Studio oeffnen.
5. Auf **Open** klicken.
6. Den entpackten Ordner auswaehlen.
7. Warten, bis Android Studio alles geladen hat.
8. Wenn Android Studio fehlende Pakete oder Lizenzen meldet, auf Installieren beziehungsweise Akzeptieren klicken.

### Variante B: Projekt mit Git herunterladen

Das ist fuer Leute, die schon Git installiert haben.

```bash
git clone https://github.com/privatdavidgottschall-sudo/Plakat-Radar.git
cd Plakat-Radar
```

Danach den Ordner in Android Studio oeffnen.

### Android SDK in Android Studio installieren

Falls Android Studio meckert, dass ein SDK fehlt:

1. Android Studio oeffnen.
2. Oben auf **Tools** klicken.
3. **SDK Manager** oeffnen.
4. Bei **Android SDK Platform 35** einen Haken setzen.
5. Bei **Android SDK Build-Tools 35.0.0** einen Haken setzen.
6. Unten auf **Apply** klicken.
7. Installieren lassen.
8. Lizenzabfragen bestaetigen.

Wenn Android Studio automatisch fragt, ob fehlende SDKs installiert werden sollen, kann man das normalerweise direkt bestaetigen.

## App mit Android Studio bauen

1. Projekt in Android Studio oeffnen.
2. Warten, bis unten keine Ladeanzeige mehr laeuft.
3. Oben im Menue **Build** anklicken.
4. **Build Bundle(s) / APK(s)** anklicken.
5. **Build APK(s)** anklicken.
6. Warten, bis Android Studio fertig ist.

Wenn alles klappt, meldet Android Studio sinngemaess:

```text
APK generated successfully
```

Die fertige APK liegt dann hier:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Diese APK ist eine Debug-/Testversion. Sie ist zum Ausprobieren gedacht, nicht als finale Play-Store-Version.

## App ueber die Konsole bauen

Im Projekt liegt ein lokales Gradle-Startskript. Es laedt beim ersten Start automatisch Gradle 8.10.2 herunter.

### Windows

Im Projektordner eine Eingabeaufforderung oder PowerShell oeffnen und ausfuehren:

```bat
gradlew.bat :app:assembleDebug
```

### macOS oder Linux

Im Projektordner ein Terminal oeffnen und ausfuehren:

```bash
sh ./gradlew :app:assembleDebug
```

Die fertige APK liegt danach hier:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Fertige APK ueber GitHub Actions herunterladen

Wer nicht selbst bauen will, kann die Test-APK ueber GitHub Actions herunterladen.

1. GitHub Actions oeffnen:
   - https://github.com/privatdavidgottschall-sudo/Plakat-Radar/actions/workflows/android-debug-apk.yml
2. Den neuesten erfolgreichen Lauf anklicken. Er sollte gruen markiert sein.
3. Unten bei **Artifacts** das Paket **PlakatRadar-debug-apk** herunterladen.
4. ZIP entpacken.
5. Die APK auf ein Android-Handy kopieren und installieren.

Wichtig: Android fragt bei selbst geladenen APKs oft nach der Erlaubnis, Apps aus unbekannten Quellen zu installieren. Das muss man fuer den jeweiligen Dateimanager oder Browser erlauben.

## Haeufige Probleme beim Bauen

### Java nicht gefunden

Dann fehlt wahrscheinlich Java JDK 17.

Loesung:

- JDK 17 herunterladen: https://adoptium.net/temurin/releases/?version=17
- installieren
- Android Studio oder den PC neu starten

### Android SDK fehlt

Dann fehlt das Android SDK 35.

Loesung:

- Android Studio oeffnen
- SDK Manager oeffnen
- Android SDK Platform 35 installieren
- Android Build-Tools 35.0.0 installieren

### Lizenzen nicht akzeptiert

Dann wurden die Android-Lizenzen noch nicht bestaetigt.

Loesung:

- Android Studio oeffnen
- SDK Manager oeffnen
- fehlende Pakete installieren
- Lizenzabfragen bestaetigen

### Gradle-Download schlaegt fehl

Dann fehlt Internet oder eine Firewall blockiert den Download.

Loesung:

- Internet pruefen
- spaeter erneut versuchen
- anderes Netzwerk testen
- Gradle-Downloadseite zur Kontrolle: https://services.gradle.org/distributions/

### Auf macOS/Linux darf `gradlew` nicht ausgefuehrt werden

Dann kann man trotzdem diesen Befehl nutzen:

```bash
sh ./gradlew :app:assembleDebug
```

Oder man macht die Datei einmalig ausfuehrbar:

```bash
chmod +x gradlew
./gradlew :app:assembleDebug
```

## Build und Tests

Das Projekt baut direkt aus dem Kotlin-Quellcode. Es gibt keine Python-Patch-Skripte, die im GitHub-Workflow zur Build-Zeit Kotlin-Code umschreiben. Der Quellcode im Repository ist die Wahrheit.

Wichtige Befehle:

```bash
sh ./gradlew :app:testDebugUnitTest
sh ./gradlew :app:assembleDebug
```

Unter Windows:

```bat
gradlew.bat :app:testDebugUnitTest
gradlew.bat :app:assembleDebug
```

## GitHub Actions

Der Workflow `Build Android APK` baut automatisch eine Debug-APK. Das Artefakt heisst:

```text
PlakatRadar-debug-apk
```

Direktlink zum Workflow:

```text
https://github.com/privatdavidgottschall-sudo/Plakat-Radar/actions/workflows/android-debug-apk.yml
```

## Wichtige Dateien

```text
app/src/main/java/de/bsw/plakatradar/MainActivity.kt
app/src/main/java/de/bsw/plakatradar/core/AccessPolicy.kt
app/src/main/java/de/bsw/plakatradar/core/TeamInvite.kt
app/src/main/java/de/bsw/plakatradar/core/SyncMerge.kt
app/src/main/java/de/bsw/plakatradar/core/SyncBundleCodec.kt
app/src/main/java/de/bsw/plakatradar/core/OfficialExport.kt
app/src/main/java/de/bsw/plakatradar/data/LocalRepository.kt
app/src/main/java/de/bsw/plakatradar/sync/NearbySyncManager.kt
app/src/test/java/de/bsw/plakatradar/core/AccessPolicyTest.kt
app/src/test/java/de/bsw/plakatradar/core/SyncMergeTest.kt
.github/workflows/android-debug-apk.yml
gradlew
gradlew.bat
gradle/wrapper/gradle-wrapper.properties
```

## Architekturstand

Der aktuelle MVP ist bewusst pragmatisch gebaut. Die naechsten sinnvollen Schritte sind:

- schrittweise MVVM/StateFlow-Struktur
- weitere Unit-Tests fuer Export, QR-Einladung und Sync-Pakete
- UI fuer Team-Schluessel-Erneuerung und Geraetesperre
- Release-Build mit fester Signatur
- Dokumentation in `docs/` buendeln

## Einschraenkungen

- kein echter Internet-Live-Sync ueber weite Entfernung
- keine zentrale Benutzerverwaltung
- OpenStreetMap-Kartenkacheln brauchen Internet, sofern sie nicht gecacht sind
- GitHub-Actions-APKs sind Debug-/Test-Builds
- fuer echte Updates ohne Deinstallation ist spaeter eine feste Release-Signatur noetig
