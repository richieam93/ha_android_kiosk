# HA Android Kiosk v17.12 / App 1.9.12

## Neu in v17.12

Diese Version repariert den Absturz aus v17.11 auf Android-6/RK3288-Tablets und behält Selbst-Sync, lokalen Cache, Seiten-Zoom und Audio-/TTS-Boost bei.

- RAM-sicherer Hintergrund-Loader: Bilder werden nicht mehr komplett in ein ByteArray geladen.
- Maximal ein Hintergrund-Decode gleichzeitig, damit Standby/Watchdog keine parallelen Loader startet.
- OutOfMemoryError wird abgefangen; bei einem zu grossen Bild bleibt die App offen und zeigt das Dashboard weiter.
- Hintergrundbilder werden nur noch nativ hinter der WebView gezeichnet, nicht doppelt als CSS-Background in Home Assistant.
- Transparente WebView nutzt keinen Software-Layer mehr, um den Java-Heap zu schonen.
- Seiten-Zoom nutzt CSS-Zoom statt grossem Transform-Layer und ist dadurch speicherschonender.
- Die bestehenden v17.11-Funktionen bleiben erhalten: Selbst-Sync, lokaler Notfall-Cache, Anzeige-Watchdog, Audio-/TTS-Boost.

## v17.3 Kiosk-Ladeverhalten

Die App erstellt beim Start zuerst eine native Android-Hintergrundebene. Wenn Home Assistant eine Hintergrundkonfiguration liefert, wird das erste Bild geladen und die WebView erst danach eingeblendet. Bei Netzwerkfehlern gibt es einen Timeout, damit der Browser trotzdem sichtbar wird.

Zusätzliche Browser-Schalter aus der Integration:

- `wait_for_background`
- `transparent_webview`
- `force_mobile_viewport`
- `reveal_ms`
