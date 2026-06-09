# PlakatRadar auf GitHub bauen

Diese Version enthält eine GitHub-Actions-Datei:

`.github/workflows/android-debug-apk.yml`

## So geht es

1. Neues GitHub-Repository erstellen.
2. Den Inhalt dieses Ordners ins Repository hochladen.
3. In GitHub auf **Actions** gehen.
4. Workflow **Build Android APK** auswählen.
5. **Run workflow** anklicken.
6. Nach dem Build unten bei **Artifacts** die Datei **PlakatRadar-debug-apk** herunterladen.
7. ZIP entpacken. Darin liegt die Debug-APK.

## Wichtig

Das ist eine Debug-APK. Für private Tests reicht sie. Für Play Store oder öffentliche Verteilung braucht man später eine signierte Release-APK oder ein AAB.

Firebase wird nicht benötigt. Es gibt keine `google-services.json`, weil diese Version lokalen P2P-Sync und Messenger-Sync nutzt.
