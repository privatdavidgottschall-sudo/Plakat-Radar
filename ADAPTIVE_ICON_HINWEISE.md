# Adaptive Icon Ergänzung

In dieser Version wurden zusätzlich moderne Android-Adaptive-Icons ergänzt.

## Neu hinzugefügt

- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- `app/src/main/res/drawable/ic_launcher_foreground.png`
- `app/src/main/res/values/colors.xml`

## Technische Wirkung

- Neuere Android-Geräte nutzen jetzt ein echtes Adaptive Icon.
- Ältere Geräte nutzen weiterhin die vorhandenen PNG-Fallbacks in den `mipmap-*`-Ordnern.
- Das verbessert die Darstellung auf modernen Launchern und bei unterschiedlichen Icon-Masken.

## Hinweis

Die Vordergrundgrafik verwendet das erzeugte Fabio-De-Masi-Motiv.
Der Hintergrund ist als heller Farbton definiert (`#F3E8DD`), damit das Icon auf verschiedenen Launchern sauber angezeigt wird.
