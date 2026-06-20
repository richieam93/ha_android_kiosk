# YAML examples for HA Android Kiosk

Copy these examples into Home Assistant and replace `device:` and the entity IDs with your own sensors, binary sensors, cameras and weather entities.

## 1. Rotate dashboard pages

```yaml
service: ha_android_kiosk.set_pages
data:
  device: bathroom_tablet
  auto_rotate: true
  pause_on_touch: true
  touch_pause: 120
  duration: 20
  pages:
    - name: Weather
      url: /lovelace/weather
      duration: 25
      zoom_percent: 120
    - name: Energy
      url: /lovelace/energy
      duration: 20
      zoom_percent: 115
    - name: Cameras
      url: /lovelace/cameras
      duration: 15
      zoom_percent: 110
```

## 2. Ticker from sensors

```yaml
alias: Kiosk - update ticker from sensors
mode: single
trigger:
  - platform: time_pattern
    minutes: "/5"
action:
  - service: ha_android_kiosk.show_sensor_overlay
    data:
      device: bathroom_tablet
      overlay: ticker
      entities:
        - entity_id: sensor.living_room_temperature
          label: Living room
          icon: "🌡️"
        - entity_id: sensor.energy_price
          label: Energy price
          icon: "⚡"
        - entity_id: sensor.pv_power
          label: PV
          icon: "☀️"
      separator: "   •   "
      position: bottom
      color: "#111827"
      text_color: "#ffffff"
      duration: 0
```

## 3. Washing machine done as banner and TTS

```yaml
alias: Kiosk - washing machine done
mode: single
trigger:
  - platform: state
    entity_id: sensor.washing_machine_status
    to: "done"
action:
  - service: ha_android_kiosk.show_banner
    data:
      device: bathroom_tablet
      title: "Washing machine"
      message: "Laundry is done"
      position: top
      color: "#15803d"
      text_color: "#ffffff"
      text_size: 20
      duration: 15
      wake_screen: true
      tts_message: "The washing machine is done."
      tts_language: en-US
      volume: 100
```

## 4. Doorbell with camera overlay

```yaml
alias: Kiosk - doorbell camera
mode: restart
trigger:
  - platform: state
    entity_id: binary_sensor.doorbell
    to: "on"
action:
  - service: ha_android_kiosk.goto_screen
    data:
      device: bathroom_tablet
      screen_id: /lovelace/cameras
  - service: ha_android_kiosk.show_alert
    data:
      device: bathroom_tablet
      title: "Doorbell"
      message: "Someone is at the door"
      severity: warning
      mode: banner
      duration: 8
      wake_screen: true
      sound: doorbell
      volume: 100
  - service: ha_android_kiosk.show_camera
    data:
      device: bathroom_tablet
      entity_id: camera.front_door
      position: bottom-right
      width: 420
      height: 260
      duration: 30
      refresh_seconds: 5
```

## 5. Window open fullscreen/panel alert

```yaml
alias: Kiosk - window open warning
mode: restart
trigger:
  - platform: state
    entity_id: binary_sensor.bathroom_window
    to: "on"
    for: "00:05:00"
action:
  - service: ha_android_kiosk.show_alert
    data:
      device: bathroom_tablet
      title: "Window open"
      message: "The bathroom window has been open for 5 minutes."
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

## 6. Weather overlay

```yaml
alias: Kiosk - show weather on motion
mode: restart
trigger:
  - platform: state
    entity_id: binary_sensor.hallway_motion
    to: "on"
action:
  - service: ha_android_kiosk.show_weather
    data:
      device: bathroom_tablet
      entity_id: weather.home
      title: "Weather"
      layout: full
      position: top-left
      duration: 45
      refresh_seconds: 300
      show_forecast: true
      show_pressure: true
      wake_screen: true
```

## 7. Status overlay from multiple sensors

```yaml
alias: Kiosk - show status on motion
mode: restart
trigger:
  - platform: state
    entity_id: binary_sensor.hallway_motion
    to: "on"
action:
  - service: ha_android_kiosk.show_status_overlay
    data:
      device: bathroom_tablet
      title: "Home status"
      entities:
        - sensor.living_room_temperature
        - sensor.bathroom_temperature
        - binary_sensor.bathroom_window
        - sensor.washing_machine_status
      position: center
      duration: 20
      color: "#111827"
      text_color: "#ffffff"
      wake_screen: true
```

## 8. Morning sequence: switch page, banner, weather, status

```yaml
alias: Kiosk - morning display
mode: single
trigger:
  - platform: time
    at: "07:00:00"
action:
  - service: ha_android_kiosk.goto_screen
    data:
      device: bathroom_tablet
      screen_id: /lovelace/start
  - service: ha_android_kiosk.show_overlay_sequence
    data:
      device: bathroom_tablet
      default_duration: 8
      gap: 1
      steps:
        - type: banner
          title: "Good morning"
          message: "Here is your daily overview"
          color: "#2563eb"
          duration: 5
        - type: weather
          entity_id: weather.home
          title: "Weather"
          position: top-left
          layout: full
          show_forecast: true
          duration: 10
        - type: status_overlay
          title: "Important values"
          entities:
            - sensor.outdoor_temperature
            - sensor.energy_price
            - sensor.pv_power
          position: center
          duration: 12
```
