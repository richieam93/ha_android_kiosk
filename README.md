# HA Android Kiosk

**HA Android Kiosk** ist ein Home-Assistant-Custom-Integration-Projekt mit eigener Android-Kiosk-App für Tablets, Wanddisplays und alte Android-Geräte.

Die Integration wird über **HACS als Custom Repository** installiert. Die APK wird separat gebaut oder von dir als GitHub-Release-Datei hochgeladen.

> Aktuelle Basis: **Integration 1.9.12 / Android-App 1.9.12**  
> Stabilisiert für ältere Tablets wie Android 6 / RK3288 / WebView 96.


## Hauptfunktionen

- Home-Assistant-Dashboard im Android-WebView anzeigen
- Kiosk-Modus ohne zusätzliche HACS-Erweiterung `kiosk-mode`
- Header, Sidebar und obere Navigation in Home Assistant ausblenden
- Seitenrotation mit konfigurierbarer Dauer
- Hintergrund/Slideshow nativ hinter der WebView
- manueller Seiten-Zoom pro Dashboard-Seite
- Selbst-Sync: Tablet kann Einstellungen selbständig neu abrufen
- lokaler Cache für Start nach längerer Offline-/Standby-Zeit
- Watchdog zur Reparatur von Anzeige, Rotation und Hintergrund
- Audio-/TTS-Steuerung mit Android-Audiokanal-Auswahl
- Home-Assistant-Entities pro Gerät: Sensoren, Switches, Numbers, Media Player
- Webinterface in der Home-Assistant-Sidebar

## HACS-Installation

Dieses Repository ist als **HACS Integration Repository** vorbereitet.

1. Repository auf GitHub erstellen, z. B. `ha-android-kiosk`.
2. Inhalt dieses Ordners in das Repository hochladen.
3. In GitHub einen Release erstellen, z. B. Tag `v1.9.12`.
4. In Home Assistant: **HACS → Drei Punkte → Custom repositories**.
5. Repository-URL einfügen.
6. Kategorie **Integration** wählen.
7. Integration installieren.
8. Home Assistant neu starten.
9. **Einstellungen → Geräte & Dienste → Integration hinzufügen → HA Android Kiosk**.



## Erste Einrichtung

1. HACS-Integration installieren und Home Assistant neu starten.
2. Integration **HA Android Kiosk** hinzufügen.
3. APK auf dem Tablet installieren.
4. App öffnen.
5. Home-Assistant-URL eintragen.
6. Mit Home Assistant anmelden.
7. Gerät im Sidebar-Panel **Android Kiosk** auswählen.
8. Browser, Seiten, Rotation, Hintergrund und Audio einstellen.
9. **Browser-Konfiguration senden**, **Slideshow ans Gerät senden** und **Rotation ans Gerät senden** einmal ausführen.
10. Danach kann das Tablet die Einstellungen selbständig abrufen.




## Lizenz

MIT-Lizenz. Siehe [LICENSE](LICENSE).
