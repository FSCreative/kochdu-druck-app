# Schritt-für-Schritt: Kochdu-Druck-App auf den Sunmi V3 Plus bringen

Es gibt zwei Wege zur fertigen App-Datei (APK). **Weg A** braucht KEIN
Android Studio und läuft über GitHub (das nutzt du ja schon) – empfohlen.

---

## WEG A – APK ohne Android Studio bauen (über GitHub, empfohlen)

**1. Neues GitHub-Repository anlegen**
- Auf github.com einloggen → oben rechts „+“ → **New repository**.
- Name z. B. `kochdu-druck-app`, **Private**, „Create repository“.

**2. Projektdateien hochladen**
- Im neuen Repo: **Add file → Upload files**.
- Den **Inhalt** des entpackten Ordners `sunmi-kochdu-print` hineinziehen
  (alle Dateien/Ordner: `app/`, `.github/`, `build.gradle`, `settings.gradle`,
  `gradle.properties`, …). Wichtig: den Ordnerinhalt hochladen, nicht den
  Ordner selbst verschachteln.
- Unten **Commit changes**.

**3. Bauen lassen**
- Oben im Repo auf den Reiter **Actions**.
- Der Workflow **„Build APK“** startet automatisch (oder „Run workflow“ klicken).
- Warte ~3–5 Min, bis ein grüner Haken erscheint.

**4. APK herunterladen**
- Auf den fertigen (grünen) Workflow-Lauf klicken.
- Unten unter **Artifacts** → **kochdu-druck-apk** herunterladen (ist eine ZIP).
- ZIP entpacken → darin liegt **`app-debug.apk`**.

→ Weiter bei **„APK auf dem V3 Plus installieren“**.

---

## WEG B – Mit Android Studio (falls du es lieber lokal baust)

1. Android Studio installieren (kostenlos).
2. Ordner `sunmi-kochdu-print` öffnen (*Open*), Gradle synchronisiert.
3. Menü **Build → Build APK(s)**.
4. Nach dem Build unten **„locate“** → `app-debug.apk`.

---

## APK auf dem V3 Plus installieren

1. Die `app-debug.apk` aufs Gerät bringen (USB-Kabel, oder per Mail/Cloud/Link
   auf dem Gerät herunterladen).
2. Auf dem V3 Plus: **Einstellungen → Apps → Spezieller Zugriff →
   Unbekannte Apps installieren** → dem Datei-Manager/Browser **erlauben**.
3. Die APK antippen → **Installieren**.
4. App **„Kochdu Druck“** öffnen.
5. Es lädt die Küche → mit **Chef-/Admin-Konto einloggen**.
6. Eine Bestellung → **„Annehmen & Drucken“** → der Bon wird jetzt **direkt
   gedruckt**, ohne Druckdialog.

---

## Falls der Bon falsch breit ist
Datei `app/src/main/java/at/kochdu/printbridge/MainActivity.kt`:
- `PRINT_WIDTH = 384`  → für **58 mm** Papier (Standard)
- `PRINT_WIDTH = 576`  → für **80 mm** Papier
Wert ändern, neu bauen (Weg A: Datei im Repo bearbeiten → Actions baut neu).

## Falls der Build an der Sunmi-Bibliothek scheitert
In `app/build.gradle` die Zeile `com.sunmi:printerlibrary:1.0.18` ggf. auf die
aktuelle Version anpassen (siehe developer.sunmi.com → Printer SDK).

## Tipp
Du kannst „Kochdu Druck“ in den Sunmi-Einstellungen als **Start-/Kiosk-App**
festlegen, damit das Gerät direkt in der Küche landet.
