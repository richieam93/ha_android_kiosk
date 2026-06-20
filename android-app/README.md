# HA Android Kiosk v17.16 / App 1.9.16

## New in v17.16 / Neu in v17.16

- First-start language prompt: **Deutsch** or **English**.
- Full Home Assistant admin panel language switch is supported by the integration (`de`/`en`).
- Dashboard language can be stored locally and changed from Home Assistant.
- Browser config accepts `dashboard_language`, `language` or `app_language`.
- The WebView sends an `Accept-Language` header and writes Home Assistant language keys into WebView storage.
- If the language changes, the dashboard reloads once so Home Assistant can apply the new language.
- Keeps the v17.12 memory-safe background loader for older Android-6/RK3288 tablets.
- Adds maintenance commands such as WebView cache clearing, overlay cleanup, identify device and immediate state report.
- Improved ticker, banner, alert, weather and camera overlays.
- Adds status overlays from Home Assistant entities and overlay sequences.
- Camera overlays can use custom size, position, duration and refresh interval.

## Build

```bash
./gradlew assembleDebug
```

Windows:

```bat
gradlew.bat assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Recommended settings for old tablets

- Keep `wait_for_background` enabled.
- Keep `transparent_webview` enabled.
- Use dashboard zoom around `110–120 %` first.
- Use reasonably sized background images.
- Use `alarm` audio stream if TTS is too quiet.
