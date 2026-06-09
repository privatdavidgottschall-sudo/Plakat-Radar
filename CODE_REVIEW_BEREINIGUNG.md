# Code-Review-Bereinigung

Nach erneuter statischer Prüfung wurden Altlasten entfernt.

## Entfernt

- ungenutzter Wartemodus `PendingApprovalScreen`
- ungenutzte ViewModel-Funktion `approveDevice(...)`
- ungenutzte Repository-Funktion `approveDevice(...)`
- deprecated unsicherer Sync-Export ohne Team-Schlüssel
- deprecated unsicherer Sync-Import ohne lokale Team-Prüfung

## Ergebnis

Der Direktbeitritt per Teamleiter-QR bleibt erhalten:

1. Teamleiter zeigt QR-Code.
2. Teammitglied scannt.
3. Teammitglied ist direkt im Team.

Der QR-Code bleibt 10 Minuten gültig. Verschlüsselung, Screenshot-Schutz, Google-Platzhalter, Berechtigungs-Popup und Nähe-/Kartenfunktionen bleiben erhalten.
