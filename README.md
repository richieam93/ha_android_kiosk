# HA Android Kiosk

**HA Android Kiosk** is a Home Assistant custom integration with a dedicated Android kiosk browser app for wall tablets, dashboards, information screens and older Android devices.

**HA Android Kiosk** ist eine Home-Assistant-Custom-Integration mit eigener Android-Kiosk-Browser-App für Wandtablets, Dashboard-Displays, Info-Bildschirme und ältere Android-Geräte.

> Current version / Aktuelle Version: **Integration 1.9.18 / Android App 1.9.18**  
> Installation: **HACS custom repository + Android APK**

---

## Deutsch

### 1. Zweck dieses Projekts

HA Android Kiosk macht aus einem Android-Tablet ein Home-Assistant-Wanddisplay.

Die Home-Assistant-Integration stellt die Verwaltung, Services, Automationsbefehle und das Sidebar-Panel bereit. Die Android-App läuft auf dem Tablet, öffnet Home Assistant in einer WebView und zeigt die konfigurierten Dashboard-Seiten im Kiosk-Modus an.

Das Webinterface dient zur Einrichtung, zum Testen und zum Kopieren von YAML-Beispielen. Der normale Betrieb soll anschließend über Home-Assistant-Automationen laufen.

Typischer Ablauf:

1. Integration über HACS installieren.
2. Android-APK auf dem Tablet installieren.
3. App starten und in Home Assistant anmelden.
4. Im Sidebar-Panel eigene Dashboard-Seiten für das Tablet hinterlegen.
5. Browser, Kiosk-Modus, Hintergrund und Rotation konfigurieren.
6. YAML-Automationen kopieren und eigene Sensoren/Entities einsetzen.
7. Tablet automatisch über Automationen steuern lassen.

---

### 2. Voraussetzungen

Benötigt wird:

- Home Assistant mit HACS.
- Ein Android-Gerät oder Tablet.
- Die Android-APK aus dem GitHub-Release oder ein lokal gebautes APK.
- Netzwerkzugriff vom Tablet auf Home Assistant.
- Ein Home-Assistant-Benutzerkonto für die Anmeldung in der Android-App.

Empfohlen wird ein eigenes Home-Assistant-Benutzerkonto für das Wandtablet. Dieses Konto kann ein eigenes Dashboard und eigene Sichtbarkeiten erhalten.

---

### 3. Installation über HACS

1. Home Assistant öffnen.
2. **HACS** öffnen.
3. Rechts oben das Menü öffnen.
4. **Custom repositories** wählen.
5. Die GitHub-Repository-URL einfügen.
6. Kategorie **Integration** auswählen.
7. Repository hinzufügen.
8. In HACS nach **HA Android Kiosk** suchen.
9. Integration installieren.
10. Home Assistant neu starten.

Nach dem Neustart erscheint im Sidebar-Menü das Panel **Android Kiosk**.

---

### 4. Android-APK installieren

Die APK wird separat installiert. Sie gehört nicht direkt zur HACS-Installation.

#### Variante A: APK über Datei installieren

1. APK aus dem GitHub-Release herunterladen.
2. APK auf das Tablet kopieren.
3. Auf dem Tablet die APK öffnen.
4. Installation aus unbekannten Quellen erlauben.
5. App installieren.
6. App starten.

#### Variante B: APK per ADB installieren

```bash
adb install -r app-debug.apk
```

Bei einem Update:

```bash
adb install -r app-debug.apk
```

Falls Android Berechtigungen nicht sauber übernimmt, kann eine Neuinstallation helfen:

```bash
adb uninstall com.hakiosk.android
adb install app-debug.apk
```

---

### 5. App starten und in Home Assistant anmelden

Beim ersten Start der Android-App:

1. Sprache wählen: **Deutsch** oder **English**.
2. Home-Assistant-Adresse eintragen, zum Beispiel:

```text
http://homeassistant.local:8123
```

oder:

```text
http://192.168.1.240:8123
```

3. Home Assistant in der App öffnen.
4. Mit einem Home-Assistant-Benutzer anmelden.
5. Das gewünschte Dashboard öffnen.
6. Danach kann das Gerät im Home-Assistant-Panel **Android Kiosk** verwaltet werden.

Die App registriert sich nach der Anmeldung bei Home Assistant. Danach erscheint das Tablet im Android-Kiosk-Panel.

---

### 6. Tablet im Android-Kiosk-Panel auswählen

Nach dem ersten Start meldet sich die Android-App bei Home Assistant.

Im Sidebar-Panel **Android Kiosk**:

1. Gerät auswählen.
2. Prüfen, ob die Geräte-ID passt.
3. Gerätename optional anpassen.
4. Speichern.

Die Geräte-ID ist wichtig für Automationen. Sie wird in YAML-Beispielen als Platzhalter verwendet.

Beispiel-Platzhalter:

```yaml
device: android_kiosk_1234567890abcdef
```

In den eigenen Automationen muss dieser Wert durch die tatsächliche Geräte-ID ersetzt werden.

---

### 7. Eigene Dashboard-Seiten für das Tablet anlegen

In Home Assistant sollte ein eigenes Dashboard für das Tablet erstellt werden, zum Beispiel:

```text
/badezimmer-display/wetter
/badezimmer-display/kalender
/badezimmer-display/sensoren
```

Im Panel **Android Kiosk → Screens** können diese Seiten hinterlegt werden.

Pro Seite können gesetzt werden:

- Name der Seite.
- URL oder Pfad.
- Anzeigedauer in Sekunden.
- optionale Seitengröße in Prozent.

Beispiel:

```text
Name: Wetter
URL: /badezimmer-display/wetter
Dauer: 20
Größe: 120
```

Danach:

1. **Speichern** klicken.
2. **Rotation ans Gerät senden** klicken.

Das Tablet rotiert anschließend automatisch durch die hinterlegten Seiten.

---

### 8. Browser und Kiosk-Modus konfigurieren

Im Panel **Android Kiosk → Browser** werden Browser- und Kiosk-Einstellungen gesetzt.

Wichtige Optionen:

- Startseite.
- Vollbild aktivieren.
- Display wach halten.
- Home-Assistant-Navigation ausblenden.
- Hintergrund zuerst laden.
- WebView transparent über Hintergrund legen.
- Dashboard-Sprache Deutsch/Englisch.
- Selbst-Sync-Intervall.
- Recovery/Watchdog.
- Standard-Seitengröße.

Empfohlener Ablauf:

1. Startseite setzen.
2. Kiosk-Modus aktivieren.
3. Display wach halten aktivieren.
4. Selbst-Sync aktivieren, zum Beispiel `300` Sekunden.
5. Speichern.
6. **Browser-Konfiguration senden** klicken.

Der Selbst-Sync sorgt dafür, dass das Tablet die gespeicherten Einstellungen regelmäßig selbst aus Home Assistant abholt. Das ist besonders wichtig, wenn das Tablet längere Zeit ausgeschaltet oder offline war.

---

### 9. Hintergrund-Slideshow einrichten

Im Panel **Android Kiosk → Hintergrund** können Hintergrundbilder und Alben verwaltet werden.

Ablauf:

1. Album erstellen oder vorhandenes Album auswählen.
2. Bilder hochladen.
3. Album als aktive Slideshow wählen.
4. Intervall, Zufall, Deckkraft und Darstellung einstellen.
5. Speichern.
6. **Slideshow ans Gerät senden** klicken.

Die Android-App zeichnet den Hintergrund nativ hinter der Home-Assistant-WebView. Dadurch bleibt der Hintergrund auch sichtbar, wenn das Dashboard transparent geladen wird.

Für ältere Tablets sollten Hintergrundbilder nicht unnötig groß sein. Empfohlen sind optimierte Bilder statt sehr große Originalfotos.

---

### 10. Wofür der Bereich „Automation & Tests“ gedacht ist

Der Bereich **Automation & Tests** ist kein dauerhaftes Bedien-Dashboard.

Er ist gedacht für:

- kurze Tests von Ticker, Banner, Alert, Wetter, Kamera, Status-Overlay, TTS und Sound;
- Vorschau, wie Meldungen auf dem Tablet aussehen;
- kopierfertige YAML-Beispiele;
- Diagnosebefehle wie Cache leeren oder App neu starten.

Der echte Betrieb soll über Home-Assistant-Automationen laufen. Das Webinterface hilft nur beim Einrichten, Testen und Kopieren der passenden YAML-Vorlagen.

---

### 11. Wie Automationen funktionieren

Eine Home-Assistant-Automation besteht meist aus drei Teilen:

- **trigger**: Wann soll etwas passieren?
- **condition**: Unter welcher Bedingung darf es passieren?
- **action**: Was soll auf dem Tablet angezeigt oder abgespielt werden?

Beispielstruktur:

```yaml
alias: Beispiel - Meldung auf Tablet anzeigen
trigger:
  - platform: state
    entity_id: binary_sensor.beispiel_sensor
    to: "on"
condition: []
action:
  - service: ha_android_kiosk.show_banner
    data:
      device: android_kiosk_1234567890abcdef
      title: "Info"
      message: "Sensor wurde ausgelöst."
      position: top
      duration: 10
mode: single
```

Wichtig: `device` muss immer zur Geräte-ID des Tablets passen.

---

### 12. Beispiel: Display-Seite wechseln

Diese Automation öffnet eine bestimmte Dashboard-Seite auf dem Tablet.

```yaml
alias: Kiosk - Wetterseite öffnen
trigger:
  - platform: time
    at: "07:00:00"
action:
  - service: ha_android_kiosk.open_url
    data:
      device: android_kiosk_1234567890abcdef
      url: /badezimmer-display/wetter
mode: single
```

---

### 13. Beispiel: Rotation ans Tablet senden

```yaml
alias: Kiosk - Seitenrotation morgens setzen
trigger:
  - platform: time
    at: "06:30:00"
action:
  - service: ha_android_kiosk.set_pages
    data:
      device: android_kiosk_1234567890abcdef
      pages:
        - name: Wetter
          url: /badezimmer-display/wetter
          duration: 20
          zoom_percent: 120
        - name: Kalender
          url: /badezimmer-display/kalender
          duration: 20
          zoom_percent: 115
        - name: Sensoren
          url: /badezimmer-display/sensoren
          duration: 15
      rotate: true
      pause_on_touch: true
      touch_pause_seconds: 30
mode: single
```

---

### 14. Beispiel: Meldung als Banner anzeigen

```yaml
alias: Kiosk - Fenster Bad offen
trigger:
  - platform: state
    entity_id: binary_sensor.fenster_bad
    to: "on"
action:
  - service: ha_android_kiosk.show_banner
    data:
      device: android_kiosk_1234567890abcdef
      title: "Fenster offen"
      message: "Das Badezimmerfenster ist geöffnet."
      position: top
      duration: 20
      color: "#f59e0b"
      text_color: "#ffffff"
      wake_screen: true
mode: single
```

---

### 15. Beispiel: TTS-Durchsage abspielen

```yaml
alias: Kiosk - Waschmaschine fertig
trigger:
  - platform: state
    entity_id: sensor.waschmaschine_status
    to: "fertig"
action:
  - service: ha_android_kiosk.tts_speak
    data:
      device: android_kiosk_1234567890abcdef
      text: "Die Waschmaschine ist fertig."
      language: de-DE
      volume: 100
      stream: alarm
  - service: ha_android_kiosk.show_banner
    data:
      device: android_kiosk_1234567890abcdef
      title: "Waschmaschine"
      message: "Die Waschmaschine ist fertig."
      duration: 30
      position: bottom
mode: single
```

---

### 16. Beispiel: Türklingel mit Kamera anzeigen

```yaml
alias: Kiosk - Türklingel Kamera anzeigen
trigger:
  - platform: state
    entity_id: binary_sensor.tuerklingel
    to: "on"
action:
  - service: ha_android_kiosk.open_url
    data:
      device: android_kiosk_1234567890abcdef
      url: /badezimmer-display/kamera
  - service: ha_android_kiosk.show_camera
    data:
      device: android_kiosk_1234567890abcdef
      camera_entity: camera.haustuer
      title: "Haustür"
      position: fullscreen
      duration: 30
      refresh_seconds: 5
      wake_screen: true
  - service: ha_android_kiosk.tts_speak
    data:
      device: android_kiosk_1234567890abcdef
      text: "Es hat an der Haustür geklingelt."
      language: de-DE
      volume: 100
      stream: alarm
mode: restart
```

---

### 17. Beispiel: Status-Overlay aus Sensoren

```yaml
alias: Kiosk - Hausstatus bei Bewegung anzeigen
trigger:
  - platform: state
    entity_id: binary_sensor.bewegung_bad
    to: "on"
action:
  - service: ha_android_kiosk.show_status_overlay
    data:
      device: android_kiosk_1234567890abcdef
      title: "Hausstatus"
      entities:
        - sensor.bad_temperatur
        - sensor.bad_luftfeuchtigkeit
        - binary_sensor.fenster_bad
        - sensor.waschmaschine_status
      position: center
      duration: 25
      color: "#111827"
      text_color: "#ffffff"
      wake_screen: true
mode: restart
```

---

### 18. Beispiel: Wetter morgens anzeigen

```yaml
alias: Kiosk - Wetter morgens anzeigen
trigger:
  - platform: time
    at: "06:45:00"
action:
  - service: ha_android_kiosk.show_weather
    data:
      device: android_kiosk_1234567890abcdef
      weather_entity: weather.home
      title: "Wetter heute"
      position: top-left
      layout: full
      duration: 60
      refresh_seconds: 300
      show_forecast: true
      show_pressure: true
mode: single
```

---

### 19. Beispiel: Morgen-Sequenz

Eine Sequenz kann mehrere Anzeigen nacheinander abspielen.

```yaml
alias: Kiosk - Morgeninformation
trigger:
  - platform: time
    at: "07:15:00"
action:
  - service: ha_android_kiosk.show_overlay_sequence
    data:
      device: android_kiosk_1234567890abcdef
      steps:
        - type: banner
          title: "Guten Morgen"
          message: "Hier sind die wichtigsten Informationen."
          duration: 8
          position: top
        - type: weather
          weather_entity: weather.home
          title: "Wetter"
          duration: 20
          position: top-left
        - type: status
          title: "Hausstatus"
          duration: 20
          entities:
            - sensor.bad_temperatur
            - binary_sensor.fenster_bad
            - sensor.waschmaschine_status
mode: single
```

---

### 20. Beispiel: Overlays wieder schließen

```yaml
alias: Kiosk - Anzeige aufräumen
trigger:
  - platform: time
    at: "23:00:00"
action:
  - service: ha_android_kiosk.clear_overlays
    data:
      device: android_kiosk_1234567890abcdef
mode: single
```

---

### 21. Wichtige Services

| Service | Funktion |
| --- | --- |
| `ha_android_kiosk.open_url` | Dashboard-Seite öffnen |
| `ha_android_kiosk.set_pages` | Seitenrotation setzen |
| `ha_android_kiosk.set_browser_config` | Browser/Kiosk/Sprache/Zoom konfigurieren |
| `ha_android_kiosk.set_background_slideshow` | Hintergrund-Slideshow senden |
| `ha_android_kiosk.show_ticker` | Ticker anzeigen |
| `ha_android_kiosk.show_banner` | Banner anzeigen |
| `ha_android_kiosk.show_alert` | Alert anzeigen |
| `ha_android_kiosk.show_weather` | Wetter-Overlay anzeigen |
| `ha_android_kiosk.show_camera` | Kamera-Overlay anzeigen |
| `ha_android_kiosk.show_status_overlay` | Sensoren als Status-Overlay anzeigen |
| `ha_android_kiosk.show_overlay_sequence` | mehrere Overlays nacheinander anzeigen |
| `ha_android_kiosk.tts_speak` | TTS-Durchsage abspielen |
| `ha_android_kiosk.play_media` | Audio/Medien abspielen |
| `ha_android_kiosk.clear_overlays` | Overlays schließen |
| `ha_android_kiosk.clear_webview_cache` | WebView-Cache leeren |
| `ha_android_kiosk.sync_settings` | Tablet zum Abrufen gespeicherter Einstellungen auffordern |
| `ha_android_kiosk.recover_dashboard` | Anzeige/Kiosk-Modus reparieren |

---

### 22. Weitere YAML-Beispiele

Weitere kopierfertige Beispiele befinden sich im Repository:

```text
docs/YAML_EXAMPLES_DE.md
docs/AUTOMATION_LAB_DE.md
examples/automation_lab_all_examples.yaml
examples/display_rotation.yaml
examples/ticker_from_sensors.yaml
examples/doorbell_camera.yaml
examples/status_overlay_sensors.yaml
examples/morning_sequence.yaml
examples/weather_overlay.yaml
examples/washing_machine_tts.yaml
examples/clear_overlays.yaml
```

In den Beispielen müssen nur folgende Werte angepasst werden:

- `device`
- Dashboard-Pfade wie `/badezimmer-display/wetter`
- Sensoren wie `sensor.bad_temperatur`
- Binary-Sensoren wie `binary_sensor.fenster_bad`
- Kameras wie `camera.haustuer`
- Weather-Entities wie `weather.home`

---

### 23. Fehlerbehebung

#### Tablet wird nicht angezeigt

- App auf dem Tablet starten.
- Home-Assistant-URL prüfen.
- Anmeldung in Home Assistant prüfen.
- Netzwerkverbindung prüfen.
- Home Assistant neu starten.

#### Dashboard bleibt leer

- Browser-Konfiguration erneut senden.
- Rotation erneut senden.
- Slideshow erneut senden.
- Service `ha_android_kiosk.recover_dashboard` ausführen.
- WebView-Cache leeren.

#### Hintergrund wird nicht angezeigt

- Hintergrund-Slideshow erneut senden.
- Prüfen, ob ein Album aktiv ist.
- Prüfen, ob Bilder im Album vorhanden sind.
- WebView transparent aktivieren.
- Hintergrund zuerst laden aktivieren.

#### TTS ist zu leise

- Android-Gerätelautstärke prüfen.
- In der Automation `volume: 100` setzen.
- `stream: alarm` oder `stream: music` testen.
- Audio-Fokus aktivieren, falls verfügbar.

#### Sprache bleibt gemischt

- HACS aktualisieren.
- Home Assistant neu starten.
- Browser hart neu laden.
- Cache von Home Assistant im Browser leeren.
- Admin-Sprache erneut auswählen.

---

## English

### 1. Purpose of this project

HA Android Kiosk turns an Android tablet into a Home Assistant wall display.

The Home Assistant integration provides device management, services, automation commands and the sidebar panel. The Android app runs on the tablet, opens Home Assistant inside a WebView and displays the configured dashboard pages in kiosk mode.

The web interface is meant for setup, testing and copying YAML examples. Regular operation should then be handled by Home Assistant automations.

Typical workflow:

1. Install the integration through HACS.
2. Install the Android APK on the tablet.
3. Start the app and sign in to Home Assistant.
4. Add the tablet dashboard pages in the sidebar panel.
5. Configure browser, kiosk mode, background and rotation.
6. Copy YAML automations and replace the example sensors/entities.
7. Let Home Assistant automations control the tablet automatically.

---

### 2. Requirements

Required:

- Home Assistant with HACS.
- An Android device or tablet.
- The Android APK from the GitHub release or a locally built APK.
- Network access from the tablet to Home Assistant.
- A Home Assistant user account for signing in inside the Android app.

A separate Home Assistant user account for the wall tablet is recommended. This account can have its own dashboard and visibility settings.

---

### 3. Install through HACS

1. Open Home Assistant.
2. Open **HACS**.
3. Open the menu in the top-right corner.
4. Select **Custom repositories**.
5. Paste the GitHub repository URL.
6. Select category **Integration**.
7. Add the repository.
8. Search for **HA Android Kiosk** in HACS.
9. Install the integration.
10. Restart Home Assistant.

After the restart, the **Android Kiosk** panel appears in the Home Assistant sidebar.

---

### 4. Install the Android APK

The APK is installed separately. It is not installed by HACS.

#### Option A: Install the APK from a file

1. Download the APK from the GitHub release.
2. Copy the APK to the tablet.
3. Open the APK on the tablet.
4. Allow installation from unknown sources.
5. Install the app.
6. Start the app.

#### Option B: Install the APK with ADB

```bash
adb install -r app-debug.apk
```

For updates:

```bash
adb install -r app-debug.apk
```

If Android does not apply permissions cleanly, a fresh installation may help:

```bash
adb uninstall com.hakiosk.android
adb install app-debug.apk
```

---

### 5. Start the app and sign in to Home Assistant

On first launch of the Android app:

1. Select the language: **Deutsch** or **English**.
2. Enter the Home Assistant address, for example:

```text
http://homeassistant.local:8123
```

or:

```text
http://192.168.1.240:8123
```

3. Open Home Assistant inside the app.
4. Sign in with a Home Assistant user.
5. Open the desired dashboard.
6. The device can then be managed from the **Android Kiosk** panel in Home Assistant.

The app registers itself with Home Assistant after sign-in. The tablet then appears in the Android Kiosk panel.

---

### 6. Select the tablet in the Android Kiosk panel

After the first app start, the Android app registers with Home Assistant.

In the **Android Kiosk** sidebar panel:

1. Select the device.
2. Check that the device ID is correct.
3. Optionally change the device name.
4. Save.

The device ID is important for automations. It is used as a placeholder in YAML examples.

Example placeholder:

```yaml
device: android_kiosk_1234567890abcdef
```

In real automations, this value must be replaced with the actual device ID.

---

### 7. Create dashboard pages for the tablet

A separate Home Assistant dashboard for the tablet is recommended, for example:

```text
/bathroom-display/weather
/bathroom-display/calendar
/bathroom-display/sensors
```

These pages can be added in **Android Kiosk → Screens**.

Each page can define:

- page name;
- URL or path;
- display duration in seconds;
- optional page size in percent.

Example:

```text
Name: Weather
URL: /bathroom-display/weather
Duration: 20
Size: 120
```

Then:

1. Click **Save**.
2. Click **Send rotation to device**.

The tablet will then rotate automatically through the configured pages.

---

### 8. Configure browser and kiosk mode

Browser and kiosk settings are configured in **Android Kiosk → Browser**.

Important options:

- start page;
- fullscreen mode;
- keep screen on;
- hide Home Assistant navigation;
- load background first;
- transparent WebView over the background;
- dashboard language German/English;
- self-sync interval;
- recovery/watchdog;
- default page size.

Recommended workflow:

1. Set the start page.
2. Enable kiosk mode.
3. Enable keep screen on.
4. Enable self-sync, for example `300` seconds.
5. Save.
6. Click **Send browser configuration**.

Self-sync allows the tablet to periodically pull its stored settings from Home Assistant. This is especially useful if the tablet has been powered off or offline for a long time.

---

### 9. Configure background slideshow

Background images and albums are managed in **Android Kiosk → Background**.

Workflow:

1. Create an album or select an existing album.
2. Upload images.
3. Choose the album as the active slideshow.
4. Configure interval, random order, opacity and display mode.
5. Save.
6. Click **Send slideshow to device**.

The Android app draws the background as a native layer behind the Home Assistant WebView. This keeps the background visible when the dashboard is loaded with transparency.

For older tablets, background images should not be unnecessarily large. Optimized images are recommended instead of very large original photos.

---

### 10. Purpose of “Automation & Tests”

The **Automation & Tests** area is not intended to be a permanent control dashboard.

It is meant for:

- quick tests of ticker, banner, alert, weather, camera, status overlay, TTS and sound;
- previewing how messages will look on the tablet;
- ready-to-copy YAML examples;
- diagnostic commands such as clearing cache or restarting the app.

Regular operation should be handled by Home Assistant automations. The web interface only helps with setup, tests and copying YAML templates.

---

### 11. How automations work

A Home Assistant automation usually contains three parts:

- **trigger**: when should something happen?
- **condition**: under which condition may it happen?
- **action**: what should be shown or played on the tablet?

Example structure:

```yaml
alias: Example - Show message on tablet
trigger:
  - platform: state
    entity_id: binary_sensor.example_sensor
    to: "on"
condition: []
action:
  - service: ha_android_kiosk.show_banner
    data:
      device: android_kiosk_1234567890abcdef
      title: "Info"
      message: "The sensor was triggered."
      position: top
      duration: 10
mode: single
```

Important: `device` must always match the device ID of the tablet.

---

### 12. Example: Change display page

This automation opens a specific dashboard page on the tablet.

```yaml
alias: Kiosk - Open weather page
trigger:
  - platform: time
    at: "07:00:00"
action:
  - service: ha_android_kiosk.open_url
    data:
      device: android_kiosk_1234567890abcdef
      url: /bathroom-display/weather
mode: single
```

---

### 13. Example: Send page rotation to the tablet

```yaml
alias: Kiosk - Set morning page rotation
trigger:
  - platform: time
    at: "06:30:00"
action:
  - service: ha_android_kiosk.set_pages
    data:
      device: android_kiosk_1234567890abcdef
      pages:
        - name: Weather
          url: /bathroom-display/weather
          duration: 20
          zoom_percent: 120
        - name: Calendar
          url: /bathroom-display/calendar
          duration: 20
          zoom_percent: 115
        - name: Sensors
          url: /bathroom-display/sensors
          duration: 15
      rotate: true
      pause_on_touch: true
      touch_pause_seconds: 30
mode: single
```

---

### 14. Example: Show a message as banner

```yaml
alias: Kiosk - Bathroom window open
trigger:
  - platform: state
    entity_id: binary_sensor.bathroom_window
    to: "on"
action:
  - service: ha_android_kiosk.show_banner
    data:
      device: android_kiosk_1234567890abcdef
      title: "Window open"
      message: "The bathroom window is open."
      position: top
      duration: 20
      color: "#f59e0b"
      text_color: "#ffffff"
      wake_screen: true
mode: single
```

---

### 15. Example: Play a TTS announcement

```yaml
alias: Kiosk - Washing machine finished
trigger:
  - platform: state
    entity_id: sensor.washing_machine_status
    to: "finished"
action:
  - service: ha_android_kiosk.tts_speak
    data:
      device: android_kiosk_1234567890abcdef
      text: "The washing machine is finished."
      language: en-US
      volume: 100
      stream: alarm
  - service: ha_android_kiosk.show_banner
    data:
      device: android_kiosk_1234567890abcdef
      title: "Washing machine"
      message: "The washing machine is finished."
      duration: 30
      position: bottom
mode: single
```

---

### 16. Example: Doorbell with camera overlay

```yaml
alias: Kiosk - Show doorbell camera
trigger:
  - platform: state
    entity_id: binary_sensor.doorbell
    to: "on"
action:
  - service: ha_android_kiosk.open_url
    data:
      device: android_kiosk_1234567890abcdef
      url: /bathroom-display/camera
  - service: ha_android_kiosk.show_camera
    data:
      device: android_kiosk_1234567890abcdef
      camera_entity: camera.front_door
      title: "Front door"
      position: fullscreen
      duration: 30
      refresh_seconds: 5
      wake_screen: true
  - service: ha_android_kiosk.tts_speak
    data:
      device: android_kiosk_1234567890abcdef
      text: "Someone rang the front doorbell."
      language: en-US
      volume: 100
      stream: alarm
mode: restart
```

---

### 17. Example: Status overlay from sensors

```yaml
alias: Kiosk - Show home status on motion
trigger:
  - platform: state
    entity_id: binary_sensor.bathroom_motion
    to: "on"
action:
  - service: ha_android_kiosk.show_status_overlay
    data:
      device: android_kiosk_1234567890abcdef
      title: "Home status"
      entities:
        - sensor.bathroom_temperature
        - sensor.bathroom_humidity
        - binary_sensor.bathroom_window
        - sensor.washing_machine_status
      position: center
      duration: 25
      color: "#111827"
      text_color: "#ffffff"
      wake_screen: true
mode: restart
```

---

### 18. Example: Show morning weather

```yaml
alias: Kiosk - Show morning weather
trigger:
  - platform: time
    at: "06:45:00"
action:
  - service: ha_android_kiosk.show_weather
    data:
      device: android_kiosk_1234567890abcdef
      weather_entity: weather.home
      title: "Weather today"
      position: top-left
      layout: full
      duration: 60
      refresh_seconds: 300
      show_forecast: true
      show_pressure: true
mode: single
```

---

### 19. Example: Morning sequence

A sequence can show multiple overlays one after another.

```yaml
alias: Kiosk - Morning information
trigger:
  - platform: time
    at: "07:15:00"
action:
  - service: ha_android_kiosk.show_overlay_sequence
    data:
      device: android_kiosk_1234567890abcdef
      steps:
        - type: banner
          title: "Good morning"
          message: "Here is the most important information."
          duration: 8
          position: top
        - type: weather
          weather_entity: weather.home
          title: "Weather"
          duration: 20
          position: top-left
        - type: status
          title: "Home status"
          duration: 20
          entities:
            - sensor.bathroom_temperature
            - binary_sensor.bathroom_window
            - sensor.washing_machine_status
mode: single
```

---

### 20. Example: Clear overlays

```yaml
alias: Kiosk - Clear display
trigger:
  - platform: time
    at: "23:00:00"
action:
  - service: ha_android_kiosk.clear_overlays
    data:
      device: android_kiosk_1234567890abcdef
mode: single
```

---

### 21. Important services

| Service | Function |
| --- | --- |
| `ha_android_kiosk.open_url` | Open a dashboard page |
| `ha_android_kiosk.set_pages` | Set page rotation |
| `ha_android_kiosk.set_browser_config` | Configure browser/kiosk/language/zoom |
| `ha_android_kiosk.set_background_slideshow` | Send background slideshow |
| `ha_android_kiosk.show_ticker` | Show ticker |
| `ha_android_kiosk.show_banner` | Show banner |
| `ha_android_kiosk.show_alert` | Show alert |
| `ha_android_kiosk.show_weather` | Show weather overlay |
| `ha_android_kiosk.show_camera` | Show camera overlay |
| `ha_android_kiosk.show_status_overlay` | Show sensors as status overlay |
| `ha_android_kiosk.show_overlay_sequence` | Show multiple overlays step by step |
| `ha_android_kiosk.tts_speak` | Play TTS announcement |
| `ha_android_kiosk.play_media` | Play audio/media |
| `ha_android_kiosk.clear_overlays` | Close overlays |
| `ha_android_kiosk.clear_webview_cache` | Clear WebView cache |
| `ha_android_kiosk.sync_settings` | Ask the tablet to pull stored settings |
| `ha_android_kiosk.recover_dashboard` | Repair display/kiosk mode |

---

### 22. More YAML examples

More ready-to-copy examples are available in the repository:

```text
docs/YAML_EXAMPLES_EN.md
docs/AUTOMATION_LAB_EN.md
examples/automation_lab_all_examples.yaml
examples/display_rotation.yaml
examples/ticker_from_sensors.yaml
examples/doorbell_camera.yaml
examples/status_overlay_sensors.yaml
examples/morning_sequence.yaml
examples/weather_overlay.yaml
examples/washing_machine_tts.yaml
examples/clear_overlays.yaml
```

Only the following values usually need to be changed in the examples:

- `device`
- dashboard paths such as `/bathroom-display/weather`
- sensors such as `sensor.bathroom_temperature`
- binary sensors such as `binary_sensor.bathroom_window`
- cameras such as `camera.front_door`
- weather entities such as `weather.home`

---

### 23. Troubleshooting

#### Tablet does not appear

- Start the app on the tablet.
- Check the Home Assistant URL.
- Check the Home Assistant sign-in.
- Check the network connection.
- Restart Home Assistant.

#### Dashboard stays blank

- Send browser configuration again.
- Send rotation again.
- Send slideshow again.
- Run the service `ha_android_kiosk.recover_dashboard`.
- Clear the WebView cache.

#### Background is not visible

- Send background slideshow again.
- Check if an album is active.
- Check if the album contains images.
- Enable transparent WebView.
- Enable background-first loading.

#### TTS is too quiet

- Check the Android device volume.
- Set `volume: 100` in the automation.
- Test `stream: alarm` or `stream: music`.
- Enable audio focus if available.

#### Language stays mixed

- Update through HACS.
- Restart Home Assistant.
- Hard-refresh the browser.
- Clear the Home Assistant browser cache.
- Select the admin language again.

---

## License

MIT License. See [LICENSE](LICENSE).
