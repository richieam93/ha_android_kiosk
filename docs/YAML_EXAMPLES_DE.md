# YAML-Beispiele fuer HA Android Kiosk

Diese Beispiele kannst du direkt in Home Assistant kopieren. Ersetze nur `device:` und die Entity-IDs durch deine eigenen Sensoren, Binary-Sensoren, Kameras und Weather-Entities.

## 1. Display-Seiten automatisch rotieren

```yaml
service: ha_android_kiosk.set_pages
data:
  device: bad_tablet
  auto_rotate: true
  pause_on_touch: true
  touch_pause: 120
  duration: 20
  pages:
    - name: Wetter
      url: /lovelace/wetter
      duration: 25
      zoom_percent: 120
    - name: Energie
      url: /lovelace/energie
      duration: 20
      zoom_percent: 115
    - name: Kameras
      url: /lovelace/kameras
      duration: 15
      zoom_percent: 110
```

## 2. Ticker aus Sensoren erzeugen

```yaml
alias: Kiosk - Ticker mit Hauswerten aktualisieren
mode: single
trigger:
  - platform: time_pattern
    minutes: "/5"
action:
  - service: ha_android_kiosk.show_sensor_overlay
    data:
      device: bad_tablet
      overlay: ticker
      entities:
        - entity_id: sensor.wohnzimmer_temperatur
          label: Wohnzimmer
          icon: "🌡️"
        - entity_id: sensor.strompreis
          label: Strompreis
          icon: "⚡"
        - entity_id: sensor.pv_leistung
          label: PV
          icon: "☀️"
      separator: "   •   "
      position: bottom
      color: "#111827"
      text_color: "#ffffff"
      duration: 0
```

## 3. Waschmaschine fertig als Banner und TTS

```yaml
alias: Kiosk - Waschmaschine fertig
mode: single
trigger:
  - platform: state
    entity_id: sensor.waschmaschine_status
    to: "fertig"
action:
  - service: ha_android_kiosk.show_banner
    data:
      device: bad_tablet
      title: "Waschmaschine"
      message: "Die Waesche ist fertig"
      position: top
      color: "#15803d"
      text_color: "#ffffff"
      text_size: 20
      duration: 15
      wake_screen: true
      tts_message: "Die Waschmaschine ist fertig."
      tts_language: de-DE
      volume: 100
```

## 4. Tuerklingel mit Kamera-Overlay

```yaml
alias: Kiosk - Tuerklingel mit Kamera
mode: restart
trigger:
  - platform: state
    entity_id: binary_sensor.haustuer_klingel
    to: "on"
action:
  - service: ha_android_kiosk.goto_screen
    data:
      device: bad_tablet
      screen_id: /lovelace/kameras
  - service: ha_android_kiosk.show_alert
    data:
      device: bad_tablet
      title: "Tuerklingel"
      message: "Jemand steht vor der Haustuer"
      severity: warning
      mode: banner
      duration: 8
      wake_screen: true
      sound: doorbell
      volume: 100
  - service: ha_android_kiosk.show_camera
    data:
      device: bad_tablet
      entity_id: camera.haustuer
      position: bottom-right
      width: 420
      height: 260
      duration: 30
      refresh_seconds: 5
```

## 5. Fenster offen als Vollbild-Alert

```yaml
alias: Kiosk - Fenster offen Warnung
mode: restart
trigger:
  - platform: state
    entity_id: binary_sensor.bad_fenster
    to: "on"
    for: "00:05:00"
action:
  - service: ha_android_kiosk.show_alert
    data:
      device: bad_tablet
      title: "Fenster offen"
      message: "Das Badfenster ist seit 5 Minuten offen."
      severity: warning
      mode: panel
      position: center
      duration: 30
      color: "#b45309"
      text_color: "#ffffff"
      text_size: 26
      vibrate: true
      wake_screen: true
```

## 6. Wetter-Overlay anzeigen

```yaml
alias: Kiosk - Wetter bei Bewegung anzeigen
mode: restart
trigger:
  - platform: state
    entity_id: binary_sensor.flur_bewegung
    to: "on"
action:
  - service: ha_android_kiosk.show_weather
    data:
      device: bad_tablet
      entity_id: weather.home
      title: "Wetter"
      layout: full
      position: top-left
      duration: 45
      refresh_seconds: 300
      show_forecast: true
      show_pressure: true
      wake_screen: true
```

## 7. Status-Overlay aus mehreren Sensoren

```yaml
alias: Kiosk - Status bei Bewegung anzeigen
mode: restart
trigger:
  - platform: state
    entity_id: binary_sensor.flur_bewegung
    to: "on"
action:
  - service: ha_android_kiosk.show_status_overlay
    data:
      device: bad_tablet
      title: "Hausstatus"
      entities:
        - sensor.wohnzimmer_temperatur
        - sensor.bad_temperatur
        - binary_sensor.fenster_bad
        - sensor.waschmaschine_status
      position: center
      duration: 20
      color: "#111827"
      text_color: "#ffffff"
      wake_screen: true
```

## 8. Morgen-Sequenz: Seite wechseln, Banner, Wetter, Sensorstatus

```yaml
alias: Kiosk - Morgenanzeige
mode: single
trigger:
  - platform: time
    at: "07:00:00"
action:
  - service: ha_android_kiosk.goto_screen
    data:
      device: bad_tablet
      screen_id: /lovelace/start
  - service: ha_android_kiosk.show_overlay_sequence
    data:
      device: bad_tablet
      default_duration: 8
      gap: 1
      steps:
        - type: banner
          title: "Guten Morgen"
          message: "Hier ist dein Tagesstart"
          color: "#2563eb"
          duration: 5
        - type: weather
          entity_id: weather.home
          title: "Wetter"
          position: top-left
          layout: full
          show_forecast: true
          duration: 10
        - type: status_overlay
          title: "Wichtige Werte"
          entities:
            - sensor.aussentemperatur
            - sensor.strompreis
            - sensor.pv_leistung
          position: center
          duration: 12
```

## 9. Kamera wieder schliessen

```yaml
service: ha_android_kiosk.clear_camera
data:
  device: bad_tablet
```

## 10. Alle Overlays schliessen

```yaml
service: ha_android_kiosk.clear_overlays
data:
  device: bad_tablet
```
