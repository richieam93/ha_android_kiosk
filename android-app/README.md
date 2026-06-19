# HA Android Kiosk v17.13 / App 1.9.13

## New in v17.13 / Neu in v17.13

- First-start language prompt: **Deutsch** or **English**.
- Dashboard language can be stored locally and changed from Home Assistant.
- Browser config accepts `dashboard_language`, `language` or `app_language`.
- The WebView sends an `Accept-Language` header and writes Home Assistant language keys into WebView storage.
- If the language changes, the dashboard reloads once so Home Assistant can apply the new language.
- Keeps the v17.12 memory-safe background loader for older Android-6/RK3288 tablets.

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
