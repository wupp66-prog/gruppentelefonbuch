# Gruppen-Telefonbuch

Android-App (Kotlin, Jetpack Compose, Room): Kontakte in selbst angelegten Gruppen
(Familie, Firma, Gruppenleiter …) speichern. Gruppe antippen = alle Kontakte der Gruppe.

- Ein Kontakt kann in mehreren Gruppen sein; „Alle Kontakte“ zeigt auch Kontakte ohne Gruppe
- Pro Kontakt mehrere Telefonnummern und E-Mail-Adressen (mit Bezeichnung) und eine Wohnadresse
- Nummer antippen = anrufen, SMS-Symbol = SMS, E-Mail antippen = E-Mail-Programm mit Empfänger,
  Adresse antippen = Karte
- Gruppenaktionen (Menü oben rechts in der Gruppe): Rundruf, SMS an alle, E-Mail an alle (An oder BCC)
- Datensicherung als JSON-Datei erstellen / wiederherstellen (Menü auf der Startseite)
- Suche über Name, Nummer, E-Mail und Adresse
- Keine Berechtigungen nötig, alle Daten bleiben lokal auf dem Handy

Bauen: Ordner in Android Studio öffnen und starten, oder per GitHub (Workflow
`.github/workflows/build.yml` erzeugt die Debug-APK).
