# HA Android Kiosk

**HA Android Kiosk** is a Home Assistant custom integration with a dedicated Android kiosk browser app for wall tablets, dashboards, old Android devices and always-on information screens.

**HA Android Kiosk** ist eine Home-Assistant-Custom-Integration mit eigener Android-Kiosk-Browser-App für Wandtablets, Dashboards, ältere Android-Geräte und dauerhaft laufende Info-Displays.

> Current version / Aktuelle Version: **Integration 1.9.16 / Android App 1.9.16**  
> Optimized for older devices such as **Android 6 / RK3288 / WebView 96**, while still working on newer Android versions.

The Home Assistant integration is installed through **HACS as a custom integration repository**. The Android APK is built or uploaded separately, for example as a GitHub release asset.

Die Home-Assistant-Integration wird über **HACS als Custom Integration Repository** installiert. Die Android-APK wird separat gebaut oder von dir als GitHub-Release-Datei hochgeladen.

---

## Deutsch

### Was ist dieses Projekt?

Dieses Projekt verbindet Home Assistant mit einer eigenen Android-App. Die Android-App öffnet dein Home-Assistant-Dashboard in einer WebView und macht daraus einen stabilen Kiosk-Browser für Tablets. Die Integration in Home Assistant verwaltet Geräte, Seiten, Rotation, Hintergründe, Medien, Sprache, Berechtigungen und Kiosk-Einstellungen über ein eigenes Sidebar-Webinterface.

Das Ziel ist ein Wandtablet, das nach Neustart, Standby oder Netzwerkunterbruch selbständig wieder in den richtigen Zustand zurückfindet: Hintergrund laden, Dashboard anzeigen, Navigation ausblenden, Seiten rotieren, TTS abspielen und regelmäßig die gespeicherten Einstellungen aus Home Assistant abrufen.

### Hauptfunktionen

- **Kiosk-Browser für Home Assistant**  
  Zeigt Lovelace-/Dashboard-Seiten im Android-WebView an und kann direkt einzelne Home-Assistant-Pfade öffnen.

- **Kiosk-Modus ohne zusätzliche HACS-Erweiterung**  
  Die App kann Home-Assistant-Header, Sidebar, Menü und obere Navigation direkt im WebView ausblenden. Eine separate `kiosk-mode`-HACS-Erweiterung ist dafür nicht nötig.

- **Admin-Webinterface Deutsch/Englisch**  
  Das komplette Sidebar-Panel **Android Kiosk Admin** kann oben rechts zwischen Deutsch und Englisch umgeschaltet werden. Die Auswahl wird gespeichert und gilt für die gesamte Verwaltungsoberfläche.

- **Dashboard-Sprache Deutsch/Englisch**  
  Pro Gerät kann die Dashboard-Sprache auf `de` oder `en` gesetzt werden. Die Android-App fragt beim ersten Start nach **Deutsch** oder **English**. Die gewählte Sprache wird lokal gespeichert und kann später im Android-Kiosk-Panel oder per Service geändert werden.

- **Seitenrotation**  
  Mehrere Dashboard-Seiten können mit eigener Dauer automatisch rotiert werden. Touch-Pause verhindert, dass eine Seite sofort weiterwechselt, während jemand das Tablet bedient.

- **Manuelle Seitengröße / Zoom**  
  Du kannst eine globale Dashboard-Größe definieren, zum Beispiel `120 %`, und zusätzlich pro Seite eine eigene Größe setzen.

- **Native Hintergrund-Slideshow**  
  Hinter der WebView läuft eine native Android-Hintergrundebene. Dadurch bleibt der Hintergrund auch bei transparentem WebView und größeren Dashboard-Zoomwerten sichtbar.

- **Hintergrund zuerst laden**  
  Optional wird zuerst der Hintergrund geladen und erst danach das Dashboard eingeblendet. Dadurch wirkt der Start sauberer und weniger flackernd.

- **RAM-sicherer Hintergrund-Loader**  
  Der Hintergrund wird speicherschonend geladen. Das ist wichtig für ältere Geräte mit wenig Heap-Speicher, zum Beispiel Android-6-/RK3288-Tablets.

- **Selbst-Sync vom Tablet**  
  Das Tablet kann Browser-, Hintergrund-, Rotation- und Geräteeinstellungen selbständig von Home Assistant abrufen. Das hilft, wenn das Gerät längere Zeit ausgeschaltet oder offline war.

- **Lokaler Cache**  
  Die zuletzt bekannten Einstellungen werden in der Android-App zwischengespeichert. Wenn Home Assistant beim Start noch nicht erreichbar ist, kann das Tablet trotzdem mit den letzten Einstellungen starten.

- **Anzeige-Watchdog / Recovery**  
  Die App kann regelmäßig Kiosk-Modus, Hintergrund, Transparenz und Zoom erneut anwenden und bei Darstellungsproblemen das Dashboard wieder sichtbar machen.

- **Audio, TTS und Lautsprecher-Boost**  
  TTS-Durchsagen, Sounds und Medien können über Home Assistant an das Tablet gesendet werden. Die App kann Android-Audiokanäle wie `music` oder `alarm` verwenden, Audio-Fokus anfordern und Lautstärke auf Maximum setzen.

- **Erweiterte Overlays**  
  Ticker, Toasts, Banner, Vollbild-/Panel-Alerts, Uhr, Wetter und Kamera-Overlay können auf dem Tablet angezeigt werden. Ticker, Banner und Alerts unterstützen Farben, Textgrößen, Positionen, Dauer, Wake-Screen, TTS, Sound und Vibration. Das Wetter-Overlay kann Daten direkt aus einer `weather.*`-Entity lesen. Das Kamera-Overlay unterstützt Position, Größe, Dauer und Snapshot-/Proxy-Refresh.

- **Status-Overlay aus Sensoren**  
  Mit `show_status_overlay` kannst du eine Liste von Home-Assistant-Entities direkt als übersichtliches Display-Overlay anzeigen, zum Beispiel Temperaturen, Fenster, Türen, Waschmaschine, PV-Leistung oder Strompreis.

- **Overlay-Sequenzen**  
  Mit `show_overlay_sequence` kannst du mehrere Anzeigen nacheinander abspielen: Banner, Wetter, Status, Kamera und Alerts. Das ist praktisch für Morgenroutinen, Türklingel, Nachtmodus oder Info-Durchläufe.

- **Geräte-Entities in Home Assistant**  
  Pro Tablet entstehen Home-Assistant-Entities wie Sensoren, Switches, Numbers und Media Player, damit du Automationen bauen kannst.

- **Berechtigungen und Gerätefunktionen**  
  Unterstützt werden unter anderem Display wach halten, Helligkeit, Lautstärke, Orientierung, Vibration, Frontkamera, Kamera-Bewegungserkennung und Lichtsensor, abhängig vom Gerät und den Android-Berechtigungen.


### Webinterface ab Version 1.9.16: Einrichtung statt Dauer-Bedienung

Das Sidebar-Panel ist jetzt klarer getrennt:

- **Screens**: Dashboard-Seiten, Reihenfolge, Dauer und Seitengröße hinterlegen.
- **Browser**: Kiosk-Modus, Sprache, Zoom, Selbst-Sync und Ladeverhalten konfigurieren.
- **Hintergrund**: Alben, Uploads und Slideshow verwalten.
- **Automation & Tests**: Ticker, Banner, Alerts, Wetter, Kamera, Status-Overlay, TTS, Sound und Gerätebefehle nur kurz testen und danach eine passende YAML-Automation kopieren.
- **Berechtigungen**: festlegen, welche Funktionen Home Assistant an ein Gerät senden darf.
- **Bereinigen**: alte Testgeräte und Registry-Einträge entfernen.

Die früheren Tabs **Meldungen**, **Module**, **Gerät** und **Medien/TTS** sind keine eigenen Bedienseiten mehr. Diese Funktionen sind jetzt im Tab **Automation & Tests** gebündelt, weil sie im echten Betrieb über Automationen gesteuert werden sollen. Dadurch bleibt das Webinterface übersichtlicher und das Tablet läuft selbständig.

Weitere Details findest du in [`docs/AUTOMATION_LAB_DE.md`](docs/AUTOMATION_LAB_DE.md).


### Erste Einrichtung

1. Installiere die HACS-Integration und starte Home Assistant neu.
2. Installiere die Android-APK auf dem Tablet.
3. Öffne die App.
4. Wähle beim ersten Start **Deutsch** oder **English**.
5. Trage die Home-Assistant-URL ein, zum Beispiel `http://homeassistant.local:8123`.
6. Melde dich mit Home Assistant an oder verwende einen Long-Lived Access Token.
7. Öffne in Home Assistant das Sidebar-Panel **Android Kiosk**.
8. Wähle dein Gerät aus.
9. Konfiguriere Browser, Seiten, Rotation, Hintergrund, Medien und Sprache.
10. Sende einmal:
    - **Browser-Konfiguration senden**
    - **Slideshow ans Gerät senden**
    - **Rotation ans Gerät senden**
11. Danach kann das Tablet die gespeicherten Einstellungen selbständig abrufen.

### Admin-Webinterface umstellen

Oben rechts im Sidebar-Panel gibt es den Schalter **Admin-Sprache**.

- **Deutsch** zeigt die komplette Verwaltungsseite auf Deutsch.
- **English** zeigt die komplette Verwaltungsseite auf Englisch.

Diese Einstellung ist unabhängig von der Dashboard-Sprache des Tablets. So kannst du zum Beispiel die Home-Assistant-Verwaltung auf Englisch bedienen, während das Tablet-Dashboard selbst auf Deutsch bleibt.

### Dashboard-Sprache ändern

Im Panel **Android Kiosk → Browser** findest du **Dashboard-Sprache**.

Verfügbare Werte:

- `de` = Deutsch
- `en` = English

Nach dem Ändern kannst du **Sprache anwenden** oder **Browser-Konfiguration senden** klicken. Die Android-App setzt dann die Sprache im WebView, schreibt passende Sprachwerte in den WebView-Speicher, setzt den `Accept-Language`-Header und lädt Home Assistant bei Bedarf einmal neu.

Service-Beispiel:

```yaml
service: ha_android_kiosk.set_dashboard_language
data:
  device: bad_tablet
  dashboard_language: en
```

Alternativ über die Browser-Konfiguration:

```yaml
service: ha_android_kiosk.set_browser_config
data:
  device: bad_tablet
  dashboard_language: de
```

### Wichtige Services

| Service | Zweck |
| --- | --- |
| `ha_android_kiosk.set_browser_config` | Browser, Kiosk-Modus, Zoom, Sprache, Reload und WebView-Verhalten setzen |
| `ha_android_kiosk.set_dashboard_language` | Dashboard-Sprache Deutsch/Englisch setzen |
| `ha_android_kiosk.open_url` | Sofort eine Dashboard-Seite öffnen |
| `ha_android_kiosk.set_background_slideshow` | Hintergrund-Slideshow ans Gerät senden |
| `ha_android_kiosk.set_background_album` | Ein gespeichertes Hintergrund-Album verwenden |
| `ha_android_kiosk.set_pages` / `send_command` | Seitenrotation konfigurieren |
| `ha_android_kiosk.tts_speak` | Sprachdurchsage ausgeben |
| `ha_android_kiosk.play_media` | Medien oder Audio abspielen |
| `ha_android_kiosk.set_audio_config` | TTS-/Audio-Lautstärke und Android-Audiokanal setzen |
| `ha_android_kiosk.recover_dashboard` | Anzeige/Kiosk-Modus manuell reparieren |
| `ha_android_kiosk.sync_settings` | Tablet zum erneuten Abrufen der gespeicherten Einstellungen auffordern |
| `ha_android_kiosk.clear_webview_cache` | WebView-Cache, Verlauf, Cookies und WebStorage leeren und Seite neu laden |
| `ha_android_kiosk.show_status_overlay` | Status-Overlay aus Sensoren/Entities erzeugen |
| `ha_android_kiosk.show_overlay_sequence` | Mehrere Overlay-Schritte nacheinander anzeigen |
| `ha_android_kiosk.clear_overlays` | Ticker, Banner, Alert, Wetter- und Kamera-Overlays ausblenden |
| `ha_android_kiosk.identify_device` | Gerät kurz sichtbar markieren |
| `ha_android_kiosk.report_device_state` | Sofort neuen Gerätestatus anfordern |

### YAML-Beispiele kopieren

Im Repository gibt es jetzt fertige Beispiele im Ordner [`examples/`](examples/) und zusätzlich eine deutsche Anleitung unter [`docs/YAML_EXAMPLES_DE.md`](docs/YAML_EXAMPLES_DE.md).

Enthalten sind unter anderem:

- `automation_doorbell_camera.yaml` – Seite wechseln, Banner anzeigen und Kamera-Overlay öffnen.
- `automation_status_overlay.yaml` – Sensorwerte als Status-Overlay anzeigen.
- `automation_morning_sequence.yaml` – Morgenanzeige mit Banner, Wetter und Status.
- `automation_washing_machine_tts.yaml` – Meldung mit TTS-Durchsage.
- `package_ha_android_kiosk_examples.yaml` – Beispiel-Package zum Kopieren nach `/config/packages/`.

Kurzes Beispiel:

```yaml
service: ha_android_kiosk.show_status_overlay
data:
  device: bad_tablet
  title: "Hausstatus"
  entities:
    - sensor.wohnzimmer_temperatur
    - binary_sensor.fenster_bad
    - sensor.waschmaschine_status
  position: center
  duration: 20
  color: "#111827"
  text_color: "#ffffff"
  wake_screen: true
```


### Hinweise für ältere Tablets

Für Android 6 / RK3288 / WebView 96 sind folgende Einstellungen empfohlen:

- **Home-Assistant-Navigation ausblenden** aktiv
- **Hintergrund zuerst laden** aktiv
- **WebView transparent** aktiv
- **Selbst-Sync** etwa `300` Sekunden
- **Recovery/Watchdog** etwa `45` Sekunden
- Seiten-Zoom zuerst mit `110–120 %` testen
- Hintergrundbilder möglichst nicht unnötig riesig hochladen
- Für laute TTS-Durchsagen zuerst den Android-Audiokanal `alarm` testen

---

## English

### What is this project?

This project connects Home Assistant with a dedicated Android kiosk app. The Android app opens your Home Assistant dashboard inside a WebView and turns the tablet into a stable kiosk browser. The Home Assistant integration manages devices, pages, rotation, backgrounds, media, language, permissions and kiosk settings through its own sidebar panel.

The goal is a wall tablet that can recover by itself after a reboot, standby period or network interruption: load the background, show the dashboard, hide navigation, rotate pages, play TTS and regularly pull stored settings from Home Assistant.

### Main features

- **Home Assistant kiosk browser**  
  Displays Lovelace/dashboard pages inside the Android WebView and can open individual Home Assistant paths directly.

- **Kiosk mode without an extra HACS frontend plugin**  
  The app can hide the Home Assistant header, sidebar, menu and top navigation directly inside the WebView. A separate `kiosk-mode` HACS plugin is not required.

- **Admin web interface in German/English**  
  The entire **Android Kiosk Admin** sidebar panel can be switched between German and English in the top-right corner. The selection is saved and applies to the whole management interface.

- **Dashboard language German/English**  
  Each device can use `de` or `en` as the dashboard language. The Android app asks for **Deutsch** or **English** on first launch. The language is stored locally and can later be changed from the Android Kiosk panel or via service call.

- **Page rotation**  
  Rotate multiple dashboard pages with custom durations. Touch pause prevents the tablet from changing pages while someone is interacting with it.

- **Manual page zoom**  
  Configure a global dashboard size, for example `120 %`, and optionally set a different zoom value for each page.

- **Native background slideshow**  
  The background is drawn by a native Android layer behind the WebView. This keeps the background visible even with a transparent WebView and larger dashboard zoom values.

- **Background-first loading**  
  The app can load the native background first and reveal the dashboard afterwards for a cleaner startup.

- **Memory-safe background loader**  
  Background images are loaded in a memory-conscious way, which is important for older Android tablets with limited Java heap.

- **Self-sync from the tablet**  
  The tablet can periodically pull browser, background, rotation and device settings from Home Assistant by itself.

- **Local cache**  
  The last known settings are cached in the Android app. If Home Assistant is not reachable immediately at startup, the tablet can still start with the previous configuration.

- **Visual watchdog / recovery**  
  The app can regularly reapply kiosk mode, background, transparency and zoom and can recover the dashboard if Home Assistant redraws parts of the layout.

- **Audio, TTS and speaker boost**  
  TTS announcements, sounds and media can be sent from Home Assistant to the tablet. The app can use Android streams such as `music` or `alarm`, request audio focus and set volume to maximum.

- **Advanced overlays**  
  Ticker, toast, banner, fullscreen/panel alert, clock, weather and camera overlays are supported. Ticker, banner and alerts support colors, text sizes, positions, duration, wake-screen, TTS, sound and vibration. The weather overlay can read data directly from a `weather.*` entity. The camera overlay supports position, size, duration and snapshot/proxy refresh.

- **Status overlay from sensors**  
  `show_status_overlay` can render a list of Home Assistant entities as a clear display overlay, for example temperatures, windows, doors, washing machine, PV power or energy price.

- **Overlay sequences**  
  `show_overlay_sequence` can show multiple steps one after another: banner, weather, status, camera and alerts. This is useful for morning routines, doorbell flows, night mode or information loops.

- **Home Assistant entities per device**  
  Each tablet exposes entities such as sensors, switches, numbers and a media player for automations.

- **Device functions and permissions**  
  Depending on the device and Android permissions, the app supports keep-screen-on, brightness, volume, orientation, vibration, front camera, camera motion detection and light sensor.


### Web interface since version 1.9.16: setup instead of permanent manual control

The sidebar panel is now structured more clearly:

- **Screens**: configure dashboard pages, order, duration and per-page size.
- **Browser**: configure kiosk mode, language, zoom, self-sync and loading behavior.
- **Background**: manage albums, uploads and slideshow.
- **Automation & Tests**: briefly test ticker, banner, alerts, weather, camera, status overlay, TTS, sound and device commands, then copy a matching YAML automation.
- **Permissions**: define which functions Home Assistant may send to a device.
- **Cleanup**: remove old test devices and registry entries.

The old tabs **Messages**, **Modules**, **Device** and **Media/TTS** are no longer separate control pages. These functions are now grouped in **Automation & Tests**, because real operation should be controlled by automations. This keeps the web interface cleaner and lets the tablet run on its own.

More details are available in [`docs/AUTOMATION_LAB_EN.md`](docs/AUTOMATION_LAB_EN.md).



### First setup

1. Install the HACS integration and restart Home Assistant.
2. Install the Android APK on the tablet.
3. Open the app.
4. Select **Deutsch** or **English** on first launch.
5. Enter your Home Assistant URL, for example `http://homeassistant.local:8123`.
6. Sign in with Home Assistant or use a long-lived access token.
7. Open the **Android Kiosk** sidebar panel in Home Assistant.
8. Select your device.
9. Configure browser, pages, rotation, background, media and language.
10. Send once:
    - **Send browser configuration**
    - **Send slideshow to device**
    - **Send rotation to device**
11. After that, the tablet can pull the stored settings by itself.

### Change admin web interface language

The sidebar panel has an **Admin language** selector in the top-right corner.

- **German** shows the full management page in German.
- **English** shows the full management page in English.

This is independent from the tablet dashboard language. For example, you can manage Home Assistant in English while the tablet dashboard itself stays German.

### Change dashboard language

In **Android Kiosk → Browser**, use **Dashboard language**.

Available values:

- `de` = German
- `en` = English

After changing the value, click **Apply language** or **Send browser configuration**. The Android app sets the language inside the WebView, writes language values to WebView storage, sets the `Accept-Language` header and reloads Home Assistant once if needed.

Service example:

```yaml
service: ha_android_kiosk.set_dashboard_language
data:
  device: bathroom_tablet
  dashboard_language: en
```

Alternatively through the browser configuration:

```yaml
service: ha_android_kiosk.set_browser_config
data:
  device: bathroom_tablet
  dashboard_language: de
```

### Important services

| Service | Purpose |
| --- | --- |
| `ha_android_kiosk.set_browser_config` | Set browser, kiosk mode, zoom, language, reload and WebView behavior |
| `ha_android_kiosk.set_dashboard_language` | Set dashboard language to German or English |
| `ha_android_kiosk.open_url` | Open a dashboard page immediately |
| `ha_android_kiosk.set_background_slideshow` | Send background slideshow configuration to the device |
| `ha_android_kiosk.set_background_album` | Use a stored background album |
| `ha_android_kiosk.set_pages` / `send_command` | Configure page rotation |
| `ha_android_kiosk.tts_speak` | Play a text-to-speech announcement |
| `ha_android_kiosk.play_media` | Play media or audio |
| `ha_android_kiosk.set_audio_config` | Set TTS/media volume and Android audio stream |
| `ha_android_kiosk.recover_dashboard` | Manually repair the dashboard display/kiosk mode |
| `ha_android_kiosk.sync_settings` | Ask the tablet to pull stored settings again |
| `ha_android_kiosk.clear_webview_cache` | Clear WebView cache, history, cookies and WebStorage, then reload the page |
| `ha_android_kiosk.clear_overlays` | Hide ticker, banner, alert, weather and camera overlays |
| `ha_android_kiosk.identify_device` | Visibly identify the device for a short time |
| `ha_android_kiosk.report_device_state` | Request a fresh device status immediately |




### Tips for older tablets

Recommended settings for Android 6 / RK3288 / WebView 96:

- enable **Hide Home Assistant navigation**
- enable **Load background first**
- enable **Transparent WebView**
- keep **Self-sync** around `300` seconds
- keep **Recovery/Watchdog** around `45` seconds
- start page zoom with `110–120 %`
- avoid unnecessarily huge background images
- test Android audio stream `alarm` first for louder TTS announcements

### License

MIT License. See [LICENSE](LICENSE).


### YAML examples

Ready-to-copy YAML examples are available in [`examples/`](examples/) and in [`docs/YAML_EXAMPLES_EN.md`](docs/YAML_EXAMPLES_EN.md). They cover doorbell camera flows, sensor status overlays, morning sequences and TTS messages.

```yaml
service: ha_android_kiosk.show_status_overlay
data:
  device: bad_tablet
  title: "Home status"
  entities:
    - sensor.living_room_temperature
    - binary_sensor.bathroom_window
    - sensor.washing_machine_status
  position: center
  duration: 20
  color: "#111827"
  text_color: "#ffffff"
  wake_screen: true
```

---

## Overlay and YAML examples / Overlay- und YAML-Beispiele

### Deutsch

Ab Version **1.9.16** sind die Anzeige-Overlays stärker ausgebaut:

- **Ticker**: Position oben/unten, Höhe, Textgröße, Farben, feste Meldungen, Live-Meldungen und Sensorwerte.
- **Banner**: Position oben/unten/mittig, Höhe, Textgröße, Farben, Dauer, Wake-Screen, TTS und Sound.
- **Alert**: Vollbild, Panel, Banner oder Toast; Severity-Farben, TTS, Sound, Vibration und Dauer.
- **Wetter-Overlay**: liest eine `weather.*`-Entity aus Home Assistant und kann Temperatur, Feuchte, Wind, Druck und Forecast anzeigen.
- **Kamera-Overlay**: zeigt `camera.*`-Entities oder direkte URLs, mit Position, Größe, Dauer und Refresh-Intervall.
- **Status-Overlay**: baut automatisch eine Anzeige aus Sensoren und Binary-Sensoren.
- **Overlay-Sequenz**: mehrere Schritte nacheinander, zum Beispiel Seite wechseln, Banner anzeigen, Wetter anzeigen, Kamera anzeigen und danach Status anzeigen.

Kopierfertige YAML-Beispiele findest du hier:

- [`docs/YAML_EXAMPLES_DE.md`](docs/YAML_EXAMPLES_DE.md)
- [`docs/AUTOMATION_LAB_DE.md`](docs/AUTOMATION_LAB_DE.md)
- [`examples/automation_lab_all_examples.yaml`](examples/automation_lab_all_examples.yaml)
- [`examples/display_rotation.yaml`](examples/display_rotation.yaml)
- [`examples/ticker_from_sensors.yaml`](examples/ticker_from_sensors.yaml)
- [`examples/doorbell_camera.yaml`](examples/doorbell_camera.yaml)
- [`examples/status_overlay_sensors.yaml`](examples/status_overlay_sensors.yaml)
- [`examples/morning_sequence.yaml`](examples/morning_sequence.yaml)
- [`examples/weather_overlay.yaml`](examples/weather_overlay.yaml)
- [`examples/washing_machine_tts.yaml`](examples/washing_machine_tts.yaml)

Die wichtigsten neuen Services:

| Service | Zweck |
| --- | --- |
| `ha_android_kiosk.show_sensor_overlay` | Sensoren automatisch als Ticker, Banner, Toast, Weather oder Alert anzeigen |
| `ha_android_kiosk.show_status_overlay` | mehrere Sensoren als Status-Panel anzeigen |
| `ha_android_kiosk.show_overlay_sequence` | mehrere Overlays nacheinander abspielen |
| `ha_android_kiosk.clear_banner` | nur Banner schließen |
| `ha_android_kiosk.clear_weather` | nur Wetter-Overlay schließen |
| `ha_android_kiosk.clear_camera` | nur Kamera-Overlay schließen |
| `ha_android_kiosk.clear_overlays` | alle Overlays schließen |

### English

Since version **1.9.16**, the display overlays are more powerful:

- **Ticker**: top/bottom position, height, text size, colors, fixed messages, live messages and sensor values.
- **Banner**: top/bottom/center position, height, text size, colors, duration, wake screen, TTS and sound.
- **Alert**: fullscreen, panel, banner or toast; severity colors, TTS, sound, vibration and duration.
- **Weather overlay**: reads a Home Assistant `weather.*` entity and can show temperature, humidity, wind, pressure and forecast.
- **Camera overlay**: shows `camera.*` entities or direct URLs with position, size, duration and refresh interval.
- **Status overlay**: automatically builds a display card from sensors and binary sensors.
- **Overlay sequence**: plays multiple steps, for example open page, show banner, show weather, show camera and then show status.

Ready-to-copy YAML examples are available here:

- [`docs/YAML_EXAMPLES_EN.md`](docs/YAML_EXAMPLES_EN.md)
- [`docs/AUTOMATION_LAB_EN.md`](docs/AUTOMATION_LAB_EN.md)
- [`examples/automation_lab_all_examples.yaml`](examples/automation_lab_all_examples.yaml)
- [`examples/display_rotation.yaml`](examples/display_rotation.yaml)
- [`examples/ticker_from_sensors.yaml`](examples/ticker_from_sensors.yaml)
- [`examples/doorbell_camera.yaml`](examples/doorbell_camera.yaml)
- [`examples/status_overlay_sensors.yaml`](examples/status_overlay_sensors.yaml)
- [`examples/morning_sequence.yaml`](examples/morning_sequence.yaml)
- [`examples/weather_overlay.yaml`](examples/weather_overlay.yaml)
- [`examples/washing_machine_tts.yaml`](examples/washing_machine_tts.yaml)

Important services:

| Service | Purpose |
| --- | --- |
| `ha_android_kiosk.show_sensor_overlay` | automatically show sensors as ticker, banner, toast, weather or alert |
| `ha_android_kiosk.show_status_overlay` | show multiple sensors as a status panel |
| `ha_android_kiosk.show_overlay_sequence` | play multiple overlays step by step |
| `ha_android_kiosk.clear_banner` | close only the banner |
| `ha_android_kiosk.clear_weather` | close only the weather overlay |
| `ha_android_kiosk.clear_camera` | close only the camera overlay |
| `ha_android_kiosk.clear_overlays` | close all overlays |
