# Kochdu Druck – WebView-App für Sunmi V3 Plus

Kleine Android-App, die die **kochdu-Küche** in einer WebView lädt und Bons
**lautlos über den eingebauten Sunmi-Drucker** druckt – über genau die
SDK-Schnittstelle, die auch deine funktionierende Sunmi-Test-App nutzt.

kochdu ruft bereits `window.SunmiPrinter.printHtml(<bon-html>)` auf. Diese App
stellt genau diese Brücke bereit: sie wandelt den HTML-Bon in ein Bild um und
druckt es über das Sunmi-Drucker-SDK. Kein Chrome-Druckdialog mehr.

---

## Was du brauchst
- **Android Studio** (kostenlos) auf einem PC/Mac – zum Bauen der APK.
- Den **Sunmi V3 Plus** zum Installieren der APK.

> Hinweis: Ich (KI) kann die APK nicht selbst kompilieren. Dieses Projekt ist
> fertig vorbereitet – du oder ein Entwickler baut es einmal (ca. 10–15 Min).

## APK bauen (Android Studio)
1. Diesen Ordner `sunmi-kochdu-print` in Android Studio öffnen
   (*Open* → den Ordner wählen). Gradle synchronisiert automatisch.
2. Oben Menü **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
3. Nach dem Build erscheint unten ein Link **„locate"** – das ist deine
   `app-debug.apk` (unter `app/build/outputs/apk/...`).

## Auf dem V3 Plus installieren
1. APK auf das Gerät kopieren (USB, oder per Link/Mail/Cloud herunterladen).
2. In den Android-Einstellungen **„Unbekannte Apps installieren"** für den
   Datei-Manager/Browser erlauben.
3. APK antippen → installieren.
4. App **„Kochdu Druck"** öffnen → es lädt die Küche → mit Chef/Admin einloggen.
5. Bestellung → **„Annehmen & Drucken"**: der Bon wird jetzt direkt gedruckt,
   ohne Druckdialog.

## Wichtige Einstellungen (ggf. anpassen)
In `app/src/main/java/at/kochdu/printbridge/MainActivity.kt`:
- `START_URL` – Standard ist `https://www.kochdu.at/kitchen`.
- `PRINT_WIDTH` – Druckbreite in Punkten:
  - **58 mm Papier → 384** (Standard, bei Handhelds meist so)
  - **80 mm Papier → 576**
  Wenn der Bon rechts abgeschnitten ist oder zu schmal druckt, diesen Wert anpassen.

## Wenn der Build wegen der Sunmi-Bibliothek fehlschlägt
Die App nutzt `com.sunmi:printerlibrary:1.0.18` (Repo `maven.sunmi.com`).
Falls die Version/Repo nicht auflöst:
- Aktuelle Koordinaten der **„Sunmi Printer SDK / Inner Printer"**-Bibliothek
  auf **developer.sunmi.com** nachsehen und in `app/build.gradle` eintragen.
- Die App-API (`InnerPrinterManager`, `SunmiPrinterService.printBitmap/lineWrap`)
  ist bei allen aktuellen Versionen gleich.

## Tipp: als Standard-/Kiosk-App
Damit das Gerät beim Start direkt in der Küche landet, kannst du
„Kochdu Druck" in den Sunmi-Einstellungen als **Standard-Home-App / Kiosk**
festlegen (Sunmi hat dafür einen Lockscreen/Kiosk-Modus).

## Voll automatisch (ohne „Annehmen"-Tippen)
Sag mir Bescheid – dann ergänze ich kochdu so, dass **neue Bestellungen
automatisch** gedruckt werden, sobald sie reinkommen (sobald diese App läuft),
ganz ohne Tippen.
