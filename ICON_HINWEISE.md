# Änderungen in dieser Version

- App-Icon integriert: Fabio De Masi mit Daumen hoch als Launcher-Icon.
- Folgende Android-Icon-Dateien wurden ergänzt:
  - app/src/main/res/mipmap-mdpi/ic_launcher.png
  - app/src/main/res/mipmap-hdpi/ic_launcher.png
  - app/src/main/res/mipmap-xhdpi/ic_launcher.png
  - app/src/main/res/mipmap-xxhdpi/ic_launcher.png
  - app/src/main/res/mipmap-xxxhdpi/ic_launcher.png
  - entsprechende ic_launcher_round.png-Dateien
- AndroidManifest.xml ergänzt um:
  - android:icon="@mipmap/ic_launcher"
  - android:roundIcon="@mipmap/ic_launcher_round"
- Zusätzlich die unskalierte Quelldatei abgelegt unter:
  - app/src/main/res/drawable-nodpi/fabio_dm_app_icon_source.png

Hinweis: Für ein noch schärferes modernes Adaptive Icon könnte man später zusätzlich
XML-Adaptive-Icons (mipmap-anydpi-v26) ergänzen. Die jetzige Variante ist bewusst
einfach und GitHub-/Build-freundlich gehalten.
