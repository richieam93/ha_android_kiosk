# HA Android Kiosk

**HA Android Kiosk** is a Home Assistant custom integration with a dedicated Android kiosk browser app for wall tablets, dashboards, old Android devices and always-on information screens.

**HA Android Kiosk** ist eine Home-Assistant-Custom-Integration mit eigener Android-Kiosk-Browser-App für Wandtablets, Dashboards, ältere Android-Geräte und dauerhaft laufende Info-Displays.

> Current version / Aktuelle Version: **Integration 1.9.13 / Android App 1.9.13**  
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

- **Overlays**  
  Ticker, Toasts, Banner, Vollbild-Alerts, Uhr, Wetter und Kamera-Overlay können auf dem Tablet angezeigt werden.

- **Geräte-Entities in Home Assistant**  
  Pro Tablet entstehen Home-Assistant-Entities wie Sensoren, Switches, Numbers und Media Player, damit du Automationen bauen kannst.

- **Berechtigungen und Gerätefunktionen**  
  Unterstützt werden unter anderem Display wach halten, Helligkeit, Lautstärke, Orientierung, Vibration, Frontkamera, Kamera-Bewegungserkennung und Lichtsensor, abhängig vom Gerät und den Android-Berechtigungen.

### HACS-Installation

Dieses Repository ist als **HACS Integration Repository** vorbereitet.

1. Lade den Inhalt dieses Projekts in dein GitHub-Repository hoch.
2. Erstelle optional einen GitHub Release, zum Beispiel `v1.9.13`.
3. Lade deine selbst gebaute APK optional als Release Asset hoch.
4. Öffne Home Assistant.
5. Öffne **HACS**.
6. Öffne **Custom repositories**.
7. Füge die GitHub-Repository-URL ein.
8. Wähle als Kategorie **Integration**.
9. Installiere **HA Android Kiosk**.
10. Starte Home Assistant neu.
11. Gehe zu **Einstellungen → Geräte & Dienste → Integration hinzufügen**.
12. Füge **HA Android Kiosk** hinzu.
13. Danach erscheint in der Sidebar das Panel **Android Kiosk**.

### Android-App installieren

Die APK wird nicht automatisch durch HACS installiert. HACS installiert nur die Home-Assistant-Integration unter `custom_components/ha_android_kiosk`.

Du kannst die APK selbst bauen:

```bash
cd android-app
./gradlew assembleDebug
```

Unter Windows:

```bat
cd android-app
gradlew.bat assembleDebug
```

Installieren per ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

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

### Repository-Struktur

```text
ha_android_kiosk/
├─ custom_components/ha_android_kiosk/   # Home-Assistant-/HACS-Integration
│  ├─ static/                            # Sidebar-Webinterface
│  ├─ translations/                      # HA-Config-Flow-Übersetzungen
│  ├─ __init__.py                        # API, Services, Panel, Geräteverwaltung
│  ├─ manifest.json                      # HA-Integration-Metadaten
│  └─ services.yaml                      # Service-Beschreibungen
├─ android-app/                          # Android-Kiosk-App
│  └─ app/src/main/java/com/hakiosk/android/
├─ hacs.json                             # HACS-Metadaten
├─ README.md
├─ LICENSE
└─ pyproject.toml
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

- **Overlays**  
  Ticker, toast, banner, fullscreen alert, clock, weather and camera overlays are supported.

- **Home Assistant entities per device**  
  Each tablet exposes entities such as sensors, switches, numbers and a media player for automations.

- **Device functions and permissions**  
  Depending on the device and Android permissions, the app supports keep-screen-on, brightness, volume, orientation, vibration, front camera, camera motion detection and light sensor.

### HACS installation

This repository is prepared as a **HACS Integration Repository**.

1. Upload this project to your GitHub repository.
2. Optionally create a GitHub release, for example `v1.9.13`.
3. Optionally upload your APK as a release asset.
4. Open Home Assistant.
5. Open **HACS**.
6. Open **Custom repositories**.
7. Paste your GitHub repository URL.
8. Choose **Integration** as the category.
9. Install **HA Android Kiosk**.
10. Restart Home Assistant.
11. Go to **Settings → Devices & services → Add integration**.
12. Add **HA Android Kiosk**.
13. The **Android Kiosk** panel appears in the sidebar.

### Install the Android app

HACS does not install the APK. HACS only installs the Home Assistant integration under `custom_components/ha_android_kiosk`.

Build the APK yourself:

```bash
cd android-app
./gradlew assembleDebug
```

On Windows:

```bat
cd android-app
gradlew.bat assembleDebug
```

Install through ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

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

### Repository structure

```text
ha_android_kiosk/
├─ custom_components/ha_android_kiosk/   # Home Assistant / HACS integration
│  ├─ static/                            # Sidebar web interface
│  ├─ translations/                      # HA config-flow translations
│  ├─ __init__.py                        # API, services, panel, device handling
│  ├─ manifest.json                      # HA integration metadata
│  └─ services.yaml                      # Service descriptions
├─ android-app/                          # Android kiosk app
│  └─ app/src/main/java/com/hakiosk/android/
├─ hacs.json                             # HACS metadata
├─ README.md
├─ LICENSE
└─ pyproject.toml
```

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
