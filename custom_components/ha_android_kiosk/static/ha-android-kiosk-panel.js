class HaAndroidKioskPanel extends HTMLElement {
  constructor() {
    super();
    this.attachShadow({ mode: "open" });
    this._loaded = false;
    this._config = { devices: {}, admin_language: "de" };
    this._uiLanguage = "de";
    this._states = {};
    this._selected = "";
    this._tab = "pages";
    this._status = "";
    this._lastError = "";
    this._backgroundFiles = [];
    this._backgroundAlbums = [{ id: "default", name: "default", count: 0 }];
  }

  set hass(hass) {
    this._hass = hass;
    if (!this._loaded) {
      this._loaded = true;
      this._load();
    }
  }

  async _load() {
    try {
      const [config, devices, backgrounds] = await Promise.all([
        this._hass.callApi("GET", "ha_android_kiosk/config"),
        this._hass.callApi("GET", "ha_android_kiosk/devices"),
        this._hass.callApi("GET", "ha_android_kiosk/backgrounds").catch(() => ({ files: [] })),
      ]);
      this._config = config || { devices: {} };
      this._config.devices = this._config.devices || {};
      const storedAdminLanguage = this._localAdminLanguage();
      this._config.admin_language = this._normalizeLanguage(storedAdminLanguage || this._config.admin_language || "de");
      this._uiLanguage = this._config.admin_language;
      this._states = (devices && devices.states) || {};
      this._backgroundFiles = (backgrounds && backgrounds.files) || [];
      this._backgroundAlbums = (backgrounds && backgrounds.albums) || [{ id: "default", name: "default", count: this._backgroundFiles.length }];
      this._selected = this._selected || Object.keys(this._config.devices)[0] || "";
      this._lastError = "";
      this._render();
    } catch (err) {
      this._lastError = String(err);
      this._render();
    }
  }

  _defaults(id = "") {
    return {
      device_id: id,
      name: id || "Neues Android-Gerät",
      browser: {
        home_url: "/lovelace/0",
        start_url: "/lovelace/0",
        user_agent: "",
        fullscreen: true,
        keep_screen_on: true,
        reload_seconds: 0,
        webview_debug: false,
        external_auth: true,
        hide_header: false,
        wait_for_background: true,
        transparent_webview: true,
        force_mobile_viewport: false,
        reveal_ms: 250,
        zoom_percent: 100,
        dashboard_language: "de",
        settings_sync_seconds: 300,
        visual_watchdog_seconds: 45,
      },
      duration: 15,
      auto_rotate: true,
      pause_on_touch: true,
      touch_pause: 30,
      pages: [{ name: "Home", url: "/lovelace/0", duration: 15 }],
      overlays: {
        ticker_enabled: true,
        ticker_position: "bottom",
        ticker_height: 36,
        ticker_auto_hide_seconds: 15,
        ticker_fixed_messages: [],
        ticker_text: "",
        ticker_color: "#111827",
        ticker_text_color: "#ffffff",
        ticker_font_size: 16,
        ticker_separator: "   •   ",
        banner_title: "Info",
        banner_message: "",
        banner_color: "#2196F3",
        banner_text_color: "#ffffff",
        banner_position: "top",
        banner_duration: 8,
        banner_height: 58,
        banner_font_size: 18,
        alert_title: "Alarm",
        alert_message: "",
        alert_severity: "warning",
        alert_mode: "fullscreen",
        alert_color: "",
        alert_text_color: "#ffffff",
        alert_font_size: 28,
        alert_duration: 10,
        alert_require_ack: false,
        alert_ack_label: "OK",
        clock_enabled: false,
        clock_position: "top-right",
        clock_show_date: true,
        weather_entity: "",
        weather_title: "Wetter",
        weather_text: "",
        weather_position: "top-left",
        weather_layout: "compact",
        weather_duration: 45,
        weather_width: 260,
        weather_height: 86,
        weather_color: "#99000000",
        weather_text_color: "#ffffff",
        weather_font_size: 16,
        weather_show_pressure: false,
        weather_show_forecast: false,
        camera_entity: "",
        camera_url: "",
        camera_title: "Kamera",
        camera_position: "fullscreen",
        camera_mode: "auto",
        camera_duration: 30,
        camera_refresh_seconds: 10,
        camera_width: 320,
        camera_height: 190,
        camera_color: "#000000",
      },
      device_settings: {
        brightness: 80,
        brightness_system: false,
        volume: 50,
        orientation: "unspecified",
        screen_power: true,
        accelerometer_motion: false,
        camera_front: false,
        camera_motion: false,
        camera_motion_facing: "front",
        light_sensor: true,
      },
      media: {
        media_url: "",
        sound_url: "",
        sound_alias: "notification",
        tts_message: "",
        tts_language: "de-DE",
        media_loop: false,
        playlist_text: "",
        media_volume: 100,
        tts_volume: 100,
        audio_stream: "music",
        force_max_volume: true,
        audio_focus: true,
      },
      background: {
        enabled: false,
        album: "default",
        images: [],
        interval: 20,
        random: false,
        size: "cover",
        position: "center",
        repeat: "no-repeat",
        opacity: 100,
      },
      allowed_functions: [
        "display", "browser", "rotation", "ticker", "alerts", "clock", "weather", "camera_overlay",
        "brightness", "volume", "orientation", "vibrate", "camera", "camera_front",
        "camera_motion_detection", "motion_detection", "light_sensor", "media_player", "microphone"
      ],
    };
  }

  _mergeDevice(base, saved) {
    const out = { ...base, ...(saved || {}) };
    out.browser = { ...base.browser, ...((saved && saved.browser) || {}) };
    out.overlays = { ...base.overlays, ...((saved && saved.overlays) || {}) };
    out.device_settings = { ...base.device_settings, ...((saved && saved.device_settings) || {}) };
    out.media = { ...base.media, ...((saved && saved.media) || {}) };
    out.background = { ...base.background, ...((saved && saved.background) || {}) };
    out.pages = Array.isArray(saved && saved.pages) ? saved.pages : base.pages;
    out.allowed_functions = Array.isArray(saved && saved.allowed_functions) ? saved.allowed_functions : base.allowed_functions;
    return out;
  }

  _device() {
    const id = this._selected;
    return this._mergeDevice(this._defaults(id), (this._config.devices || {})[id] || { device_id: id });
  }

  _putDraftDevice(device) {
    if (!device || !device.device_id) return;
    if (this._selected && this._selected !== device.device_id) delete this._config.devices[this._selected];
    this._selected = device.device_id;
    this._config.devices[device.device_id] = device;
  }

  _stateFor(id) {
    return this._states[id] || {};
  }

  _render() {
    const devices = this._config.devices || {};
    const ids = Object.keys(devices).sort();
    const d = this._device();
    const state = this._stateFor(d.device_id || this._selected);
    const uiLang = this._adminLanguage();
    this.shadowRoot.innerHTML = `
      <link rel="stylesheet" href="/ha_android_kiosk_static/ha-android-kiosk-panel.css">
      <style>
        :host { display:block; padding:16px; box-sizing:border-box; }
        * { box-sizing:border-box; }
        .wrap { max-width:1440px; margin:0 auto; }
        ha-card { display:block; overflow:hidden; }
        .head { display:flex; justify-content:space-between; gap:16px; align-items:flex-start; padding:22px 24px; border-bottom:1px solid var(--divider-color); }
        .title { font-size:26px; font-weight:800; letter-spacing:-0.02em; }
        .sub { color:var(--secondary-text-color); margin-top:5px; line-height:1.35; max-width:820px; }
        .topActions, .actions, .miniActions { display:flex; flex-wrap:wrap; gap:8px; }
        .topActions { justify-content:flex-end; align-items:flex-start; }
        .languageBox { min-width:150px; }
        .languageBox label { margin:0 0 4px; font-size:12px; color:var(--secondary-text-color); }
        .languageBox select { padding:8px 10px; border-radius:10px; }
        .layout { display:grid; grid-template-columns:330px 1fr; min-height:680px; }
        .side { border-right:1px solid var(--divider-color); padding:16px; background:var(--secondary-background-color); }
        .main { padding:16px; }
        .device { display:block; width:100%; text-align:left; margin:0 0 10px; border:1px solid var(--divider-color); border-radius:14px; padding:12px; background:var(--card-background-color); color:var(--primary-text-color); cursor:pointer; }
        .device.active { outline:2px solid var(--primary-color); }
        .devName { font-weight:750; font-size:15px; }
        .devId { font-family:monospace; color:var(--secondary-text-color); font-size:12px; margin-top:2px; word-break:break-all; }
        .pill { display:inline-flex; align-items:center; gap:5px; padding:3px 8px; border-radius:999px; background:var(--primary-color); color:var(--text-primary-color); margin-top:8px; font-size:12px; }
        .pill.off { background:var(--disabled-color); color:var(--primary-text-color); }
        .tabs { display:flex; flex-wrap:wrap; gap:8px; margin-bottom:16px; }
        .tab { background:var(--secondary-background-color); color:var(--primary-text-color); border:1px solid var(--divider-color); }
        .tab.active { background:var(--primary-color); color:var(--text-primary-color); border-color:var(--primary-color); }
        .section { border:1px solid var(--divider-color); border-radius:16px; padding:16px; background:var(--card-background-color); margin-bottom:16px; }
        h2 { font-size:18px; margin:0 0 12px; }
        h3 { font-size:15px; margin:18px 0 8px; }
        label { display:block; margin:11px 0 6px; font-weight:650; }
        input, textarea, select { width:100%; border:1px solid var(--divider-color); border-radius:11px; padding:10px; background:var(--secondary-background-color); color:var(--primary-text-color); }
        input[type="checkbox"] { width:auto; }
        textarea { min-height:110px; font-family:ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; resize:vertical; }
        button { border:0; border-radius:11px; padding:10px 14px; background:var(--primary-color); color:var(--text-primary-color); cursor:pointer; font-weight:700; }
        button.secondary { background:var(--secondary-background-color); color:var(--primary-text-color); border:1px solid var(--divider-color); }
        button.warn { background:var(--error-color); color:white; }
        button.ghost { background:transparent; color:var(--primary-color); border:1px solid var(--primary-color); }
        button.small { padding:7px 10px; font-size:12px; border-radius:9px; }
        .grid2 { display:grid; grid-template-columns:1fr 1fr; gap:12px; }
        .grid3 { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:12px; }
        .grid4 { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:12px; }
        .checks { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:8px; margin-top:8px; }
        .checks label { display:flex; align-items:center; gap:8px; margin:0; font-weight:500; line-height:1.25; }
        .hint { color:var(--secondary-text-color); font-size:13px; line-height:1.35; }
        .status { color:var(--secondary-text-color); padding:0 16px 16px; min-height:22px; }
        .error { color:var(--error-color); padding:12px 16px; }
        .kv { display:grid; grid-template-columns:150px 1fr; gap:5px 10px; font-size:13px; margin-top:12px; }
        .kv div:nth-child(odd) { color:var(--secondary-text-color); }
        .pageItem { border:1px solid var(--divider-color); border-radius:14px; padding:12px; margin-bottom:10px; background:var(--secondary-background-color); }
        .pageGrid { display:grid; grid-template-columns:1.1fr 1.8fr 105px 105px; gap:10px; align-items:end; }
        .pageHeader { display:flex; justify-content:space-between; gap:10px; align-items:center; margin-bottom:8px; }
        .badge { font-size:12px; color:var(--secondary-text-color); }
        .preview { border:1px dashed var(--divider-color); border-radius:14px; padding:14px; background:var(--secondary-background-color); min-height:84px; }
        .quick { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:8px; margin:10px 0 14px; }
        .dangerZone { border-color: var(--error-color); }
        .deviceTools { display:grid; grid-template-columns:1fr auto; gap:8px; align-items:center; margin:6px 0; }
        .cleanupRow { display:grid; grid-template-columns:auto 1fr 110px 110px; gap:10px; align-items:center; border:1px solid var(--divider-color); border-radius:12px; padding:10px; margin-bottom:8px; }
        .mediaControls { display:flex; gap:8px; flex-wrap:wrap; align-items:center; margin-top:10px; }
        .bgGrid { display:grid; grid-template-columns:repeat(auto-fill,minmax(150px,1fr)); gap:10px; margin-top:12px; }
        .albumGrid { display:grid; grid-template-columns:repeat(auto-fill,minmax(170px,1fr)); gap:10px; margin:12px 0; }
        .albumCard { border:1px solid var(--divider-color); border-radius:14px; padding:12px; background:var(--secondary-background-color); cursor:pointer; }
        .albumCard.active { border-color:var(--primary-color); box-shadow:0 0 0 2px color-mix(in srgb, var(--primary-color) 30%, transparent); }
        .albumTitle { font-weight:800; margin-bottom:4px; }
        .dropzone { border:2px dashed var(--divider-color); border-radius:16px; padding:18px; background:var(--secondary-background-color); text-align:center; margin:12px 0; }
        .dropzone.drag { border-color:var(--primary-color); background:color-mix(in srgb, var(--primary-color) 12%, var(--secondary-background-color)); }
        .uploadSummary { font-size:13px; color:var(--secondary-text-color); margin-top:8px; white-space:pre-line; }
        .helpBox { border-left:4px solid var(--primary-color); background:var(--secondary-background-color); border-radius:12px; padding:12px; margin:12px 0; }
        .bgItem { border:1px solid var(--divider-color); border-radius:12px; padding:8px; background:var(--secondary-background-color); }
        .bgItem img { width:100%; height:82px; object-fit:cover; border-radius:9px; display:block; background:#111; }
        .bgItem .name { font-size:12px; word-break:break-all; margin-top:6px; color:var(--secondary-text-color); min-height:28px; }
        @media(max-width:980px){ .layout,.grid2,.grid3,.grid4,.pageGrid,.quick { grid-template-columns:1fr; } .side{border-right:0;border-bottom:1px solid var(--divider-color);} }
      </style>
      <div class="wrap"><ha-card>
        <div class="head">
          <div>
            <div class="title">Android Kiosk Admin</div>
            <div class="sub">Konfiguriere Android-Kiosk-Screens, Browser, Rotation und Hintergrund-Alben. Webinterface v17.17: Bedienfunktionen wie Meldungen, Overlays, Module, Gerät und Medien sind jetzt als Automation-&-Testbereich mit kopierfertigen YAML-Beispielen organisiert, damit der echte Betrieb sauber über Home-Assistant-Automationen läuft.</div>
          </div>
          <div class="topActions">
            <div class="languageBox">
              <label for="admin_language">Admin-Sprache</label>
              <select id="admin_language">
                <option value="de" ${uiLang === "de" ? "selected" : ""}>Deutsch</option>
                <option value="en" ${uiLang === "en" ? "selected" : ""}>English</option>
              </select>
            </div>
            <button id="reload" class="secondary">Neu laden</button>
            <button id="saveTop">Speichern</button>
            <button id="applyTop" class="ghost">Profil anwenden</button>
          </div>
        </div>
        ${this._lastError ? `<div class="error">${this._escape(this._lastError)}</div>` : ""}
        <div class="layout">
          <div class="side">
            <h2>Geräte</h2>
            ${ids.length ? ids.map((id) => this._deviceButton(id, devices[id], this._stateFor(id))).join("") : `<div class="hint">Noch kein Gerät registriert. Starte die Android-App und melde sie an.</div>`}
            <div class="actions">
              <button id="newDevice" class="secondary">Gerät hinzufügen</button>
              <button id="deleteDevice" class="warn">Vollständig löschen</button>
              <button id="openCleanup" class="secondary">Testgeräte bereinigen</button>
            </div>
            <div class="section" style="margin-top:16px">
              <h2>Status</h2>
              ${this._statusPanel(d, state)}
            </div>
          </div>
          <div class="main">
            ${this._selected ? this._deviceEditor(d, state) : `<div class="section"><h2>Kein Gerät ausgewählt</h2><div class="hint">Wähle links ein Gerät oder füge eines manuell hinzu.</div></div>`}
          </div>
        </div>
        <div class="status">${this._escape(this._status || "")}</div>
      </ha-card></div>`;
    this._bind();
    this._translateAdminUi();
  }

  _deviceButton(id, cfg, state) {
    const online = this._isOnline(state.last_seen || cfg.last_seen);
    return `<button class="device ${id === this._selected ? "active" : ""}" data-device="${this._escape(id)}">
      <div class="devName">${this._escape((cfg && cfg.name) || id)}</div>
      <div class="devId">${this._escape(id)}</div>
      <span class="pill ${online ? "" : "off"}">${online ? "online" : "offline"}</span>
    </button>`;
  }

  _statusPanel(d, state) {
    const online = this._isOnline(state.last_seen || d.last_seen);
    const bgReady = state.background_ready === true ? "bereit" : (state.background_enabled ? "lädt/warte" : "aus");
    return `<div class="kv">
      <div>Gerät</div><div>${this._escape(d.name || d.device_id)}</div>
      <div>Online</div><div>${online ? "ja" : "nein"}</div>
      <div>Letzter Status</div><div>${this._escape(state.status || state.last_status || d.last_status || "-")}</div>
      <div>Zuletzt gesehen</div><div>${this._escape(state.last_seen || d.last_seen || "-")}</div>
      <div>Aktuelle URL</div><div>${this._escape(state.current_url || d.current_url || "-")}</div>
      <div>Letzter Befehl</div><div>${this._escape(state.last_command || d.last_command || "-")}</div>
      <div>Helligkeit</div><div>${this._escape(state.brightness ?? "-")}</div>
      <div>Lautstärke</div><div>${this._escape(state.volume ?? "-")}</div>
      <div>Hintergrund</div><div>${this._escape(bgReady)}</div>
      <div>HA-Navigation</div><div>${state.hide_ha_header ? "ausgeblendet" : "sichtbar"}</div>
      <div>Seiten-Zoom</div><div>${this._escape(state.current_page_zoom_percent || state.browser_zoom_percent || "-")}${state.current_page_zoom_percent || state.browser_zoom_percent ? "%" : ""}</div>
    </div>`;
  }

  _deviceEditor(d, state) {
    return `${this._identitySection(d)}${this._tabs()}${this._tabContent(d, state)}`;
  }

  _identitySection(d) {
    return `<div class="section">
      <div class="grid2">
        <div><label>Gerätename</label><input id="name" value="${this._escape(d.name || "")}"></div>
        <div><label>Geräte-ID</label><input id="device_id" value="${this._escape(d.device_id || "")}"></div>
      </div>
      <div class="hint" style="margin-top:8px">Die Geräte-ID muss zur ID in der Android-App passen. Name ist frei wählbar.</div>
    </div>`;
  }

  _tabs() {
    const tabs = [
      ["pages", "Screens"],
      ["browser", "Browser"],
      ["background", "Hintergrund"],
      ["automation", "Automation & Tests"],
      ["permissions", "Berechtigungen"],
      ["cleanup", "Bereinigen"]
    ];
    return `<div class="tabs">${tabs.map(([id, label]) => `<button class="tab ${this._tab === id ? "active" : ""}" data-tab="${id}">${label}</button>`).join("")}</div>`;
  }

  _tabContent(d, state) {
    if (["messages", "modules", "device", "media", "examples"].includes(this._tab)) this._tab = "automation";
    if (this._tab === "browser") return this._browserTab(d, state);
    if (this._tab === "background") return this._backgroundTab(d);
    if (this._tab === "automation") return this._automationLabTab(d, state);
    if (this._tab === "permissions") return this._permissionsTab(d);
    if (this._tab === "cleanup") return this._cleanupTab(d);
    return this._pagesTab(d, state);
  }

  _pagesTab(d, state) {
    const pages = d.pages || [];
    const currentUrl = this._escape(this._toRelative(state.current_url || d.current_url || ""));
    return `<div class="section">
      <h2>${this._tr("Dashboard-Seiten")}</h2>
      <div class="hint">${this._tr("Füge Seiten per Formular hinzu. URL kann relativ sein, z. B. /lovelace/0. Pro Seite kannst du optional eine Größe in Prozent setzen, z. B. 120 für größere Widgets.")}</div>
      <div class="quick">
        <button class="secondary quickPage" data-url="/lovelace/0" data-name="Home">+ /lovelace/0</button>
        <button class="secondary quickPage" data-url="/dashboard-home/0" data-name="Dashboard Home">+ dashboard-home</button>
        <button class="secondary quickPage" data-url="/energy" data-name="${this._tr("Energie")}">+ ${this._tr("Energie")}</button>
        <button class="secondary quickPage" data-url="${currentUrl}" data-name="${this._tr("Aktuelle Seite")}">+ ${this._tr("aktuelle URL")}</button>
      </div>
      <div id="pagesList">${pages.map((p, i) => this._pageRow(p, i)).join("")}</div>
      <div class="actions">
        <button id="addPage">${this._tr("Seite hinzufügen")}</button>
        <button id="addManyPages" class="secondary">${this._tr("Mehrere aus Liste einfügen")}</button>
        <button id="savePages">${this._tr("Speichern")}</button>
        <button id="sendPages" class="ghost">${this._tr("Rotation ans Gerät senden")}</button>
      </div>
    </div>
    <div class="section">
      <h2>${this._tr("Rotation")}</h2>
      <div class="grid4">
        <div><label>${this._tr("Standard-Dauer pro Seite")}</label><input id="duration" type="number" min="1" value="${this._escape(d.duration ?? 15)}"></div>
        <div><label>${this._tr("Automatisch rotieren")}</label><select id="auto_rotate"><option value="true" ${d.auto_rotate ? "selected" : ""}>${this._tr("Ja")}</option><option value="false" ${!d.auto_rotate ? "selected" : ""}>${this._tr("Nein")}</option></select></div>
        <div><label>${this._tr("Bei Touch pausieren")}</label><select id="pause_on_touch"><option value="true" ${d.pause_on_touch ? "selected" : ""}>${this._tr("Ja")}</option><option value="false" ${!d.pause_on_touch ? "selected" : ""}>${this._tr("Nein")}</option></select></div>
        <div><label>${this._tr("Touch-Pause Sekunden")}</label><input id="touch_pause" type="number" min="0" value="${this._escape(d.touch_pause ?? 30)}"></div>
      </div>
      <div class="actions">
        <button id="prevPage" class="secondary">${this._tr("Vorherige Seite")}</button>
        <button id="nextPage" class="secondary">${this._tr("Nächste Seite")}</button>
        <button id="pauseRotation" class="secondary">${this._tr("Rotation pausieren")}</button>
        <button id="resumeRotation" class="secondary">${this._tr("Rotation fortsetzen")}</button>
      </div>
    </div>`;
  }

  _pageRow(p, index) {
    const url = typeof p === "string" ? p : (p.url || p.path || "");
    const fallbackName = this._tr("Seite") + ` ${index + 1}`;
    const name = typeof p === "string" ? fallbackName : (p.name || p.title || fallbackName);
    const duration = typeof p === "string" ? "" : (p.duration || "");
    const zoom = typeof p === "string" ? "" : (p.zoom_percent || p.zoom || p.scale_percent || "");
    return `<div class="pageItem" data-page-index="${index}">
      <div class="pageHeader"><strong>${this._tr("Seite")} ${index + 1}</strong><span class="badge">${this._escape(url)}</span></div>
      <div class="pageGrid">
        <div><label>${this._tr("Name")}</label><input class="pageName" value="${this._escape(name)}"></div>
        <div><label>${this._tr("URL / Pfad")}</label><input class="pageUrl" value="${this._escape(url)}" placeholder="/lovelace/0"></div>
        <div><label>${this._tr("Dauer")}</label><input class="pageDuration" type="number" min="1" value="${this._escape(duration)}" placeholder="${this._tr("Standard")}"></div>
        <div><label>${this._tr("Größe %")}</label><input class="pageZoom" type="number" min="50" max="200" value="${this._escape(zoom)}" placeholder="${this._tr("Global")}"></div>
      </div>
      <div class="miniActions" style="margin-top:10px">
        <button class="small secondary movePage" data-dir="-1">↑</button>
        <button class="small secondary movePage" data-dir="1">↓</button>
        <button class="small secondary duplicatePage">${this._tr("Duplizieren")}</button>
        <button class="small ghost testPage">${this._tr("Öffnen")}</button>
        <button class="small warn deletePage">${this._tr("Löschen")}</button>
      </div>
    </div>`;
  }

  _browserTab(d, state) {
    const b = d.browser || {};
    const online = this._isOnline(state.last_seen || d.last_seen);
    const bgReady = state.background_ready === true || !state.background_enabled;
    return `<div class="section heroSection">
      <div class="sectionHead">
        <div>
          <h2>Webbrowser & Kiosk-Modus</h2>
          <div class="hint">Optimiert für dein Android-6-Tablet und Android-9-Smartphone: Hintergrund zuerst, transparente WebView, HA-Navigation per eigenem Kiosk-Injector ohne HACS ausblenden und optional mobile Viewport-Logik.</div>
        </div>
        <button id="applyKioskPreset" class="ghost">Empfohlenes Kiosk-Profil setzen</button>
      </div>
      <div class="metricGrid">
        <div class="metricCard"><div class="metricLabel">Status</div><div class="metricValue">${online ? "online" : "offline"}</div></div>
        <div class="metricCard"><div class="metricLabel">Hintergrund</div><div class="metricValue">${bgReady ? "bereit" : "lädt"}</div></div>
        <div class="metricCard"><div class="metricLabel">Navigation</div><div class="metricValue">${b.hide_header || state.hide_ha_header ? "aus" : "sichtbar"}</div></div>
        <div class="metricCard"><div class="metricLabel">Viewport</div><div class="metricValue">${b.force_mobile_viewport ? "mobil" : "auto"}</div></div>
        <div class="metricCard"><div class="metricLabel">Größe</div><div class="metricValue">${this._escape(state.current_page_zoom_percent || b.zoom_percent || 100)}%</div></div>
        <div class="metricCard"><div class="metricLabel">Sprache</div><div class="metricValue">${this._escape(state.dashboard_language || b.dashboard_language || "de")}</div></div>
        <div class="metricCard"><div class="metricLabel">Selbst-Sync</div><div class="metricValue">${this._escape(state.last_settings_sync_status || "bereit")}</div></div>
      </div>
    </div>
    <div class="section">
      <h2>Start & Navigation</h2>
      <div class="grid2">
        <div><label>Home-/Startseite</label><input id="browser_home_url" value="${this._escape(b.home_url || "/lovelace/0")}" placeholder="/lovelace/0"></div>
        <div><label>Jetzt öffnen</label><input id="browser_open_url" value="${this._escape(b.start_url || b.home_url || "/lovelace/0")}" placeholder="/lovelace/0"></div>
      </div>
      <div class="grid4">
        <div><label>Auto-Reload Sekunden</label><input id="browser_reload_seconds" type="number" min="0" value="${this._escape(b.reload_seconds ?? 0)}"></div>
        <div><label>Vollbild</label><select id="browser_fullscreen"><option value="true" ${b.fullscreen !== false ? "selected" : ""}>Ja</option><option value="false" ${b.fullscreen === false ? "selected" : ""}>Nein</option></select></div>
        <div><label>Display wach halten</label><select id="browser_keep_screen_on"><option value="true" ${b.keep_screen_on !== false ? "selected" : ""}>Ja</option><option value="false" ${b.keep_screen_on === false ? "selected" : ""}>Nein</option></select></div>
        <div><label>Browser-Auto-Login</label><select id="browser_external_auth"><option value="true" ${b.external_auth !== false ? "selected" : ""}>Ja</option><option value="false" ${b.external_auth === false ? "selected" : ""}>Nein</option></select></div>
      </div>
    </div>
    <div class="section">
      <h2>Darstellung & Ladeverhalten</h2>
      <div class="checks richChecks">
        <label><input id="browser_hide_header" type="checkbox" ${b.hide_header ? "checked" : ""}> Home-Assistant-Navigation ausblenden (ohne HACS / eigener Kiosk-Injector)</label>
        <label><input id="browser_wait_for_background" type="checkbox" ${b.wait_for_background !== false ? "checked" : ""}> Hintergrund zuerst laden, dann Dashboard einblenden</label>
        <label><input id="browser_transparent_webview" type="checkbox" ${b.transparent_webview !== false ? "checked" : ""}> WebView transparent über Hintergrund legen</label>
        <label><input id="browser_force_mobile_viewport" type="checkbox" ${b.force_mobile_viewport ? "checked" : ""}> Tablet als mobile Ansicht rendern</label>
        <label><input id="browser_webview_debug" type="checkbox" ${b.webview_debug ? "checked" : ""}> WebView-Debugging aktivieren</label>
      </div>
      <div class="grid4">
        <div><label>Einblenddauer Dashboard ms</label><input id="browser_reveal_ms" type="number" min="0" max="2000" value="${this._escape(b.reveal_ms ?? 250)}"></div>
        <div><label>Standard-Seitengröße %</label><input id="browser_zoom_percent" type="number" min="50" max="200" value="${this._escape(b.zoom_percent ?? 100)}"></div>
        <div><label>Dashboard-Sprache</label><select id="browser_dashboard_language"><option value="de" ${(b.dashboard_language || "de") === "de" ? "selected" : ""}>Deutsch</option><option value="en" ${(b.dashboard_language || "de") === "en" ? "selected" : ""}>English</option></select></div>
        <div><label>User-Agent optional</label><input id="browser_user_agent" value="${this._escape(b.user_agent || "")}" placeholder="Leer = Standard-WebView"></div>
      </div>
      <div class="grid3">
        <div><label>Selbst-Sync alle Sekunden</label><input id="browser_settings_sync_seconds" type="number" min="0" max="86400" value="${this._escape(b.settings_sync_seconds ?? 300)}"></div>
        <div><label>Recovery/Watchdog Sekunden</label><input id="browser_visual_watchdog_seconds" type="number" min="0" max="3600" value="${this._escape(b.visual_watchdog_seconds ?? 45)}"></div>
        <div><label>Letzter Geräte-Sync</label><input readonly value="${this._escape(state.last_settings_sync_status || "-")}"></div>
      </div>
      <div class="helpBox"><strong>Selbst-Sync:</strong> Das Tablet lädt Rotation, Hintergrund und Browser-Einstellungen selbständig neu. 300 Sekunden ist empfohlen; 0 schaltet den zyklischen Abruf aus. Der lokale Cache startet auch, wenn Home Assistant nach langer Pause noch nicht sofort erreichbar ist.</div>
      <div class="helpBox"><strong>Größe/Zoom:</strong> 100% ist normal. 115–130% macht Karten und Widgets größer. In der Seitenliste kannst du für jede Seite einen eigenen Wert setzen; leer bedeutet: Standard-Seitengröße verwenden. Ab dieser Version bleibt der native Hintergrund auch bei 120%+ sichtbar.</div>
      <div class="helpBox"><strong>Dashboard-Sprache:</strong> Deutsch oder Englisch wird an die Android-App gesendet. Die App setzt Accept-Language, schreibt die Sprache in den WebView-Speicher und lädt Home Assistant bei einer Änderung einmal neu.</div>
      <div class="helpBox"><strong>Für das SuitePad/RK3288 empfohlen:</strong> Navigation ausblenden nutzt einen eingebauten Kiosk-Injector ohne HACS. Mobile Viewport nur einschalten, wenn Home Assistant auf dem Tablet weiterhin links eine Sidebar erzwingt.</div>
      <div class="actions">
        <button id="saveBrowser">Speichern</button>
        <button id="applyBrowser" class="ghost">Browser-Konfiguration senden</button>
        <button id="openBrowserUrl" class="secondary">URL öffnen</button>
        <button id="applyLanguage" class="secondary">Sprache anwenden</button>
        <button id="reloadPage" class="secondary">Seite neu laden</button>
        <button id="syncSettingsNow" class="secondary">Einstellungen vom Tablet abrufen lassen</button>
        <button id="recoverDashboard" class="secondary">Anzeige reparieren</button>
      </div>
    </div>
    <div class="section"><h2>Aktuelle Browser-Info</h2>${this._statusPanel(d, state)}</div>`;
  }

  _messagesTab(d) {
    const o = d.overlays || {};
    const fixed = Array.isArray(o.ticker_fixed_messages) ? o.ticker_fixed_messages.join("\n") : "";
    return `<div class="section">
      <h2>Ticker</h2>
      <div class="hint">Der Ticker kann feste Statusmeldungen oder Live-Meldungen anzeigen. Du kannst Farbe, Textgröße, Höhe, Position und Trennzeichen einstellen.</div>
      <div class="grid4">
        <div><label>Aktiv</label><select id="ticker_enabled"><option value="true" ${o.ticker_enabled !== false ? "selected" : ""}>Ja</option><option value="false" ${o.ticker_enabled === false ? "selected" : ""}>Nein</option></select></div>
        <div><label>Position</label><select id="ticker_position"><option value="bottom" ${o.ticker_position !== "top" ? "selected" : ""}>bottom</option><option value="top" ${o.ticker_position === "top" ? "selected" : ""}>top</option></select></div>
        <div><label>Höhe px</label><input id="ticker_height" type="number" min="20" max="120" value="${this._escape(o.ticker_height ?? 36)}"></div>
        <div><label>Auto-Hide Sekunden</label><input id="ticker_auto_hide_seconds" type="number" min="0" value="${this._escape(o.ticker_auto_hide_seconds ?? 15)}"></div>
      </div>
      <div class="grid4">
        <div><label>Hintergrundfarbe</label><input id="ticker_color" value="${this._escape(o.ticker_color || "#111827")}"></div>
        <div><label>Textfarbe</label><input id="ticker_text_color" value="${this._escape(o.ticker_text_color || "#ffffff")}"></div>
        <div><label>Textgröße</label><input id="ticker_font_size" type="number" min="10" max="40" value="${this._escape(o.ticker_font_size ?? 16)}"></div>
        <div><label>Trennzeichen</label><input id="ticker_separator" value="${this._escape(o.ticker_separator || "   •   ")}"></div>
      </div>
      <label>Live-Ticker Text</label><input id="ticker_text" value="${this._escape(o.ticker_text || o.ticker || "")}" placeholder="Willkommen zuhause 👋">
      <label>Feste Tickerliste, eine Meldung pro Zeile</label><textarea id="ticker_fixed_messages">${this._escape(fixed)}</textarea>
      <div class="quick"><button class="secondary quickTicker" data-text="🌡️ Innen: {{ states('sensor.wohnzimmer_temperatur') }} °C">Temperatur-Beispiel</button><button class="secondary quickTicker" data-text="🪟 Fenster prüfen">Fenster-Beispiel</button><button class="secondary quickTicker" data-text="⚡ PV / Strompreis anzeigen">Energie-Beispiel</button></div>
      <div class="actions"><button id="saveMessages">Speichern</button><button id="sendTicker">Live-Ticker senden</button><button id="sendTickerConfig" class="ghost">Ticker-Konfiguration senden</button><button id="clearTicker" class="secondary">Ticker löschen</button></div>
    </div>
    <div class="section">
      <h2>Toast / Banner / Alert</h2>
      <div class="hint">Banner sind kurze Hinweise am Rand. Alerts können als Vollbild, Panel, Banner oder Toast angezeigt werden und optional TTS, Sound, Vibration und Bestätigung verwenden.</div>
      <div class="grid4">
        <div><label>Titel</label><input id="banner_title" value="${this._escape(o.banner_title || "Info")}"></div>
        <div><label>Banner-Position</label><select id="banner_position">${this._positionOptions(o.banner_position || "top")}</select></div>
        <div><label>Banner-Dauer Sekunden</label><input id="banner_duration" type="number" min="0" value="${this._escape(o.banner_duration ?? 8)}"></div>
        <div><label>Banner-Höhe px</label><input id="banner_height" type="number" min="30" max="220" value="${this._escape(o.banner_height ?? 58)}"></div>
      </div>
      <div class="grid4">
        <div><label>Bannerfarbe</label><input id="banner_color" value="${this._escape(o.banner_color || "#2196F3")}"></div>
        <div><label>Banner-Textfarbe</label><input id="banner_text_color" value="${this._escape(o.banner_text_color || "#ffffff")}"></div>
        <div><label>Banner-Textgröße</label><input id="banner_font_size" type="number" min="10" max="42" value="${this._escape(o.banner_font_size ?? 18)}"></div>
        <div><label>Alert-Modus</label><select id="alert_mode">${this._options(["fullscreen","panel","banner","toast"], o.alert_mode || "fullscreen")}</select></div>
      </div>
      <label>Banner-/Toast-Meldung</label><textarea id="banner_message">${this._escape(o.banner_message || "")}</textarea>
      <div class="grid4">
        <div><label>Alert-Titel</label><input id="alert_title" value="${this._escape(o.alert_title || "Alarm")}"></div>
        <div><label>Severity</label><select id="alert_severity">${this._options(["info","success","warning","critical"], o.alert_severity || "warning")}</select></div>
        <div><label>Alert-Dauer Sekunden</label><input id="alert_duration" type="number" min="0" value="${this._escape(o.alert_duration ?? 10)}"></div>
        <div><label>Bestätigen-Text</label><input id="alert_ack_label" value="${this._escape(o.alert_ack_label || "OK")}"></div>
      </div>
      <div class="grid3">
        <div><label>Alert-Farbe optional</label><input id="alert_color" value="${this._escape(o.alert_color || "")}" placeholder="leer = Severity-Farbe"></div>
        <div><label>Alert-Textfarbe</label><input id="alert_text_color" value="${this._escape(o.alert_text_color || "#ffffff")}"></div>
        <div><label>Alert-Textgröße</label><input id="alert_font_size" type="number" min="12" max="60" value="${this._escape(o.alert_font_size ?? 28)}"></div>
      </div>
      <label>Alert-Text</label><textarea id="alert_message">${this._escape(o.alert_message || "")}</textarea>
      <label><input id="alert_require_ack" type="checkbox" ${o.alert_require_ack ? "checked" : ""}> Bestätigung verlangen</label>
      <div class="actions"><button id="sendToast">Toast senden</button><button id="sendBanner">Banner senden</button><button id="sendAlert">Alert senden</button><button id="clearAlerts" class="secondary">Meldungen schließen</button></div>
    </div>`;
  }

  _modulesTab(d) {
    const o = d.overlays || {};
    return `<div class="section">
      <h2>Zusatzmodule</h2>
      <div class="grid3">
        <div><label>Uhr aktiv</label><select id="clock_enabled"><option value="true" ${o.clock_enabled ? "selected" : ""}>Ja</option><option value="false" ${!o.clock_enabled ? "selected" : ""}>Nein</option></select></div>
        <div><label>Uhr-Position</label><select id="clock_position">${this._positionOptions(o.clock_position || "top-right")}</select></div>
        <div><label>Datum anzeigen</label><select id="clock_show_date"><option value="true" ${o.clock_show_date !== false ? "selected" : ""}>Ja</option><option value="false" ${o.clock_show_date === false ? "selected" : ""}>Nein</option></select></div>
      </div>
      <div class="actions"><button id="saveModules">Speichern</button><button id="sendClock">Uhr anzeigen</button><button id="hideClock" class="secondary">Uhr ausblenden</button></div>
    </div>
    <div class="section">
      <h2>Wetter-Overlay</h2>
      <div class="hint">Du kannst eine Weather-Entity verwenden oder einen fertigen Text per Automation senden. Die Integration ergänzt Temperatur, Feuchte und Wind automatisch, wenn möglich.</div>
      <div class="grid4"><div><label>Weather-Entity</label><input id="weather_entity" value="${this._escape(o.weather_entity || "")}" placeholder="weather.home"></div><div><label>Titel</label><input id="weather_title" value="${this._escape(o.weather_title || "Wetter")}"></div><div><label>Position</label><select id="weather_position">${this._positionOptions(o.weather_position || "top-left")}</select></div><div><label>Layout</label><select id="weather_layout">${this._options(["compact","full"], o.weather_layout || "compact")}</select></div></div>
      <div class="grid4"><div><label>Dauer Sekunden</label><input id="weather_duration" type="number" min="0" value="${this._escape(o.weather_duration ?? 45)}"></div><div><label>Breite px</label><input id="weather_width" type="number" min="120" max="900" value="${this._escape(o.weather_width ?? 260)}"></div><div><label>Höhe px</label><input id="weather_height" type="number" min="40" max="500" value="${this._escape(o.weather_height ?? 86)}"></div><div><label>Textgröße</label><input id="weather_font_size" type="number" min="10" max="42" value="${this._escape(o.weather_font_size ?? 16)}"></div></div>
      <div class="grid2"><div><label>Hintergrundfarbe</label><input id="weather_color" value="${this._escape(o.weather_color || "#99000000")}"></div><div><label>Textfarbe</label><input id="weather_text_color" value="${this._escape(o.weather_text_color || "#ffffff")}"></div></div>
      <label>Optionaler Wettertext</label><textarea id="weather_text" placeholder="leer = aus Weather-Entity automatisch bauen">${this._escape(o.weather_text || "")}</textarea>
      <div class="checks"><label><input id="weather_show_pressure" type="checkbox" ${o.weather_show_pressure ? "checked" : ""}> Luftdruck anzeigen</label><label><input id="weather_show_forecast" type="checkbox" ${o.weather_show_forecast ? "checked" : ""}> Vorhersage anzeigen, falls vorhanden</label></div>
      <div class="actions"><button id="saveModules2">Speichern</button><button id="sendWeather">Wetter anzeigen</button><button id="hideWeather" class="secondary">Wetter ausblenden</button></div>
    </div>
    <div class="section">
      <h2>Kamera-Overlay</h2>
      <div class="hint">Kamera kann als Vollbild oder als kleines Bild-im-Bild angezeigt werden. Für ältere Tablets ist Snapshot/Proxy oft stabiler als ein echter Stream.</div>
      <div class="grid4"><div><label>HA Kamera-Entity</label><input id="camera_entity" value="${this._escape(o.camera_entity || "")}" placeholder="camera.haustuer"></div><div><label>Titel</label><input id="camera_title" value="${this._escape(o.camera_title || "Kamera")}"></div><div><label>Position</label><select id="camera_position">${this._positionOptions(o.camera_position || "fullscreen")}</select></div><div><label>Modus</label><select id="camera_mode">${this._options(["auto","snapshot","camera_proxy","entity_picture","stream"], o.camera_mode || "auto")}</select></div></div>
      <div class="grid4"><div><label>Dauer Sekunden</label><input id="camera_duration" type="number" min="0" value="${this._escape(o.camera_duration ?? 30)}"></div><div><label>Refresh Sekunden</label><input id="camera_refresh_seconds" type="number" min="0" value="${this._escape(o.camera_refresh_seconds ?? 10)}"></div><div><label>Breite px</label><input id="camera_width" type="number" min="160" max="1200" value="${this._escape(o.camera_width ?? 320)}"></div><div><label>Höhe px</label><input id="camera_height" type="number" min="90" max="900" value="${this._escape(o.camera_height ?? 190)}"></div></div>
      <label>Kamera-/Bild-URL optional</label><input id="camera_url" value="${this._escape(o.camera_url || "")}" placeholder="/api/camera_proxy/camera.haustuer oder http(s)://...">
      <div class="actions"><button id="saveModules3">Speichern</button><button id="sendCamera">Kamera anzeigen</button><button id="hideCamera" class="secondary">Kamera ausblenden</button></div>
    </div>`;
  }

  _backgroundTab(d) {
    const b = d.background || {};
    const activeAlbum = b.album || "default";
    const albumList = this._backgroundAlbums || [{ id: "default", name: "default", count: 0 }];
    const albumOptions = albumList.map((a) => `<option value="${this._escape(a.id)}" ${activeAlbum === a.id ? "selected" : ""}>${this._escape(a.name || a.id)} (${a.count || 0})</option>`).join("");
    const albumCards = albumList.map((a) => `<div class="albumCard ${activeAlbum === a.id ? "active" : ""}" data-album-id="${this._escape(a.id)}">
      <div class="albumTitle">${this._escape(a.name || a.id)}</div>
      <div class="hint">${this._escape(String(a.count || 0))} Bild(er)</div>
      <div class="miniActions"><button class="small secondary useAlbum">Album wählen</button>${a.id !== "default" ? `<button class="small warn deleteAlbum">Album löschen</button>` : ""}</div>
    </div>`).join("");
    const imagesText = Array.isArray(b.images) ? b.images.join("\n") : "";
    const filtered = (this._backgroundFiles || []).filter((file) => (file.album || "default") === activeAlbum);
    const gallery = filtered.map((file) => `<div class="bgItem" data-bg-url="${this._escape(file.url)}" data-bg-name="${this._escape(file.name)}" data-bg-album="${this._escape(file.album || "default")}">
      <img src="${this._escape(file.url)}" loading="lazy">
      <div class="name">${this._escape(file.name)}</div>
      <div class="hint">Album: ${this._escape(file.album || "default")}</div>
      <div class="miniActions"><button class="small secondary addBgFile">Hinzufügen</button><button class="small warn deleteBgFile">Löschen</button></div>
    </div>`).join("");
    return `<div class="section">
      <h2>Hintergrund-Alben</h2>
      <div class="hint">Lege Alben wie <strong>Bad</strong>, <strong>Küche</strong>, <strong>Ferien</strong> oder <strong>Nachtmodus</strong> an. Pro Gerät wählst du, welches Album als Slideshow läuft.</div>
      <div class="grid3">
        <div><label>Aktives Album für dieses Gerät</label><select id="bg_album">${albumOptions}</select></div>
        <div><label>Neues Album</label><input id="bg_new_album" placeholder="z. B. Bad, Küche, Ferien"></div>
        <div style="align-self:end"><button id="createAlbum">Album erstellen</button></div>
      </div>
      <div class="albumGrid">${albumCards}</div>
    </div>
    <div class="section">
      <h2>Hintergrund-Slideshow</h2>
      <div class="hint">Die App setzt den Hintergrund über dem geladenen Home-Assistant-Dashboard. Du musst in der Lovelace-YAML keinen einzelnen statischen Hintergrund mehr eintragen.</div>
      <div class="helpBox"><strong>Empfohlen:</strong> Entferne den statischen <code>background:</code>-Block aus deiner Dashboard-Seite. Die Slideshow kommt dann aus dem gewählten Album.</div>
      <div class="grid4">
        <div><label>Aktiv</label><select id="bg_enabled"><option value="true" ${b.enabled ? "selected" : ""}>Ja</option><option value="false" ${!b.enabled ? "selected" : ""}>Nein</option></select></div>
        <div><label>Intervall Sekunden</label><input id="bg_interval" type="number" min="2" value="${this._escape(b.interval ?? 20)}"></div>
        <div><label>Zufällig</label><select id="bg_random"><option value="false" ${!b.random ? "selected" : ""}>Nein</option><option value="true" ${b.random ? "selected" : ""}>Ja</option></select></div>
        <div><label>Deckkraft %</label><input id="bg_opacity" type="number" min="0" max="100" value="${this._escape(b.opacity ?? 100)}"></div>
      </div>
      <div class="grid3">
        <div><label>Größe</label><select id="bg_size">${this._options(["cover","contain","auto","100% 100%"], b.size || "cover")}</select></div>
        <div><label>Position</label><select id="bg_position">${this._options(["center","top","bottom","left","right"], b.position || "center")}</select></div>
        <div><label>Wiederholung</label><select id="bg_repeat">${this._options(["no-repeat","repeat","repeat-x","repeat-y"], b.repeat || "no-repeat")}</select></div>
      </div>
      <label>Bildliste, eine URL pro Zeile</label><textarea id="bg_images" placeholder="/local/ha_android_kiosk/backgrounds/bild1.jpg\n/local/ha_android_kiosk/backgrounds/Bad/bild2.jpg">${this._escape(imagesText)}</textarea>
      <div class="actions"><button id="saveBackground">Speichern</button><button id="useAlbumImages" class="secondary">Gewähltes Album verwenden</button><button id="sendBackground" class="ghost">Slideshow ans Gerät senden</button><button id="clearBackground" class="secondary">Hintergrund entfernen</button></div>
    </div>
    <div class="section">
      <h2>Bilder ins Album hochladen</h2>
      <div class="hint">Du kannst mehrere Bilder gleichzeitig auswählen oder hier hineinziehen. Sie werden im gewählten Album gespeichert.</div>
      <div id="bg_dropzone" class="dropzone"><strong>Bilder hier ablegen</strong><br><span class="hint">oder Dateien auswählen</span><input id="bg_upload" type="file" accept="image/*" multiple style="margin-top:12px"><div id="bg_upload_summary" class="uploadSummary">Noch keine Dateien ausgewählt.</div></div>
      <div class="checks">
        <label><input id="bg_upload_add" type="checkbox" checked> Hochgeladene Bilder direkt zur Slideshow hinzufügen</label>
        <label><input id="bg_upload_enable" type="checkbox" checked> Slideshow aktivieren</label>
        <label><input id="bg_upload_save" type="checkbox" checked> Danach automatisch speichern</label>
        <label><input id="bg_upload_send" type="checkbox"> Danach direkt ans ausgewählte Gerät senden</label>
      </div>
      <div class="actions"><button id="uploadBackgrounds">Hochladen</button><button id="uploadUseSend" class="ghost">Hochladen + verwenden + senden</button><button id="refreshBackgrounds" class="secondary">Galerie neu laden</button><button id="addAllBackgrounds" class="secondary">Alle Bilder aus Album hinzufügen</button></div>
      <h3>Galerie: ${this._escape(activeAlbum)}</h3>
      <div class="bgGrid">${gallery || `<div class="hint">Noch keine Bilder in diesem Album hochgeladen.</div>`}</div>
    </div>`;
  }


  _deviceTab(d, state = {}) {
    const s = d.device_settings || {};
    return `<div class="section">
      <h2>Smartphone-Funktionen</h2>
      <div class="grid4">
        <div><label>Helligkeit %</label><input id="brightness" type="number" min="0" max="100" value="${this._escape(s.brightness ?? 80)}"></div>
        <div><label>Lautstärke %</label><input id="volume" type="number" min="0" max="100" value="${this._escape(s.volume ?? 50)}"></div>
        <div><label>Ausrichtung</label><select id="orientation">${this._options(["unspecified","portrait","landscape","reverse_portrait","reverse_landscape","sensor"], s.orientation || "unspecified")}</select></div>
        <div><label>Lichtstärke</label><div class="metricBig">${this._escape(state.light_lux ?? "-")} <span>lx</span></div></div>
      </div>
      <div class="checks">
        <label><input id="brightness_system" type="checkbox" ${s.brightness_system ? "checked" : ""}> Systemhelligkeit</label>
        <label><input id="screen_power" type="checkbox" ${s.screen_power !== false ? "checked" : ""}> Bildschirm an</label>
        <label><input id="accelerometer_motion" type="checkbox" ${s.accelerometer_motion ? "checked" : ""}> Bewegungssensor</label>
        <label><input id="camera_front" type="checkbox" ${s.camera_front ? "checked" : ""}> Frontkamera aktiv</label>
        <label><input id="camera_motion" type="checkbox" ${s.camera_motion ? "checked" : ""}> Frontkamera-Bewegungserkennung</label>
        <label><input id="light_sensor" type="checkbox" ${s.light_sensor !== false ? "checked" : ""}> Lichtsensor melden</label>
      </div>
      <div class="helpBox"><strong>Frontkamera-Bewegung:</strong> Es wird kein Bild an Home Assistant übertragen. Die App wertet nur Helligkeitsänderungen der Frontkamera lokal aus und meldet Bewegung als Sensor.</div>
      <div class="actions"><button id="saveDeviceSettings">Speichern</button><button id="sendBrightness">Helligkeit setzen</button><button id="sendVolume">Lautstärke setzen</button><button id="sendOrientation">Ausrichtung setzen</button><button id="screenOn" class="secondary">Bildschirm an</button><button id="screenBlank" class="secondary">Schwarzbild</button><button id="vibrate" class="secondary">Vibrieren</button></div>
      <div class="actions"><button id="motionOn" class="secondary">Bewegungssensor an</button><button id="motionOff" class="secondary">Bewegungssensor aus</button><button id="frontCameraOn" class="secondary">Frontkamera an</button><button id="frontCameraOff" class="secondary">Frontkamera aus</button><button id="cameraMotionOn" class="secondary">Frontkamera-Motion an</button><button id="cameraMotionOff" class="secondary">Frontkamera-Motion aus</button><button id="allCamerasOff" class="secondary">Kamera aus</button></div>
    </div>
    <div class="section"><h2>Sensorstatus</h2><div class="kv"><div>Frontkamera aktiv</div><div>${state.camera_front_active ? "ja" : "nein"}</div><div>Kamera-Bewegung</div><div>${state.camera_motion_detected ? "erkannt" : "-"}</div><div>Kamera-Wert</div><div>${this._escape(state.camera_motion_score ?? "-")}</div><div>Lichtsensor</div><div>${state.light_sensor_available ? "verfügbar" : "nicht verfügbar / noch nicht gemeldet"}</div></div></div>
    <div class="section dangerZone"><h2>App-Aktionen</h2><div class="hint">Wartungsfunktionen für Geräte, die lange im Kiosk-Betrieb laufen: Status anfordern, Gerät sichtbar identifizieren, WebView-Cache leeren oder die App neu starten.</div><div class="actions"><button id="requestDeviceState" class="secondary">Gerätestatus anfordern</button><button id="identifyDevice" class="secondary">Gerät identifizieren</button><button id="clearWebViewCache" class="secondary">WebView-Cache leeren</button><button id="clearOverlays" class="secondary">Overlays löschen</button><button id="openAndroidSettings" class="secondary">Android-App-Einstellungen öffnen</button><button id="restart" class="warn">App neu starten</button></div></div>`;
  }

  _mediaTab(d) {
    const m = d.media || {};
    const volume = (d.device_settings || {}).volume ?? 70;
    return `<div class="section">
      <h2>Medien / TTS</h2>
      <div class="grid2"><div><label>Media-URL</label><input id="media_url" value="${this._escape(m.media_url || "")}" placeholder="/media/local/song.mp3 oder http(s)://..."></div><div><label>Lautstärke für Medien %</label><input id="media_volume" type="number" min="0" max="100" value="${this._escape(m.media_volume ?? volume)}"></div></div>
      <div class="grid4">
        <div><label>TTS-Lautstärke %</label><input id="tts_volume" type="number" min="0" max="100" value="${this._escape(m.tts_volume ?? 100)}"></div>
        <div><label>Android-Audiokanal</label><select id="audio_stream">${this._options(["music","alarm","notification","ring"], m.audio_stream || "music")}</select></div>
        <label><input id="force_max_volume" type="checkbox" ${m.force_max_volume !== false ? "checked" : ""}> Sprache immer auf Max</label>
        <label><input id="audio_focus" type="checkbox" ${m.audio_focus !== false ? "checked" : ""}> Audio-Fokus anfordern</label>
      </div>
      <div class="helpBox"><strong>Lautsprecher-Boost:</strong> Die App kann den Android-Audiokanal für Sprache/Medien auf Maximum setzen, Audio-Fokus anfordern und optional den Alarm-Kanal verwenden. Mehr als die physische Lautsprecherleistung des Tablets ist softwareseitig nicht möglich, aber bei vielen RK3288-Tablets ist der Alarm-Kanal lauter als Musik.</div>
      <div class="grid2"><div><label>Sound-Alias</label><input id="sound_alias" value="${this._escape(m.sound_alias || "notification")}" placeholder="doorbell, notification, alarm"></div><div><label>Sound-URL</label><input id="sound_url" value="${this._escape(m.sound_url || "")}"></div></div>
      <label>Playlist / Warteschlange, eine URL pro Zeile</label><textarea id="playlist_text" placeholder="/media/local/song1.mp3\n/media/local/song2.mp3">${this._escape(m.playlist_text || "")}</textarea>
      <label><input id="media_loop" type="checkbox" ${m.media_loop ? "checked" : ""}> Wiederholen / Loop</label>
      <div class="mediaControls">
        <button id="saveMedia">Speichern</button>
        <button id="playMedia">Media abspielen</button>
        <button id="playPlaylist" class="ghost">Playlist starten</button>
        <button id="playSound">Sound abspielen</button>
        <button id="mediaPrev" class="secondary">⏮ Zurück</button>
        <button id="mediaPause" class="secondary">⏸ Pause</button>
        <button id="mediaResume" class="secondary">▶ Weiter</button>
        <button id="mediaNext" class="secondary">⏭ Weiter</button>
        <button id="mediaStop" class="secondary">⏹ Stop</button>
      </div>
      <div class="grid3" style="margin-top:12px">
        <div><label>Seek zu Sekunde</label><input id="media_seek" type="number" min="0" value="0"></div>
        <div><label>Mute</label><select id="media_muted"><option value="false">aus</option><option value="true">ein</option></select></div>
        <div><label>Loop direkt setzen</label><select id="media_loop_select"><option value="false" ${!m.media_loop ? "selected" : ""}>aus</option><option value="true" ${m.media_loop ? "selected" : ""}>ein</option></select></div>
      </div>
      <div class="actions"><button id="mediaSeek" class="secondary">Springen</button><button id="mediaMute" class="secondary">Mute an</button><button id="mediaUnmute" class="secondary">Mute aus</button><button id="mediaLoopSet" class="secondary">Loop setzen</button><button id="setMediaVolume" class="secondary">Medienlautstärke setzen</button><button id="setAudioConfig" class="secondary">Audio-Boost senden</button></div>
    </div>
    <div class="section">
      <h2>TTS</h2>
      <div class="grid2"><div><label>TTS Sprache</label><input id="tts_language" value="${this._escape(m.tts_language || "de-DE")}"></div><div><label>Beispiele</label><select id="tts_example"><option value="">Text wählen...</option><option>Achtung. Jemand steht vor der Haustür.</option><option>Die Waschmaschine ist fertig.</option><option>Bitte Fenster schließen.</option><option>Test. Diese Durchsage sollte jetzt lauter sein.</option></select></div></div>
      <label>TTS Text</label><textarea id="tts_message">${this._escape(m.tts_message || "")}</textarea>
      <div class="actions"><button id="ttsSpeak">TTS sprechen</button><button id="ttsLoudTest" class="ghost">Lauten Test sprechen</button><button id="ttsStop" class="secondary">TTS stoppen</button></div>
    </div>`;
  }


  _cleanupTab(d) {
    const devices = this._config.devices || {};
    const ids = Object.keys(devices).sort();
    return `<div class="section dangerZone">
      <h2>Alte Testgeräte bereinigen</h2>
      <div class="hint">Löscht Geräte aus der Kiosk-Konfiguration und entfernt zusätzlich Entity-Registry- und Device-Registry-Einträge dieser Integration. Danach Home Assistant neu laden oder Integration neu starten, falls eine bereits geladene Entity noch sichtbar bleibt.</div>
      <div class="actions" style="margin:12px 0"><button id="selectAllCleanup" class="secondary">Alle markieren</button><button id="selectTestCleanup" class="secondary">Testgeräte markieren</button><button id="selectOfflineCleanup" class="secondary">Offline markieren</button><button id="selectNoneCleanup" class="secondary">Auswahl leeren</button></div>
      ${ids.length ? ids.map((id) => this._cleanupRow(id, devices[id], this._stateFor(id))).join("") : `<div class="hint">Keine gespeicherten Geräte vorhanden.</div>`}
      <div class="actions"><button id="deleteSelectedDevices" class="warn">Markierte vollständig löschen</button><button id="deleteCurrentDevice" class="warn">Aktuelles Gerät vollständig löschen</button></div>
    </div>`;
  }

  _cleanupRow(id, cfg, state) {
    const online = this._isOnline(state.last_seen || cfg.last_seen);
    const label = `${cfg.name || id} (${id})`;
    return `<div class="cleanupRow" data-cleanup-id="${this._escape(id)}">
      <input class="cleanupCheck" type="checkbox" value="${this._escape(id)}">
      <div><strong>${this._escape(cfg.name || id)}</strong><div class="devId">${this._escape(id)}</div></div>
      <span class="pill ${online ? "" : "off"}">${online ? "online" : "offline"}</span>
      <button class="small warn deleteOneCleanup" title="${this._escape(label)}">Löschen</button>
    </div>`;
  }


  _automationLabTab(d, state = {}) {
    const o = d.overlays || {};
    const m = d.media || {};
    const s = d.device_settings || {};
    const fixed = Array.isArray(o.ticker_fixed_messages) ? o.ticker_fixed_messages.join("\n") : "";
    return `<div class="section heroSection">
      <div class="sectionHead">
        <div>
          <h2>Automation & Tests</h2>
          <div class="hint">Dieser Bereich ist bewusst kein Dauer-Bedien-Dashboard mehr. Er dient nur zum Testen von Anzeige-Bausteinen und zum Kopieren fertiger YAML-Automationen. Der echte Betrieb soll danach über Home-Assistant-Automationen, Skripte und Sensoren laufen.</div>
        </div>
        <button id="clearOverlays" class="secondary">Alle Test-Overlays schließen</button>
      </div>
      <div class="metricGrid">
        <div class="metricCard"><div class="metricLabel">Prinzip</div><div class="metricValue">Automation first</div></div>
        <div class="metricCard"><div class="metricLabel">Tests</div><div class="metricValue">lokal senden</div></div>
        <div class="metricCard"><div class="metricLabel">YAML</div><div class="metricValue">kopieren</div></div>
        <div class="metricCard"><div class="metricLabel">Gerät</div><div class="metricValue">${this._escape(d.device_id || "-")}</div></div>
      </div>
    </div>

    <div class="section">
      <div class="sectionHead"><h2>Meldungen testen</h2><div class="hint">Nutze diese Felder nur zum Vorschauen. Für den Alltag kopierst du unten die YAML-Beispiele.</div></div>
      <div class="grid4">
        <div><label>Ticker-Position</label><select id="ticker_position"><option value="bottom" ${o.ticker_position !== "top" ? "selected" : ""}>bottom</option><option value="top" ${o.ticker_position === "top" ? "selected" : ""}>top</option></select></div>
        <div><label>Ticker-Höhe px</label><input id="ticker_height" type="number" min="20" max="120" value="${this._escape(o.ticker_height ?? 36)}"></div>
        <div><label>Ticker-Textgröße</label><input id="ticker_text_size" type="number" min="10" max="42" value="${this._escape(o.ticker_text_size ?? o.ticker_font_size ?? 16)}"></div>
        <div><label>Ticker-Dauer Sekunden</label><input id="ticker_auto_hide_seconds" type="number" min="0" value="${this._escape(o.ticker_auto_hide_seconds ?? 15)}"></div>
      </div>
      <div class="grid3"><div><label>Ticker-Farbe</label><input id="ticker_color" value="${this._escape(o.ticker_color || "#111827")}"></div><div><label>Ticker-Textfarbe</label><input id="ticker_text_color" value="${this._escape(o.ticker_text_color || "#ffffff")}"></div><div><label>Trennzeichen</label><input id="ticker_separator" value="${this._escape(o.ticker_separator || "   •   ")}"></div></div>
      <label>Live-Ticker Testtext</label><input id="ticker_text" value="${this._escape(o.ticker_text || o.ticker || "🌡️ Bad: 22.4 °C   •   Fenster geschlossen")}" placeholder="Kurzer Testtext">
      <label>Feste Tickerliste, eine Meldung pro Zeile</label><textarea id="ticker_fixed_messages" placeholder="Eine Meldung pro Zeile">${this._escape(fixed)}</textarea>
      <div class="actions"><button id="sendTicker">Ticker testen</button><button id="sendTickerConfig" class="secondary">Ticker-Konfiguration testen</button><button id="clearTicker" class="secondary">Ticker schließen</button></div>

      <hr>
      <div class="grid4">
        <div><label>Banner-/Toast-Titel</label><input id="banner_title" value="${this._escape(o.banner_title || "Info")}"></div>
        <div><label>Banner-Position</label><select id="banner_position">${this._positionOptions(o.banner_position || "top")}</select></div>
        <div><label>Banner-Höhe px</label><input id="banner_height" type="number" min="30" max="220" value="${this._escape(o.banner_height ?? 58)}"></div>
        <div><label>Banner-Dauer Sekunden</label><input id="banner_duration" type="number" min="0" value="${this._escape(o.banner_duration ?? 8)}"></div>
      </div>
      <div class="grid3"><div><label>Bannerfarbe</label><input id="banner_color" value="${this._escape(o.banner_color || "#2196F3")}"></div><div><label>Banner-Textfarbe</label><input id="banner_text_color" value="${this._escape(o.banner_text_color || "#ffffff")}"></div><div><label>Banner-Textgröße</label><input id="banner_text_size" type="number" min="10" max="42" value="${this._escape(o.banner_text_size ?? o.banner_font_size ?? 18)}"></div></div>
      <label>Banner-/Toast-Meldung</label><textarea id="banner_message">${this._escape(o.banner_message || "Dies ist eine Testmeldung auf dem Display.")}</textarea>
      <div class="actions"><button id="sendToast" class="secondary">Toast testen</button><button id="sendBanner">Banner testen</button><button id="clearBanner" class="secondary">Banner schließen</button></div>

      <hr>
      <div class="grid4">
        <div><label>Alert-Titel</label><input id="alert_title" value="${this._escape(o.alert_title || "Alarm")}"></div>
        <div><label>Alert-Modus</label><select id="alert_mode">${this._options(["fullscreen","panel","banner","toast"], o.alert_mode || "fullscreen")}</select></div>
        <div><label>Severity</label><select id="alert_severity">${this._options(["info","success","warning","critical"], o.alert_severity || "warning")}</select></div>
        <div><label>Alert-Dauer Sekunden</label><input id="alert_duration" type="number" min="0" value="${this._escape(o.alert_duration ?? 10)}"></div>
      </div>
      <div class="grid4"><div><label>Alert-Farbe optional</label><input id="alert_color" value="${this._escape(o.alert_color || "")}" placeholder="leer = Severity-Farbe"></div><div><label>Alert-Textfarbe</label><input id="alert_text_color" value="${this._escape(o.alert_text_color || "#ffffff")}"></div><div><label>Alert-Textgröße</label><input id="alert_text_size" type="number" min="12" max="60" value="${this._escape(o.alert_text_size ?? o.alert_font_size ?? 28)}"></div><div><label>Sound-Alias</label><input id="alert_sound" value="${this._escape(o.alert_sound || "")}" placeholder="doorbell, alarm"></div></div>
      <label>Alert-Text</label><textarea id="alert_message">${this._escape(o.alert_message || "Achtung: Dies ist ein Test-Alert.")}</textarea>
      <div class="grid2"><label><input id="alert_require_ack" type="checkbox" ${o.alert_require_ack ? "checked" : ""}> Bestätigung verlangen</label><label><input id="alert_vibrate" type="checkbox" ${o.alert_vibrate ? "checked" : ""}> Vibration testen</label></div>
      <label>Optionaler TTS-Text zum Alert</label><input id="alert_tts_message" value="${this._escape(o.alert_tts_message || "")}" placeholder="z. B. Achtung, Wasser erkannt.">
      <div class="actions"><button id="sendAlert">Alert testen</button><button id="clearAlerts" class="secondary">Meldungen schließen</button></div>
    </div>

    <div class="section">
      <div class="sectionHead"><h2>Module testen</h2><div class="hint">Uhr, Wetter, Kamera und Status-Overlay sind Anzeige-Module. Teste sie hier, danach steuerst du sie mit Automationen.</div></div>
      <h3>Uhr</h3>
      <div class="grid3"><div><label>Uhr-Position</label><select id="clock_position">${this._positionOptions(o.clock_position || "top-right")}</select></div><div><label>Datum anzeigen</label><select id="clock_show_date"><option value="true" ${o.clock_show_date !== false ? "selected" : ""}>Ja</option><option value="false" ${o.clock_show_date === false ? "selected" : ""}>Nein</option></select></div><div><label>Uhr aktiv speichern</label><select id="clock_enabled"><option value="false" ${!o.clock_enabled ? "selected" : ""}>Nein</option><option value="true" ${o.clock_enabled ? "selected" : ""}>Ja</option></select></div></div>
      <div class="actions"><button id="sendClock" class="secondary">Uhr testen</button><button id="hideClock" class="secondary">Uhr schließen</button></div>
      <h3>Wetter-Overlay</h3>
      <div class="grid4"><div><label>Weather-Entity</label><input id="weather_entity" value="${this._escape(o.weather_entity || "")}" placeholder="weather.home"></div><div><label>Titel</label><input id="weather_title" value="${this._escape(o.weather_title || "Wetter")}"></div><div><label>Position</label><select id="weather_position">${this._positionOptions(o.weather_position || "top-left")}</select></div><div><label>Layout</label><select id="weather_layout">${this._options(["compact","full"], o.weather_layout || "compact")}</select></div></div>
      <div class="grid4"><div><label>Dauer Sekunden</label><input id="weather_duration" type="number" min="0" value="${this._escape(o.weather_duration ?? 45)}"></div><div><label>Refresh Sekunden</label><input id="weather_refresh_seconds" type="number" min="0" value="${this._escape(o.weather_refresh_seconds ?? 300)}"></div><div><label>Breite px</label><input id="weather_width" type="number" min="120" max="900" value="${this._escape(o.weather_width ?? 260)}"></div><div><label>Höhe px</label><input id="weather_height" type="number" min="40" max="500" value="${this._escape(o.weather_height ?? 86)}"></div></div>
      <div class="checks"><label><input id="weather_show_pressure" type="checkbox" ${o.weather_show_pressure ? "checked" : ""}> Luftdruck anzeigen</label><label><input id="weather_show_forecast" type="checkbox" ${o.weather_show_forecast ? "checked" : ""}> Vorhersage anzeigen</label></div>
      <div class="actions"><button id="sendWeather">Wetter testen</button><button id="hideWeather" class="secondary">Wetter schließen</button></div>
      <h3>Kamera-Overlay</h3>
      <div class="grid4"><div><label>HA Kamera-Entity</label><input id="camera_entity" value="${this._escape(o.camera_entity || "")}" placeholder="camera.haustuer"></div><div><label>Titel</label><input id="camera_title" value="${this._escape(o.camera_title || "Kamera")}"></div><div><label>Position</label><select id="camera_position">${this._positionOptions(o.camera_position || "fullscreen")}</select></div><div><label>Modus</label><select id="camera_mode">${this._options(["auto","snapshot","camera_proxy","entity_picture","stream"], o.camera_mode || "auto")}</select></div></div>
      <div class="grid4"><div><label>Dauer Sekunden</label><input id="camera_duration" type="number" min="0" value="${this._escape(o.camera_duration ?? 30)}"></div><div><label>Refresh Sekunden</label><input id="camera_refresh_seconds" type="number" min="0" value="${this._escape(o.camera_refresh_seconds ?? 10)}"></div><div><label>Breite px</label><input id="camera_width" type="number" min="160" max="1200" value="${this._escape(o.camera_width ?? 420)}"></div><div><label>Höhe px</label><input id="camera_height" type="number" min="90" max="900" value="${this._escape(o.camera_height ?? 260)}"></div></div>
      <label>Kamera-/Bild-URL optional</label><input id="camera_url" value="${this._escape(o.camera_url || "")}" placeholder="/api/camera_proxy/camera.haustuer oder http(s)://...">
      <div class="actions"><button id="sendCamera">Kamera testen</button><button id="hideCamera" class="secondary">Kamera schließen</button></div>
      <h3>Status-Overlay aus Sensoren</h3>
      <div class="grid4"><div><label>Status-Titel</label><input id="status_title" value="${this._escape(o.status_title || "Hausstatus")}"></div><div><label>Modus</label><select id="status_mode">${this._options(["panel","banner","fullscreen"], o.status_mode || "panel")}</select></div><div><label>Position</label><select id="status_position">${this._positionOptions(o.status_position || "center")}</select></div><div><label>Dauer Sekunden</label><input id="status_duration" type="number" min="0" value="${this._escape(o.status_duration ?? 20)}"></div></div>
      <label>Sensoren/Binary-Sensoren, eine Entity pro Zeile</label><textarea id="status_entities" placeholder="sensor.bad_temperatur
binary_sensor.fenster_bad
sensor.waschmaschine_status">${this._escape(Array.isArray(o.status_entities) ? o.status_entities.join("\n") : "")}</textarea>
      <div class="actions"><button id="sendStatusOverlay">Status-Overlay testen</button><button id="sendMorningSequence" class="ghost">Morgen-Sequenz testen</button></div>
    </div>

    <div class="section">
      <div class="sectionHead"><h2>Medien/TTS testen</h2><div class="hint">Kurze Tests für Sprache, Sound und Musik. Für echte Ansagen oder Playlists nutze die YAML-Beispiele unten.</div></div>
      <div class="grid4"><div><label>Android-Audiokanal</label><select id="audio_stream">${this._options(["music","alarm","notification","ring"], m.audio_stream || "music")}</select></div><div><label>TTS-Lautstärke %</label><input id="tts_volume" type="number" min="0" max="100" value="${this._escape(m.tts_volume ?? 100)}"></div><div><label>Medienlautstärke %</label><input id="media_volume" type="number" min="0" max="100" value="${this._escape(m.media_volume ?? 100)}"></div><div><label>TTS Sprache</label><input id="tts_language" value="${this._escape(m.tts_language || "de-DE")}"></div></div>
      <div class="grid2"><label><input id="force_max_volume" type="checkbox" ${m.force_max_volume !== false ? "checked" : ""}> Sprache immer auf Max</label><label><input id="audio_focus" type="checkbox" ${m.audio_focus !== false ? "checked" : ""}> Audio-Fokus anfordern</label></div>
      <label>TTS-Testtext</label><textarea id="tts_message">${this._escape(m.tts_message || "Test. Diese Durchsage kommt aus dem Automation-Testbereich.")}</textarea>
      <div class="grid2"><div><label>Sound-Alias</label><input id="sound_alias" value="${this._escape(m.sound_alias || "notification")}" placeholder="doorbell, notification, alarm"></div><div><label>Sound-URL optional</label><input id="sound_url" value="${this._escape(m.sound_url || "")}"></div></div>
      <div class="grid2"><div><label>Media-URL</label><input id="media_url" value="${this._escape(m.media_url || "")}" placeholder="/media/local/song.mp3 oder http(s)://..."></div><label><input id="media_loop" type="checkbox" ${m.media_loop ? "checked" : ""}> Wiederholen / Loop</label></div>
      <div class="actions"><button id="ttsSpeak">TTS testen</button><button id="ttsLoudTest" class="ghost">Lauten Test sprechen</button><button id="ttsStop" class="secondary">TTS stoppen</button><button id="playSound" class="secondary">Sound testen</button><button id="playMedia" class="secondary">Media testen</button><button id="mediaStop" class="secondary">Media stoppen</button></div>
    </div>

    <div class="section dangerZone">
      <div class="sectionHead"><h2>Geräte-Testbefehle</h2><div class="hint">Nur für Diagnose und kurze Tests. Regelmäßige Helligkeit, Lautstärke oder Cache-Reparatur lieber per Automation ausführen.</div></div>
      <div class="grid4"><div><label>Helligkeit %</label><input id="brightness" type="number" min="0" max="100" value="${this._escape(s.brightness ?? 80)}"></div><div><label>Lautstärke %</label><input id="volume" type="number" min="0" max="100" value="${this._escape(s.volume ?? 50)}"></div><div><label>Ausrichtung</label><select id="orientation">${this._options(["unspecified","portrait","landscape","reverse_portrait","reverse_landscape","sensor"], s.orientation || "unspecified")}</select></div><label><input id="brightness_system" type="checkbox" ${s.brightness_system ? "checked" : ""}> Systemhelligkeit</label></div>
      <div class="actions"><button id="sendBrightness" class="secondary">Helligkeit testen</button><button id="sendVolume" class="secondary">Lautstärke testen</button><button id="sendOrientation" class="secondary">Ausrichtung testen</button><button id="requestDeviceState" class="secondary">Gerätestatus anfordern</button><button id="identifyDevice" class="secondary">Gerät identifizieren</button><button id="clearWebViewCache" class="secondary">WebView-Cache leeren</button><button id="restart" class="warn">App neu starten</button></div>
    </div>

    <div class="section">
      <h2>Kopierfertige YAML-Automationen</h2>
      <div class="hint">Diese Beispiele sind als Vorlage gedacht. Kopiere ein Beispiel, ersetze nur <code>device</code> und deine Entity-IDs/Sensoren, und füge es in Home Assistant als Automation oder Script ein.</div>
    </div>
    ${this._automationYamlExamples(d)}`;
  }

  _automationYamlExamples(d) {
    const device = ((d && d.device_id) || this._selected || "bad_tablet").trim() || "bad_tablet";
    const examples = [];
    examples.push(["Display je nach Tageszeit wechseln", `alias: Kiosk - Display je nach Tageszeit wechseln
mode: restart
trigger:
  - platform: time
    at: "06:30:00"
  - platform: time
    at: "22:30:00"
action:
  - choose:
      - conditions: "{{ now().hour >= 22 or now().hour < 6 }}"
        sequence:
          - service: ha_android_kiosk.goto_screen
            data:
              device: ${device}
              page_id: /lovelace/nacht
          - service: ha_android_kiosk.set_brightness
            data:
              device: ${device}
              value: 20
    default:
      - service: ha_android_kiosk.goto_screen
        data:
          device: ${device}
          page_id: /lovelace/home
      - service: ha_android_kiosk.set_brightness
        data:
          device: ${device}
          value: 85
`]);
    examples.push(["Ticker aus Sensoren", `alias: Kiosk - Sensorwerte als Ticker anzeigen
mode: restart
trigger:
  - platform: time_pattern
    minutes: "/5"
action:
  - service: ha_android_kiosk.show_sensor_overlay
    data:
      device: ${device}
      type: ticker
      title: "Status"
      entities:
        - sensor.bad_temperatur
        - sensor.aussentemperatur
        - sensor.pv_leistung
        - sensor.strompreis
      separator: "   •   "
      position: bottom
      duration: 300
      color: "#111827"
      text_color: "#ffffff"
`]);
    examples.push(["Fenster offen als Banner", `alias: Kiosk - Fenster offen melden
mode: restart
trigger:
  - platform: state
    entity_id:
      - binary_sensor.fenster_bad
      - binary_sensor.fenster_wohnzimmer
    to: "on"
action:
  - service: ha_android_kiosk.show_banner
    data:
      device: ${device}
      title: "Fenster offen"
      message: "{{ trigger.to_state.attributes.friendly_name or trigger.entity_id }} ist offen"
      position: top
      color: "#f59e0b"
      text_color: "#111827"
      duration: 30
      wake_screen: true
`]);
    examples.push(["Wasserleck als kritischer Alert", `alias: Kiosk - Wasserleck kritisch anzeigen
mode: single
trigger:
  - platform: state
    entity_id: binary_sensor.wassersensor_bad
    to: "on"
action:
  - service: ha_android_kiosk.show_alert
    data:
      device: ${device}
      title: "Wasser erkannt"
      message: "Bitte sofort im Badezimmer prüfen!"
      severity: critical
      mode: fullscreen
      duration: 0
      require_ack: true
      wake_screen: true
      vibrate: true
      sound: alarm
      tts_message: "Achtung. Wasser wurde im Badezimmer erkannt."
      tts_language: de-DE
      volume: 100
`]);
    examples.push(["Türklingel mit Kamera", `alias: Kiosk - Türklingel mit Kamera
mode: restart
trigger:
  - platform: state
    entity_id: binary_sensor.haustuer_klingel
    to: "on"
action:
  - service: ha_android_kiosk.goto_screen
    data:
      device: ${device}
      page_id: /lovelace/kamera
  - service: ha_android_kiosk.show_camera
    data:
      device: ${device}
      entity_id: camera.haustuer
      title: "Haustür"
      mode: snapshot
      position: bottom-right
      width: 420
      height: 260
      duration: 45
      refresh_seconds: 5
      wake_screen: true
  - service: ha_android_kiosk.tts_speak
    data:
      device: ${device}
      message: "Jemand steht vor der Haustür."
      language: de-DE
      volume: 100
      stream: alarm
      force_max_volume: true
`]);
    examples.push(["Wetter morgens anzeigen", `alias: Kiosk - Wetter morgens anzeigen
mode: single
trigger:
  - platform: time
    at: "07:00:00"
action:
  - service: ha_android_kiosk.show_weather
    data:
      device: ${device}
      entity_id: weather.home
      title: "Wetter heute"
      position: top-left
      layout: full
      show_forecast: true
      show_pressure: true
      duration: 60
      wake_screen: true
`]);
    examples.push(["Status-Overlay bei Bewegung", `alias: Kiosk - Hausstatus bei Bewegung anzeigen
mode: restart
trigger:
  - platform: state
    entity_id: binary_sensor.flur_bewegung
    to: "on"
action:
  - service: ha_android_kiosk.show_status_overlay
    data:
      device: ${device}
      title: "Hausstatus"
      entities:
        - sensor.bad_temperatur
        - sensor.wohnzimmer_temperatur
        - binary_sensor.fenster_bad
        - sensor.waschmaschine_status
      position: center
      duration: 20
      color: "#111827"
      text_color: "#ffffff"
      wake_screen: true
`]);
    examples.push(["Waschmaschine fertig mit TTS", `alias: Kiosk - Waschmaschine fertig melden
mode: single
trigger:
  - platform: state
    entity_id: sensor.waschmaschine_status
    to: "fertig"
action:
  - service: ha_android_kiosk.show_banner
    data:
      device: ${device}
      title: "Waschmaschine"
      message: "Die Wäsche ist fertig"
      position: top
      color: "#15803d"
      text_color: "#ffffff"
      duration: 20
      wake_screen: true
  - service: ha_android_kiosk.tts_speak
    data:
      device: ${device}
      message: "Die Waschmaschine ist fertig."
      language: de-DE
      volume: 100
      stream: alarm
      force_max_volume: true
`]);
    examples.push(["Musik oder Sound per Automation", `alias: Kiosk - Begrüßungssound abspielen
mode: single
trigger:
  - platform: state
    entity_id: binary_sensor.eingang_bewegung
    to: "on"
action:
  - service: ha_android_kiosk.play_media
    data:
      device: ${device}
      media_url: /media/local/kiosk/welcome.mp3
      volume: 85
      stream: music
      loop: false
      audio_focus: true
`]);
    examples.push(["Morgen-Sequenz mit mehreren Overlays", `alias: Kiosk - Morgen-Sequenz
mode: single
trigger:
  - platform: time
    at: "07:15:00"
action:
  - service: ha_android_kiosk.show_overlay_sequence
    data:
      device: ${device}
      default_duration: 8
      gap: 1
      clear_between: true
      clear_after: false
      steps:
        - type: banner
          title: "Guten Morgen"
          message: "Hier ist dein Tagesstart"
          color: "#2563eb"
        - type: weather
          entity_id: weather.home
          title: "Wetter"
          position: top-left
          layout: full
          show_forecast: true
        - type: status_overlay
          title: "Wichtige Werte"
          entities:
            - sensor.aussentemperatur
            - sensor.strompreis
            - sensor.pv_leistung
`]);
    examples.push(["Alles schließen / Display aufräumen", `alias: Kiosk - Overlays schließen
mode: single
trigger:
  - platform: time
    at: "23:00:00"
action:
  - service: ha_android_kiosk.clear_overlays
    data:
      device: ${device}
  - service: ha_android_kiosk.stop_media
    data:
      device: ${device}
  - service: ha_android_kiosk.goto_screen
    data:
      device: ${device}
      page_id: /lovelace/nacht
`]);
    return examples.map(([title, yaml]) => this._yamlBox(title, yaml)).join("");
  }

  _yamlBox(title, yaml) {
    const id = `yaml_${Math.random().toString(36).slice(2)}`;
    return `<div class="section"><div class="sectionHead"><h2>${this._escape(title)}</h2><button class="secondary copyYaml" data-copy-target="${id}">YAML kopieren</button></div><textarea id="${id}" readonly style="min-height:260px;font-family:monospace">${this._escape(yaml)}</textarea></div>`;
  }

  _permissionsTab(d) {
    const allowed = new Set(d.allowed_functions || []);
    const list = [
      ["display", "Display / Seiten"], ["browser", "Browser"], ["rotation", "Rotation"], ["ticker", "Ticker"], ["alerts", "Meldungen / Alerts"], ["clock", "Uhr"], ["weather", "Wetter"], ["camera_overlay", "Kamera-Overlay"], ["brightness", "Helligkeit"], ["volume", "Lautstärke"], ["orientation", "Ausrichtung"], ["vibrate", "Vibration"], ["camera", "Kamera allgemein"], ["camera_front", "Frontkamera"], ["camera_motion_detection", "Frontkamera-Bewegung"], ["motion_detection", "Bewegungssensor"], ["light_sensor", "Lichtsensor"], ["media_player", "Medien/TTS"], ["microphone", "Mikrofon"]
    ];
    return `<div class="section"><h2>Berechtigungen für Home Assistant</h2><div class="hint">Nur aktivierte Funktionen dürfen von Home Assistant an dieses Gerät gesendet werden.</div><div class="checks">${list.map(([id, label]) => `<label><input class="allowed" type="checkbox" value="${id}" ${allowed.has(id) ? "checked" : ""}> ${label}</label>`).join("")}</div><div class="actions"><button id="savePermissions">Speichern</button><button id="allowAll" class="secondary">Alles erlauben</button><button id="allowSafe" class="secondary">Nur Anzeige/Overlays</button></div></div>`;
  }

  _bind() {
    const on = (id, fn) => { const el = this.shadowRoot.getElementById(id); if (el) el.addEventListener("click", fn); };
    this.shadowRoot.querySelectorAll("[data-device]").forEach((el) => el.addEventListener("click", () => { this._selected = el.dataset.device; this._render(); }));
    this.shadowRoot.querySelectorAll("[data-tab]").forEach((el) => el.addEventListener("click", () => { this._saveDraftFromDom(); this._tab = el.dataset.tab; this._render(); }));
    this.shadowRoot.querySelectorAll(".copyYaml").forEach((el) => el.addEventListener("click", () => this._copyYaml(el.dataset.copyTarget)));
    on("reload", () => this._load());
    const adminLanguage = this.shadowRoot.getElementById("admin_language");
    if (adminLanguage) adminLanguage.addEventListener("change", () => this._setAdminLanguage(adminLanguage.value));
    on("saveTop", () => this._save());
    on("applyTop", async () => { if (await this._save(false)) await this._command("apply_profile", {}); });
    on("newDevice", () => this._newDevice());
    on("deleteDevice", () => this._deleteDevice());
    on("openCleanup", () => { this._tab = "cleanup"; this._render(); });

    on("addPage", () => this._addPage({ name: `Seite ${this._collectPages().length + 1}`, url: "/lovelace/0", duration: this._value("duration", "15") }));
    on("addManyPages", () => this._addManyPages());
    this.shadowRoot.querySelectorAll(".quickPage").forEach((el) => el.addEventListener("click", () => this._addPage({ name: el.dataset.name || "Seite", url: el.dataset.url || "/lovelace/0", duration: this._value("duration", "15") })));
    this.shadowRoot.querySelectorAll(".pageItem").forEach((row) => {
      const idx = Number(row.dataset.pageIndex);
      const click = (sel, fn) => { const el = row.querySelector(sel); if (el) el.addEventListener("click", () => fn(idx)); };
      click(".deletePage", (i) => this._deletePage(i));
      click(".duplicatePage", (i) => this._duplicatePage(i));
      click(".testPage", (i) => this._testPage(i));
      const move = row.querySelector(".movePage");
      if (move) move.addEventListener("click", () => this._movePage(idx, Number(move.dataset.dir || 0)));
      row.querySelectorAll(".movePage").forEach((el) => el.addEventListener("click", () => this._movePage(idx, Number(el.dataset.dir || 0))));
    });
    on("savePages", () => this._save());
    on("sendPages", async () => { if (await this._save(false)) await this._command("set_pages", this._pagePayload()); });
    on("nextPage", () => this._command("next_screen", {}));
    on("prevPage", () => this._command("previous_screen", {}));
    on("pauseRotation", () => this._command("pause_rotation", { duration: Number(this._value("touch_pause") || 300) }));
    on("resumeRotation", () => this._command("resume_rotation", {}));

    on("saveBrowser", () => this._save());
    on("applyBrowser", async () => { if (await this._save(false)) await this._command("browser_config", this._browserPayload()); });
    on("applyLanguage", async () => { if (await this._save(false)) await this._command("browser_config", { dashboard_language: this._value("browser_dashboard_language") || "de", language: this._value("browser_dashboard_language") || "de" }); });
    on("applyKioskPreset", () => this._applyRecommendedKioskPreset());
    on("openBrowserUrl", () => this._command("show_page", { url: this._value("browser_open_url") }));
    on("reloadPage", () => this._command("reload_page", {}));
    on("syncSettingsNow", () => this._command("sync_settings", {}));
    on("recoverDashboard", () => this._command("recover_dashboard", {}));

    on("saveMessages", () => this._save());
    on("sendTicker", () => this._command("ticker", { text: this._value("ticker_text"), duration: Number(this._value("ticker_auto_hide_seconds") || 0), visible: true, position: this._value("ticker_position"), height: Number(this._value("ticker_height") || 38), color: this._value("ticker_color"), text_color: this._value("ticker_text_color"), text_size: Number(this._value("ticker_text_size") || 16), separator: this._value("ticker_separator") || "   •   " }));
    on("sendTickerConfig", () => this._command("ticker_config", this._tickerPayload()));
    on("clearTicker", () => this._command("clear_ticker", {}));
    on("sendToast", () => this._command("toast", { title: this._value("banner_title"), message: this._value("banner_message") || this._value("ticker_text"), duration: Number(this._value("banner_duration") || 6) }));
    on("sendBanner", () => this._command("banner", { title: this._value("banner_title"), message: this._value("banner_message"), color: this._value("banner_color"), text_color: this._value("banner_text_color") || "#ffffff", position: this._value("banner_position") || "top", height: Number(this._value("banner_height") || 64), text_size: Number(this._value("banner_text_size") || 18), duration: Number(this._value("banner_duration") || 8), wake_screen: true }));
    on("clearBanner", () => this._command("clear", { target: "banner" }));
    on("sendAlert", () => this._command("alert", { title: this._value("alert_title"), message: this._value("alert_message") || this._value("banner_message"), severity: this._value("alert_severity"), mode: this._value("alert_mode") || "fullscreen", color: this._value("alert_color"), text_color: this._value("alert_text_color"), text_size: Number(this._value("alert_text_size") || 28), duration: Number(this._value("alert_duration") || 0), require_ack: this._checked("alert_require_ack"), vibrate: this._checked("alert_vibrate"), sound: this._value("alert_sound"), tts_message: this._value("alert_tts_message"), wake_screen: true }));
    on("clearAlerts", () => this._command("clear_alert", {}));
    on("sendStatusOverlay", () => this._command("status_overlay", { title: this._value("status_title") || "Hausstatus", mode: this._value("status_mode") || "panel", position: this._value("status_position") || "center", duration: Number(this._value("status_duration") || 20), entities: this._value("status_entities").split(/\n|,/).map((x) => x.trim()).filter(Boolean), wake_screen: true }));
    on("sendMorningSequence", () => this._command("overlay_sequence", { default_duration: 8, gap: 1, steps: [ { type: "banner", title: "Guten Morgen", message: "Hier ist dein Tagesstart", color: "#2563eb", duration: 5 }, { type: "weather", entity_id: this._value("weather_entity") || "weather.home", title: this._value("weather_title") || "Wetter", position: "top-left", layout: "full", duration: 10 }, { type: "status_overlay", title: this._value("status_title") || "Hausstatus", entities: this._value("status_entities").split(/\n|,/).map((x) => x.trim()).filter(Boolean), duration: 12 } ] }));

    on("saveModules", () => this._save());
    on("sendClock", () => this._command("clock", { visible: true, position: this._value("clock_position"), show_date: this._boolValue("clock_show_date") }));
    on("hideClock", () => this._command("clock", { visible: false }));
    on("sendWeather", () => this._command("weather", { entity_id: this._value("weather_entity"), title: this._value("weather_title"), position: this._value("weather_position"), layout: this._value("weather_layout") || "compact", duration: Number(this._value("weather_duration") || 45), refresh_seconds: Number(this._value("weather_refresh_seconds") || 300), width: Number(this._value("weather_width") || 280), height: Number(this._value("weather_height") || 74), show_forecast: this._checked("weather_show_forecast"), show_pressure: this._checked("weather_show_pressure"), visible: true, wake_screen: true }));
    on("hideWeather", () => this._command("clear", { target: "weather" }));
    on("sendCamera", () => this._command("camera", { entity_id: this._value("camera_entity"), url: this._value("camera_url"), title: this._value("camera_title"), mode: this._value("camera_mode") || "auto", position: this._value("camera_position"), duration: Number(this._value("camera_duration") || 30), refresh_seconds: Number(this._value("camera_refresh_seconds") || 0), width: Number(this._value("camera_width") || 360), height: Number(this._value("camera_height") || 220), visible: true, wake_screen: true }));
    on("hideCamera", () => this._command("clear", { target: "camera" }));

    on("saveBackground", () => this._save());
    on("sendBackground", async () => { if (await this._save(false)) await this._command("background_slideshow", this._backgroundPayload()); });
    on("clearBackground", () => this._command("clear_background", {}));
    on("uploadBackgrounds", () => this._uploadBackgrounds(false));
    on("uploadUseSend", () => this._uploadBackgrounds(true));
    on("refreshBackgrounds", () => this._load());
    on("addAllBackgrounds", () => this._addAllBackgrounds());
    on("useAlbumImages", () => this._useSelectedAlbumImages());
    on("createAlbum", () => this._createAlbum());
    const albumSelect = this.shadowRoot.getElementById("bg_album");
    if (albumSelect) albumSelect.addEventListener("change", () => this._setActiveAlbum(albumSelect.value));
    this.shadowRoot.querySelectorAll(".albumCard").forEach((el) => el.addEventListener("click", (ev) => {
      if (ev.target && ev.target.classList && ev.target.classList.contains("deleteAlbum")) return;
      this._setActiveAlbum(el.dataset.albumId || "default");
    }));
    this.shadowRoot.querySelectorAll(".useAlbum").forEach((el) => el.addEventListener("click", () => { const row = el.closest("[data-album-id]"); if (row) this._setActiveAlbum(row.dataset.albumId || "default"); }));
    this.shadowRoot.querySelectorAll(".deleteAlbum").forEach((el) => el.addEventListener("click", () => { const row = el.closest("[data-album-id]"); if (row) this._deleteAlbum(row.dataset.albumId || ""); }));
    this._bindBackgroundUpload();
    this.shadowRoot.querySelectorAll(".addBgFile").forEach((el) => el.addEventListener("click", () => { const row = el.closest("[data-bg-url]"); if (row) this._addBackgroundUrl(row.dataset.bgUrl); }));
    this.shadowRoot.querySelectorAll(".deleteBgFile").forEach((el) => el.addEventListener("click", () => { const row = el.closest("[data-bg-name]"); if (row) this._deleteBackgroundFile(row.dataset.bgName, row.dataset.bgAlbum || "default"); }));

    on("saveDeviceSettings", () => this._save());
    on("sendBrightness", () => this._command("brightness", { value: Number(this._value("brightness") || 0), system: this._checked("brightness_system") }));
    on("sendVolume", () => this._command("volume", { value: Number(this._value("volume") || 0), stream: "music" }));
    on("sendOrientation", () => this._command("orientation", { value: this._value("orientation") }));
    on("screenOn", () => this._command("set_screen_power", { power: true }));
    on("screenBlank", () => this._command("set_screen_power", { power: false }));
    on("vibrate", () => this._command("vibrate", { duration: 350 }));
    on("motionOn", () => this._command("motion_detection", { enabled: true }));
    on("motionOff", () => this._command("motion_detection", { enabled: false }));
    on("frontCameraOn", () => this._command("camera_front", { enabled: true }));
    on("frontCameraOff", () => this._command("camera_front", { enabled: false }));
    on("cameraMotionOn", () => this._command("camera_motion_detection", { enabled: true, facing: "front" }));
    on("cameraMotionOff", () => this._command("camera_motion_detection", { enabled: false }));
    on("allCamerasOff", () => this._command("camera_all_off", {}));
    on("requestDeviceState", () => this._command("report_device_state", {}));
    on("identifyDevice", () => this._command("identify_device", {}));
    on("clearWebViewCache", () => this._command("clear_webview_cache", {}));
    on("clearOverlays", () => this._command("clear", { target: "overlays" }));
    on("openAndroidSettings", () => this._command("open_android_settings", {}));
    on("restart", () => this._command("restart_app", {}));

    on("saveMedia", () => this._save());
    on("playMedia", () => this._command("play_media", { media_url: this._value("media_url"), volume: Number(this._value("media_volume") || this._value("volume") || 70), stream: this._value("audio_stream") || "music", loop: this._checked("media_loop"), force_max_volume: this._checked("force_max_volume"), audio_focus: this._checked("audio_focus") }));
    on("playSound", () => this._command("play_sound", { sound: this._value("sound_alias"), sound_url: this._value("sound_url"), volume: Number(this._value("media_volume") || 70), stream: this._value("audio_stream") || "music", force_max_volume: this._checked("force_max_volume"), audio_focus: this._checked("audio_focus") }));
    on("ttsSpeak", () => this._command("tts_speak", { message: this._value("tts_message"), language: this._value("tts_language") || "de-DE", volume: Number(this._value("tts_volume") || 100), stream: this._value("audio_stream") || "music", force_max_volume: this._checked("force_max_volume"), audio_focus: this._checked("audio_focus") }));
    on("ttsLoudTest", () => this._command("tts_speak", { message: "Test. Diese Durchsage sollte jetzt lauter sein.", language: this._value("tts_language") || "de-DE", volume: 100, stream: this._value("audio_stream") || "alarm", force_max_volume: true, audio_focus: true }));
    on("playPlaylist", () => this._command("play_media", this._playlistPayload()));
    on("mediaPause", () => this._command("media_pause", {}));
    on("mediaResume", () => this._command("media_resume", {}));
    on("mediaStop", () => this._command("stop_media", {}));
    on("mediaNext", () => this._command("media_next", {}));
    on("mediaPrev", () => this._command("media_previous", {}));
    on("mediaSeek", () => this._command("media_seek", { position: Number(this._value("media_seek") || 0) }));
    on("mediaMute", () => this._command("media_mute", { muted: true }));
    on("mediaUnmute", () => this._command("media_unmute", {}));
    on("mediaLoopSet", () => this._command("media_loop", { loop: this._boolValue("media_loop_select") }));
    on("setMediaVolume", () => this._command("set_media_volume", { volume: Number(this._value("media_volume") || 70), stream: this._value("audio_stream") || "music" }));
    on("setAudioConfig", () => this._command("audio_config", this._audioPayload()));
    on("ttsStop", () => this._command("tts_stop", {}));
    const ttsExample = this.shadowRoot.getElementById("tts_example");
    if (ttsExample) ttsExample.addEventListener("change", () => { const t = this.shadowRoot.getElementById("tts_message"); if (t && ttsExample.value) t.value = ttsExample.value; });

    on("savePermissions", () => this._save());
    on("selectAllCleanup", () => this._selectCleanup("all"));
    on("selectNoneCleanup", () => this._selectCleanup("none"));
    on("selectTestCleanup", () => this._selectCleanup("test"));
    on("selectOfflineCleanup", () => this._selectCleanup("offline"));
    on("deleteSelectedDevices", () => this._deleteSelectedDevices());
    on("deleteCurrentDevice", () => this._deleteDevice());
    this.shadowRoot.querySelectorAll(".deleteOneCleanup").forEach((el) => el.addEventListener("click", () => { const row = el.closest("[data-cleanup-id]"); if (row) this._deleteDeviceById(row.dataset.cleanupId); }));
    on("allowAll", () => { this.shadowRoot.querySelectorAll(".allowed").forEach((el) => el.checked = true); });
    on("allowSafe", () => {
      const safe = new Set(["display", "browser", "rotation", "ticker", "alerts", "clock", "weather", "camera_overlay"]);
      this.shadowRoot.querySelectorAll(".allowed").forEach((el) => el.checked = safe.has(el.value));
    });
  }

  _newDevice() {
    const id = prompt(this._tr("Geräte-ID aus der Android-App:"));
    if (!id) return;
    const clean = id.trim();
    this._selected = clean;
    this._config.devices[clean] = this._defaults(clean);
    this._render();
  }

  async _deleteDevice() {
    if (!this._selected) return;
    await this._deleteDeviceById(this._selected);
  }

  async _deleteDeviceById(id) {
    if (!id) return;
    if (!confirm(this._adminLanguage() === "en" ? `Delete device ${id} completely?\n\nThis also removes the 27 entities and the device registry entry for this integration.` : `Gerät ${id} vollständig löschen?\n\nDas entfernt auch die 27 Entitäten und den Geräte-Registry-Eintrag dieser Integration.`)) return;
    try {
      await this._hass.callApi("DELETE", `ha_android_kiosk/devices/${encodeURIComponent(id)}?remove_registry=true`);
      delete this._config.devices[id];
      delete this._states[id];
      if (this._selected === id) this._selected = Object.keys(this._config.devices)[0] || "";
      this._status = `Gerät ${id} wurde vollständig gelöscht.`;
      this._lastError = "";
      await this._load();
    } catch (err) {
      this._lastError = String(err);
      this._status = "Gerät konnte nicht gelöscht werden.";
      this._render();
    }
  }

  _selectCleanup(mode) {
    const checks = Array.from(this.shadowRoot.querySelectorAll(".cleanupCheck"));
    const devices = this._config.devices || {};
    checks.forEach((el) => {
      const id = el.value;
      const cfg = devices[id] || {};
      const state = this._stateFor(id);
      const text = `${id} ${cfg.name || ""}`.toLowerCase();
      if (mode === "all") el.checked = true;
      else if (mode === "none") el.checked = false;
      else if (mode === "test") el.checked = /test|tester|android_test|demo|old|alt/.test(text);
      else if (mode === "offline") el.checked = !this._isOnline(state.last_seen || cfg.last_seen);
    });
  }

  async _deleteSelectedDevices() {
    const ids = Array.from(this.shadowRoot.querySelectorAll(".cleanupCheck:checked")).map((el) => el.value);
    if (!ids.length) { this._status = "Keine Geräte markiert."; this._render(); return; }
    if (!confirm(this._adminLanguage() === "en" ? `${ids.length} device(s) delete completely?\n\n${ids.join(", ")}` : `${ids.length} Gerät(e) vollständig löschen?\n\n${ids.join(", ")}`)) return;
    try {
      await this._hass.callApi("POST", "ha_android_kiosk/cleanup", { devices: ids, remove_registry: true });
      ids.forEach((id) => { delete this._config.devices[id]; delete this._states[id]; });
      if (!this._config.devices[this._selected]) this._selected = Object.keys(this._config.devices)[0] || "";
      this._status = `${ids.length} Gerät(e) gelöscht.`;
      this._lastError = "";
      await this._load();
    } catch (err) {
      this._lastError = String(err);
      this._status = "Bereinigung fehlgeschlagen.";
      this._render();
    }
  }

  _saveDraftFromDom() {
    if (!this._selected || !this.shadowRoot.getElementById("device_id")) return;
    try { this._putDraftDevice(this._collectDevice()); } catch (err) { /* ignore half-filled forms */ }
  }

  _collectDevice() {
    const id = (this._value("device_id") || this._selected || "").trim();
    const current = this._config.devices[this._selected] || {};
    const d = this._mergeDevice(this._defaults(id), current);
    d.device_id = id;
    d.name = this._value("name", d.name || id) || id;
    d.browser = { ...d.browser, ...this._collectBrowser() };
    d.pages = this._exists("pagesList") ? this._collectPages() : d.pages;
    d.duration = Number(this._value("duration", d.duration || 15));
    d.auto_rotate = this._exists("auto_rotate") ? this._boolValue("auto_rotate") : d.auto_rotate;
    d.pause_on_touch = this._exists("pause_on_touch") ? this._boolValue("pause_on_touch") : d.pause_on_touch;
    d.touch_pause = Number(this._value("touch_pause", d.touch_pause || 30));
    d.overlays = { ...d.overlays, ...this._collectOverlays() };
    d.device_settings = { ...d.device_settings, ...this._collectDeviceSettings() };
    d.media = { ...d.media, ...this._collectMedia() };
    d.background = { ...d.background, ...this._collectBackground() };
    const allowedBoxes = Array.from(this.shadowRoot.querySelectorAll(".allowed"));
    if (allowedBoxes.length) d.allowed_functions = allowedBoxes.filter((el) => el.checked).map((el) => el.value);
    return d;
  }

  _collectBrowser() {
    const out = {};
    if (this._exists("browser_home_url")) out.home_url = this._value("browser_home_url");
    if (this._exists("browser_open_url")) out.start_url = this._value("browser_open_url");
    if (this._exists("browser_user_agent")) out.user_agent = this._value("browser_user_agent");
    if (this._exists("browser_reload_seconds")) out.reload_seconds = Number(this._value("browser_reload_seconds") || 0);
    if (this._exists("browser_fullscreen")) out.fullscreen = this._boolValue("browser_fullscreen");
    if (this._exists("browser_keep_screen_on")) out.keep_screen_on = this._boolValue("browser_keep_screen_on");
    if (this._exists("browser_external_auth")) out.external_auth = this._boolValue("browser_external_auth");
    if (this._exists("browser_hide_header")) out.hide_header = this._checked("browser_hide_header");
    if (this._exists("browser_wait_for_background")) out.wait_for_background = this._checked("browser_wait_for_background");
    if (this._exists("browser_transparent_webview")) out.transparent_webview = this._checked("browser_transparent_webview");
    if (this._exists("browser_force_mobile_viewport")) out.force_mobile_viewport = this._checked("browser_force_mobile_viewport");
    if (this._exists("browser_reveal_ms")) out.reveal_ms = Number(this._value("browser_reveal_ms") || 250);
    if (this._exists("browser_zoom_percent")) out.zoom_percent = this._clampZoom(Number(this._value("browser_zoom_percent") || 100));
    if (this._exists("browser_dashboard_language")) out.dashboard_language = this._value("browser_dashboard_language") || "de";
    if (this._exists("browser_settings_sync_seconds")) out.settings_sync_seconds = Number(this._value("browser_settings_sync_seconds") || 0);
    if (this._exists("browser_visual_watchdog_seconds")) out.visual_watchdog_seconds = Number(this._value("browser_visual_watchdog_seconds") || 0);
    if (this._exists("browser_webview_debug")) out.webview_debug = this._checked("browser_webview_debug");
    return out;
  }

  _collectPages() {
    return Array.from(this.shadowRoot.querySelectorAll(".pageItem")).map((row, index) => {
      const url = (row.querySelector(".pageUrl")?.value || "").trim();
      const durationText = (row.querySelector(".pageDuration")?.value || "").trim();
      const zoomText = (row.querySelector(".pageZoom")?.value || "").trim();
      const item = {
        name: (row.querySelector(".pageName")?.value || `Seite ${index + 1}`).trim(),
        url,
        path: url,
        duration: durationText ? Number(durationText) : undefined,
      };
      if (zoomText) {
        item.zoom_percent = this._clampZoom(Number(zoomText));
        item.zoom = item.zoom_percent;
      }
      return item;
    }).filter((p) => p.url);
  }

  _collectOverlays() {
    const out = {};
    const copy = [
      "ticker_text", "ticker_position", "ticker_color", "ticker_text_color", "ticker_separator",
      "banner_title", "banner_message", "banner_color", "banner_position", "banner_text_color",
      "alert_title", "alert_message", "alert_severity", "alert_mode", "alert_color", "alert_text_color", "alert_sound", "alert_tts_message",
      "clock_position",
      "weather_entity", "weather_title", "weather_position", "weather_layout",
      "camera_entity", "camera_url", "camera_title", "camera_mode", "camera_position",
      "status_title", "status_mode", "status_position"
    ];
    for (const id of copy) if (this._exists(id)) out[id] = this._value(id);
    const nums = ["ticker_height", "ticker_text_size", "ticker_font_size", "ticker_auto_hide_seconds", "banner_height", "banner_duration", "banner_text_size", "banner_font_size", "alert_duration", "alert_text_size", "alert_font_size", "status_duration", "weather_duration", "weather_refresh_seconds", "weather_width", "weather_height", "camera_duration", "camera_refresh_seconds", "camera_width", "camera_height"];
    nums.forEach((id) => { if (this._exists(id)) out[id] = Number(this._value(id) || 0); });
    if (this._exists("ticker_enabled")) out.ticker_enabled = this._boolValue("ticker_enabled");
    if (this._exists("ticker_fixed_messages")) out.ticker_fixed_messages = this._value("ticker_fixed_messages").split("\n").map((x) => x.trim()).filter(Boolean);
    if (this._exists("status_entities")) out.status_entities = this._value("status_entities").split(/\n|,/).map((x) => x.trim()).filter(Boolean);
    ["alert_require_ack", "alert_vibrate", "clock_enabled", "clock_show_date", "weather_show_forecast", "weather_show_pressure"].forEach((id) => { if (this._exists(id)) out[id] = id.startsWith("clock_") || id.startsWith("ticker_") ? this._boolValue(id) : this._checked(id); });
    return out;
  }

  _collectDeviceSettings() {
    const out = {};
    if (this._exists("brightness")) out.brightness = Number(this._value("brightness") || 0);
    if (this._exists("volume")) out.volume = Number(this._value("volume") || 0);
    if (this._exists("orientation")) out.orientation = this._value("orientation");
    ["brightness_system", "screen_power", "accelerometer_motion", "camera_front", "camera_motion", "light_sensor"].forEach((id) => { if (this._exists(id)) out[id] = this._checked(id); });
    out.camera_motion_facing = "front";
    return out;
  }

  _collectMedia() {
    const out = {};
    ["media_url", "sound_url", "sound_alias", "tts_message", "tts_language", "playlist_text"].forEach((id) => { if (this._exists(id)) out[id] = this._value(id); });
    if (this._exists("media_volume")) out.media_volume = Number(this._value("media_volume") || 70);
    if (this._exists("tts_volume")) out.tts_volume = Number(this._value("tts_volume") || 100);
    if (this._exists("audio_stream")) out.audio_stream = this._value("audio_stream") || "music";
    if (this._exists("force_max_volume")) out.force_max_volume = this._checked("force_max_volume");
    if (this._exists("audio_focus")) out.audio_focus = this._checked("audio_focus");
    if (this._exists("media_loop")) out.media_loop = this._checked("media_loop");
    return out;
  }


  _collectBackground() {
    const out = {};
    if (this._exists("bg_album")) out.album = this._value("bg_album") || "default";
    if (this._exists("bg_enabled")) out.enabled = this._boolValue("bg_enabled");
    if (this._exists("bg_interval")) out.interval = Number(this._value("bg_interval") || 20);
    if (this._exists("bg_random")) out.random = this._boolValue("bg_random");
    if (this._exists("bg_size")) out.size = this._value("bg_size") || "cover";
    if (this._exists("bg_position")) out.position = this._value("bg_position") || "center";
    if (this._exists("bg_repeat")) out.repeat = this._value("bg_repeat") || "no-repeat";
    if (this._exists("bg_opacity")) out.opacity = Number(this._value("bg_opacity") || 100);
    if (this._exists("bg_images")) out.images = this._value("bg_images").split("\n").map((x) => x.trim()).filter(Boolean);
    return out;
  }

  async _save(show = true) {
    const d = this._collectDevice();
    if (!d.device_id) { this._status = "Geräte-ID fehlt."; this._render(); return false; }
    this._putDraftDevice(d);
    await this._saveRaw(show ? "Gespeichert." : "");
    return true;
  }

  async _saveRaw(message = "Gespeichert.") {
    this._config.admin_language = this._adminLanguage();
    await this._hass.callApi("POST", "ha_android_kiosk/config", this._config);
    this._status = message;
    this._lastError = "";
    this._render();
  }

  async _command(command, payload) {
    const d = this._collectDevice();
    const device_id = d.device_id || this._selected;
    if (!device_id) { this._status = "Kein Gerät ausgewählt."; this._render(); return; }
    try {
      await this._hass.callApi("POST", "ha_android_kiosk/command", { device_id, command, payload });
      this._status = `Gesendet: ${command} an ${device_id}`;
      this._lastError = "";
    } catch (err) {
      this._lastError = String(err);
      this._status = "Befehl konnte nicht gesendet werden.";
    }
    this._render();
  }

  _backgroundPayload() {
    const b = this._collectDevice().background || {};
    return {
      enabled: b.enabled,
      album: b.album || "default",
      images: b.images || [],
      images_text: (b.images || []).join("\n"),
      interval: b.interval,
      random: b.random,
      size: b.size,
      position: b.position,
      repeat: b.repeat,
      opacity: b.opacity,
    };
  }

  _addBackgroundUrl(url) {
    if (!url) return;
    const d = this._collectDevice();
    d.background = d.background || {};
    const list = Array.isArray(d.background.images) ? [...d.background.images] : [];
    if (!list.includes(url)) list.push(url);
    d.background.images = list;
    d.background.enabled = true;
    this._putDraftDevice(d);
    this._render();
  }

  _addAllBackgrounds() {
    const d = this._collectDevice();
    d.background = d.background || {};
    const list = new Set(Array.isArray(d.background.images) ? d.background.images : []);
    const album = (d.background && d.background.album) || "default";
    for (const file of this._backgroundFiles || []) if ((file.album || "default") === album) list.add(file.url);
    d.background.images = Array.from(list);
    d.background.enabled = d.background.images.length > 0;
    this._putDraftDevice(d);
    this._render();
  }

  _bindBackgroundUpload() {
    const input = this.shadowRoot.getElementById("bg_upload");
    const zone = this.shadowRoot.getElementById("bg_dropzone");
    const update = () => this._updateUploadSummary();
    if (input) input.addEventListener("change", update);
    if (!zone || !input) return;
    ["dragenter", "dragover"].forEach((ev) => zone.addEventListener(ev, (event) => { event.preventDefault(); zone.classList.add("drag"); }));
    ["dragleave", "drop"].forEach((ev) => zone.addEventListener(ev, (event) => { event.preventDefault(); zone.classList.remove("drag"); }));
    zone.addEventListener("drop", (event) => {
      const files = event.dataTransfer && event.dataTransfer.files;
      if (files && files.length) { input.files = files; this._updateUploadSummary(); }
    });
    this._updateUploadSummary();
  }

  _updateUploadSummary() {
    const input = this.shadowRoot.getElementById("bg_upload");
    const summary = this.shadowRoot.getElementById("bg_upload_summary");
    if (!summary) return;
    if (!input || !input.files || !input.files.length) { summary.textContent = "Noch keine Dateien ausgewählt."; return; }
    const files = Array.from(input.files);
    const total = files.reduce((sum, file) => sum + (file.size || 0), 0);
    const names = files.slice(0, 8).map((file) => `• ${file.name}`).join("\n");
    summary.textContent = files.length + " Bild(er) ausgewählt, " + (total / 1024 / 1024).toFixed(1) + " MB\n" + names + (files.length > 8 ? "\n…" : "");
  }

  async _refreshBackgroundGallery() {
    try {
      const backgrounds = await this._hass.callApi("GET", "ha_android_kiosk/backgrounds");
      this._backgroundFiles = (backgrounds && backgrounds.files) || [];
      this._backgroundAlbums = (backgrounds && backgrounds.albums) || [{ id: "default", name: "default", count: this._backgroundFiles.length }];
    } catch (err) { this._lastError = String(err); }
  }

  async _uploadBackgrounds(forceSend = false) {
    const input = this.shadowRoot.getElementById("bg_upload");
    if (!input || !input.files || !input.files.length) { this._status = "Bitte zuerst Bilder auswählen."; this._render(); return; }
    try {
      const form = new FormData();
      form.append("album", this._value("bg_album", "default") || "default");
      Array.from(input.files).forEach((file) => form.append("files", file, file.name));
      const resp = await fetch("/api/ha_android_kiosk/backgrounds/upload", { method: "POST", headers: { Authorization: `Bearer ${this._hass.auth.data.access_token}` }, body: form });
      if (!resp.ok) throw new Error(await resp.text());
      const data = await resp.json();
      const uploaded = data.files || [];
      const addToSlideshow = this._checked("bg_upload_add") || forceSend;
      const enable = this._checked("bg_upload_enable") || forceSend;
      const autoSave = this._checked("bg_upload_save") || forceSend;
      const autoSend = this._checked("bg_upload_send") || forceSend;
      if (addToSlideshow) {
        const d = this._collectDevice();
        d.background = d.background || {};
        d.background.album = data.album || this._value("bg_album", "default") || "default";
        const list = new Set(Array.isArray(d.background.images) ? d.background.images : []);
        for (const file of uploaded) if (file.url) list.add(file.url);
        d.background.images = Array.from(list);
        if (enable) d.background.enabled = true;
        if (!d.background.interval) d.background.interval = 20;
        if (!d.background.size) d.background.size = "cover";
        if (!d.background.position) d.background.position = "center";
        if (!d.background.repeat) d.background.repeat = "no-repeat";
        if (typeof d.background.opacity === "undefined") d.background.opacity = 100;
        this._putDraftDevice(d);
      }
      await this._refreshBackgroundGallery();
      if (autoSave) await this._saveRaw("");
      if (autoSend) await this._command("background_slideshow", this._backgroundPayload());
      this._status = `${uploaded.length} Hintergrundbild(er) hochgeladen${addToSlideshow ? " und zur Slideshow hinzugefügt" : ""}${autoSend ? " und ans Gerät gesendet" : ""}.`;
      this._lastError = "";
      this._render();
    } catch (err) {
      this._lastError = String(err);
      this._status = "Upload fehlgeschlagen.";
      this._render();
    }
  }

  async _deleteBackgroundFile(name, album = "default") {
    if (!name) return;
    if (!confirm(this._adminLanguage() === "en" ? `Delete background image ${name} from album ${album || "default"}?` : `Hintergrundbild ${name} aus Album ${album || "default"} löschen?`)) return;
    try {
      const resp = await fetch(`/api/ha_android_kiosk/backgrounds/albums/${encodeURIComponent(album || "default")}/files/${encodeURIComponent(name)}`, {
        method: "DELETE",
        headers: { Authorization: `Bearer ${this._hass.auth.data.access_token}` },
      });
      if (!resp.ok) throw new Error(await resp.text());
      for (const id of Object.keys(this._config.devices || {})) {
        const bg = this._config.devices[id].background;
        if (bg && Array.isArray(bg.images)) bg.images = bg.images.filter((url) => !url.endsWith(`/${name}`));
      }
      await this._saveRaw("");
      await this._load();
    } catch (err) {
      this._lastError = String(err);
      this._status = "Bild konnte nicht gelöscht werden.";
      this._render();
    }
  }

  _filesForActiveAlbum() {
    const d = this._collectDevice();
    const album = (d.background && d.background.album) || "default";
    return (this._backgroundFiles || []).filter((file) => (file.album || "default") === album);
  }

  _setActiveAlbum(album) {
    const d = this._collectDevice();
    d.background = d.background || {};
    d.background.album = album || "default";
    this._putDraftDevice(d);
    this._render();
  }

  _useSelectedAlbumImages() {
    const d = this._collectDevice();
    d.background = d.background || {};
    d.background.images = this._filesForActiveAlbum().map((file) => file.url).filter(Boolean);
    d.background.enabled = d.background.images.length > 0;
    if (!d.background.interval) d.background.interval = 20;
    if (!d.background.size) d.background.size = "cover";
    if (!d.background.position) d.background.position = "center";
    if (!d.background.repeat) d.background.repeat = "no-repeat";
    this._putDraftDevice(d);
    this._render();
  }

  async _createAlbum() {
    const name = this._value("bg_new_album", "").trim();
    if (!name) { this._status = "Bitte einen Albumnamen eingeben."; this._render(); return; }
    try {
      await this._hass.callApi("POST", "ha_android_kiosk/backgrounds/albums", { album: name });
      await this._refreshBackgroundGallery();
      this._setActiveAlbum(name.replace(/[^A-Za-z0-9_.-]+/g, "_").replace(/^[._]+|[._]+$/g, "") || "default");
      this._status = `Album ${name} erstellt.`;
    } catch (err) { this._lastError = String(err); this._status = "Album konnte nicht erstellt werden."; this._render(); }
  }

  async _deleteAlbum(album) {
    if (!album || album === "default") return;
    if (!confirm(this._adminLanguage() === "en" ? `Delete album ${album} including all images?` : `Album ${album} inklusive aller Bilder löschen?`)) return;
    try {
      const resp = await fetch(`/api/ha_android_kiosk/backgrounds/albums/${encodeURIComponent(album)}`, {
        method: "DELETE",
        headers: { Authorization: `Bearer ${this._hass.auth.data.access_token}` },
      });
      if (!resp.ok) throw new Error(await resp.text());
      for (const id of Object.keys(this._config.devices || {})) {
        const bg = this._config.devices[id].background;
        if (bg && bg.album === album) bg.album = "default";
        if (bg && Array.isArray(bg.images)) bg.images = bg.images.filter((url) => !String(url).includes(`/backgrounds/${album}/`));
      }
      await this._saveRaw("");
      await this._refreshBackgroundGallery();
      this._status = `Album ${album} gelöscht.`;
      this._render();
    } catch (err) { this._lastError = String(err); this._status = "Album konnte nicht gelöscht werden."; this._render(); }
  }

  _applyRecommendedKioskPreset() {
    const setCheck = (id, value) => { const el = this.shadowRoot.getElementById(id); if (el) el.checked = value; };
    const setValue = (id, value) => { const el = this.shadowRoot.getElementById(id); if (el) el.value = value; };
    setCheck("browser_hide_header", true);
    setCheck("browser_wait_for_background", true);
    setCheck("browser_transparent_webview", true);
    setCheck("browser_force_mobile_viewport", false);
    setValue("browser_fullscreen", "true");
    setValue("browser_keep_screen_on", "true");
    setValue("browser_external_auth", "true");
    setValue("browser_reveal_ms", "250");
    setValue("browser_zoom_percent", "120");
    setValue("browser_dashboard_language", this._value("browser_dashboard_language", "de") || "de");
    setValue("browser_settings_sync_seconds", "300");
    setValue("browser_visual_watchdog_seconds", "45");
    this._status = "Empfohlenes Kiosk-Profil gesetzt. Bitte speichern oder direkt senden.";
    this._saveDraftFromDom();
    this._render();
  }

  _playlistPayload() {
    const lines = this._value("playlist_text", "").split("\n").map((x) => x.trim()).filter(Boolean);
    const first = this._value("media_url", "").trim();
    const playlist = first ? [first, ...lines] : lines;
    return { playlist, playlist_text: lines.join("\n"), volume: Number(this._value("media_volume") || 70), stream: this._value("audio_stream") || "music", loop: this._checked("media_loop"), force_max_volume: this._checked("force_max_volume"), audio_focus: this._checked("audio_focus") };
  }

  _audioPayload() {
    return {
      media_volume: Number(this._value("media_volume") || 100),
      tts_volume: Number(this._value("tts_volume") || 100),
      audio_stream: this._value("audio_stream") || "music",
      force_max_volume: this._checked("force_max_volume"),
      audio_focus: this._checked("audio_focus"),
    };
  }

  _browserPayload() {
    const b = this._collectDevice().browser || {};
    return {
      home_url: b.home_url,
      start_url: b.start_url,
      url: this._value("browser_open_url", b.start_url || b.home_url),
      user_agent: b.user_agent,
      fullscreen: b.fullscreen,
      keep_screen_on: b.keep_screen_on,
      reload_seconds: b.reload_seconds,
      webview_debug: b.webview_debug,
      external_auth: b.external_auth,
      hide_header: b.hide_header,
      wait_for_background: b.wait_for_background,
      transparent_webview: b.transparent_webview,
      force_mobile_viewport: b.force_mobile_viewport,
      reveal_ms: b.reveal_ms,
      zoom_percent: b.zoom_percent,
      dashboard_language: b.dashboard_language || "de",
      language: b.dashboard_language || "de",
      settings_sync_seconds: b.settings_sync_seconds,
      visual_watchdog_seconds: b.visual_watchdog_seconds,
    };
  }

  _pagePayload() {
    const d = this._collectDevice();
    return { pages: d.pages, duration: d.duration, auto_rotate: d.auto_rotate, pause_on_touch: d.pause_on_touch, touch_pause: d.touch_pause };
  }

  _tickerPayload() {
    const o = this._collectDevice().overlays || {};
    return { enabled: o.ticker_enabled, fixed_messages: o.ticker_fixed_messages || [], messages: o.ticker_fixed_messages || [], position: o.ticker_position, height: o.ticker_height, auto_hide_seconds: o.ticker_auto_hide_seconds, color: o.ticker_color, text_color: o.ticker_text_color, text_size: o.ticker_text_size, separator: o.ticker_separator || "   •   " };
  }

  _addPage(page) {
    const d = this._collectDevice();
    d.pages = d.pages || [];
    const url = page.url || "/lovelace/0";
    const entry = { name: page.name || `Seite ${d.pages.length + 1}`, url, path: url, duration: Number(page.duration || d.duration || 15) };
    if (page.zoom_percent || page.zoom) entry.zoom_percent = this._clampZoom(Number(page.zoom_percent || page.zoom));
    d.pages.push(entry);
    this._putDraftDevice(d);
    this._render();
  }

  _addManyPages() {
    const text = prompt(this._tr("Seiten einfügen, eine pro Zeile. Format: Name | /pfad | Dauer | Größe%\nBeispiel: Wohnzimmer | /lovelace/0 | 20 | 125"));
    if (!text) return;
    const d = this._collectDevice();
    d.pages = d.pages || [];
    for (const line of text.split("\n").map((x) => x.trim()).filter(Boolean)) {
      const parts = line.split("|").map((x) => x.trim());
      let name = parts[0], url = parts[1], duration = parts[2], zoom = parts[3];
      if (!url) { url = name; name = `Seite ${d.pages.length + 1}`; }
      const entry = { name, url, path: url, duration: duration ? Number(duration) : Number(d.duration || 15) };
      if (zoom) entry.zoom_percent = this._clampZoom(Number(zoom));
      d.pages.push(entry);
    }
    this._putDraftDevice(d);
    this._render();
  }

  _deletePage(index) {
    const d = this._collectDevice();
    d.pages.splice(index, 1);
    this._putDraftDevice(d);
    this._render();
  }

  _duplicatePage(index) {
    const d = this._collectDevice();
    const copy = { ...(d.pages[index] || {}) };
    copy.name = `${copy.name || "Seite"} Kopie`;
    d.pages.splice(index + 1, 0, copy);
    this._putDraftDevice(d);
    this._render();
  }

  _movePage(index, dir) {
    const d = this._collectDevice();
    const target = index + dir;
    if (target < 0 || target >= d.pages.length) return;
    const [item] = d.pages.splice(index, 1);
    d.pages.splice(target, 0, item);
    this._putDraftDevice(d);
    this._render();
  }

  _testPage(index) {
    const p = this._collectPages()[index];
    if (p && p.url) this._command("show_page", { url: p.url, zoom_percent: p.zoom_percent || undefined });
  }

  _positionOptions(selected) {
    return ["top", "bottom", "center", "fullscreen", "top-left", "top-right", "bottom-left", "bottom-right"].map((v) => `<option value="${v}" ${selected === v ? "selected" : ""}>${v}</option>`).join("");
  }

  _options(values, selected) {
    return values.map((v) => `<option value="${v}" ${selected === v ? "selected" : ""}>${v}</option>`).join("");
  }

  _toRelative(url) {
    if (!url) return "";
    try {
      const parsed = new URL(url, window.location.origin);
      parsed.searchParams.delete("external_auth");
      return `${parsed.pathname}${parsed.search || ""}${parsed.hash || ""}`;
    } catch (_err) {
      return url;
    }
  }

  _clampZoom(value) {
    const n = Number(value);
    if (!Number.isFinite(n) || n <= 0) return 100;
    return Math.max(50, Math.min(200, Math.round(n)));
  }

  _localAdminLanguage() {
    try { return localStorage.getItem("ha_android_kiosk_admin_language") || ""; } catch (_err) { return ""; }
  }

  _adminLanguage() {
    return this._normalizeLanguage(this._uiLanguage || (this._config && this._config.admin_language) || this._localAdminLanguage() || "de");
  }

  _normalizeLanguage(lang) {
    const value = String(lang || "de").toLowerCase().trim();
    return value.startsWith("en") ? "en" : "de";
  }

  async _copyYaml(targetId) {
    const el = this.shadowRoot.getElementById(targetId);
    if (!el) return;
    try {
      await navigator.clipboard.writeText(el.value || el.textContent || "");
      this._status = "YAML wurde kopiert.";
    } catch (err) {
      el.focus();
      el.select();
      this._status = "Bitte YAML manuell kopieren.";
    }
    this._render();
  }

  async _setAdminLanguage(lang) {
    const next = this._normalizeLanguage(lang);
    this._uiLanguage = next;
    this._config = this._config || { devices: {} };
    this._config.devices = this._config.devices || {};
    this._config.admin_language = next;
    try { localStorage.setItem("ha_android_kiosk_admin_language", next); } catch (_err) {}
    this._status = next === "en" ? "Admin interface switched to English." : "Admin-Oberfläche auf Deutsch umgestellt.";
    try {
      await this._hass.callApi("POST", "ha_android_kiosk/config", this._config);
      this._lastError = "";
    } catch (err) {
      this._lastError = String(err);
    }
    this._render();
  }

  _tr(text) {
    if (this._adminLanguage() !== "en") return text;
    return this._translateText(text);
  }

  _translateAdminUi() {
    if (this._adminLanguage() !== "en") return;
    const translateNode = (node) => {
      if (!node) return;
      if (node.nodeType === Node.TEXT_NODE) {
        const original = node.nodeValue;
        const trimmed = original.trim();
        if (trimmed) {
          const translated = this._translateText(trimmed);
          if (translated !== trimmed) node.nodeValue = original.replace(trimmed, translated);
        }
        return;
      }
      if (node.nodeType !== Node.ELEMENT_NODE) return;
      if (["SCRIPT", "STYLE", "CODE"].includes(node.tagName)) return;
      for (const attr of ["placeholder", "title", "aria-label"]) {
        if (node.hasAttribute(attr)) {
          const value = node.getAttribute(attr);
          const translated = this._translateText(value);
          if (translated !== value) node.setAttribute(attr, translated);
        }
      }
      Array.from(node.childNodes).forEach(translateNode);
    };
    translateNode(this.shadowRoot);
  }

  _translateText(text) {
    const value = String(text ?? "");
    const map = HaAndroidKioskPanel.I18N_EN;
    if (Object.prototype.hasOwnProperty.call(map, value)) return map[value];
    const simple = value.replace(/\s+/g, " ").trim();
    if (Object.prototype.hasOwnProperty.call(map, simple)) return map[simple];
    let m;
    if ((m = simple.match(/^Seite (\d+)$/))) return `Page ${m[1]}`;
    if ((m = simple.match(/^Seite (\d+) Kopie$/))) return `Page ${m[1]} copy`;
    if ((m = simple.match(/^Gesendet: (.+) an (.+)$/))) return `Sent: ${m[1]} to ${m[2]}`;
    if ((m = simple.match(/^Gerät (.+) wurde vollständig gelöscht\.$/))) return `Device ${m[1]} was fully deleted.`;
    if ((m = simple.match(/^(\d+) Gerät\(e\) gelöscht\.$/))) return `${m[1]} device(s) deleted.`;
    if ((m = simple.match(/^Album (.+) erstellt\.$/))) return `Album ${m[1]} created.`;
    if ((m = simple.match(/^Album (.+) gelöscht\.$/))) return `Album ${m[1]} deleted.`;
    if ((m = simple.match(/^(\d+) Bild\(er\) ausgewählt, (.+)$/))) return `${m[1]} image(s) selected, ${m[2]}`;
    if ((m = simple.match(/^(.+) Kopie$/))) return `${m[1]} copy`;
    return value;
  }

  _exists(id) { return !!this.shadowRoot.getElementById(id); }
  _value(id, fallback = "") { const el = this.shadowRoot.getElementById(id); return el ? el.value : fallback; }
  _checked(id) { const el = this.shadowRoot.getElementById(id); return !!(el && el.checked); }
  _boolValue(id) { return this._value(id) === "true"; }
  _isOnline(value) {
    if (!value) return false;
    const t = Date.parse(value);
    return Number.isFinite(t) && (Date.now() - t) < 180000;
  }
  _escape(value) {
    return String(value ?? "").replace(/[&<>'"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" }[c]));
  }
}
HaAndroidKioskPanel.I18N_EN = {
  'Android Kiosk Admin': 'Android Kiosk Admin',
  'Konfiguriere Android-Kiosk-Screens, Browser, Rotation und Hintergrund-Alben. Webinterface v17.17: Bedienfunktionen wie Meldungen, Overlays, Module, Gerät und Medien sind jetzt als Automation-&-Testbereich mit kopierfertigen YAML-Beispielen organisiert, damit der echte Betrieb sauber über Home-Assistant-Automationen läuft.': 'Configure Android kiosk screens, browser, rotation and background albums. Web interface v17.17: control functions such as messages, overlays, modules, device actions and media are now organized as an Automation & Tests area with copy-ready YAML examples, so real operation runs cleanly through Home Assistant automations.',
  'Admin-Sprache': 'Admin language',
  'Deutsch': 'German',
  'English': 'English',
  'Neu laden': 'Reload',
  'Speichern': 'Save',
  'Profil anwenden': 'Apply profile',
  'Geräte': 'Devices',
  'Noch kein Gerät registriert. Starte die Android-App und melde sie an.': 'No device registered yet. Start the Android app and sign it in.',
  'Gerät hinzufügen': 'Add device',
  'Vollständig löschen': 'Delete completely',
  'Testgeräte bereinigen': 'Clean test devices',
  'Status': 'Status',
  'Kein Gerät ausgewählt': 'No device selected',
  'Wähle links ein Gerät oder füge eines manuell hinzu.': 'Select a device on the left or add one manually.',
  'Gerät': 'Device',
  'Online': 'Online',
  'Letzter Status': 'Last status',
  'Zuletzt gesehen': 'Last seen',
  'Aktuelle URL': 'Current URL',
  'Letzter Befehl': 'Last command',
  'Helligkeit': 'Brightness',
  'Lautstärke': 'Volume',
  'Hintergrund': 'Background',
  'HA-Navigation': 'HA navigation',
  'Seiten-Zoom': 'Page zoom',
  'Sprache': 'Language',
  'ja': 'yes',
  'nein': 'no',
  'bereit': 'ready',
  'lädt': 'loading',
  'lädt/warte': 'loading/waiting',
  'aus': 'off',
  'ein': 'on',
  'sichtbar': 'visible',
  'ausgeblendet': 'hidden',
  'mobil': 'mobile',
  'auto': 'auto',
  'Gerätename': 'Device name',
  'Geräte-ID': 'Device ID',
  'Die Geräte-ID muss zur ID in der Android-App passen. Name ist frei wählbar.': 'The device ID must match the ID shown in the Android app. The name can be chosen freely.',
  'Seiten': 'Pages',
  'Browser': 'Browser',
  'Meldungen': 'Messages',
  'Module': 'Modules',
  'Medien/TTS': 'Media/TTS',
  'Medien / TTS': 'Media / TTS',
  'Berechtigungen': 'Permissions',
  'Bereinigen': 'Cleanup',
  'Screens': 'Screens',
  'Automation & Tests': 'Automation & tests',
  'Automation first': 'Automation first',
  'lokal senden': 'send locally',
  'kopieren': 'copy',
  'Dieser Bereich ist bewusst kein Dauer-Bedien-Dashboard mehr. Er dient nur zum Testen von Anzeige-Bausteinen und zum Kopieren fertiger YAML-Automationen. Der echte Betrieb soll danach über Home-Assistant-Automationen, Skripte und Sensoren laufen.': 'This area is intentionally no longer a permanent control dashboard. It is only for testing display blocks and copying ready-made YAML automations. Real operation should then run through Home Assistant automations, scripts and sensors.',
  'Alle Test-Overlays schließen': 'Close all test overlays',
  'Meldungen testen': 'Test messages',
  'Module testen': 'Test modules',
  'Medien/TTS testen': 'Test media/TTS',
  'Geräte-Testbefehle': 'Device test commands',
  'Kopierfertige YAML-Automationen': 'Copy-ready YAML automations',
  'Dashboard-Seiten': 'Dashboard pages',
  'Füge Seiten per Formular hinzu. URL kann relativ sein, z. B. /lovelace/0. Pro Seite kannst du optional eine Größe in Prozent setzen, z. B. 120 für größere Widgets.': 'Add pages with the form. The URL can be relative, for example /lovelace/0. You can optionally set a size percentage per page, for example 120 for larger widgets.',
  'Energie': 'Energy',
  'Aktuelle Seite': 'Current page',
  'aktuelle URL': 'current URL',
  'Seite': 'Page',
  'Name': 'Name',
  'URL / Pfad': 'URL / path',
  'Dauer': 'Duration',
  'Standard': 'Default',
  'Größe %': 'Size %',
  'Global': 'Global',
  'Duplizieren': 'Duplicate',
  'Öffnen': 'Open',
  'Löschen': 'Delete',
  'Füge Seiten per Formular hinzu. URL kann relativ sein, z. B.': 'Add pages with the form. The URL can be relative, for example',
  '. Pro Seite kannst du optional eine Größe in Prozent setzen, z. B. 120 für größere Widgets.': '. You can optionally set a size percentage per page, for example 120 for larger widgets.',
  '+ Energie': '+ Energy',
  '+ aktuelle URL': '+ current URL',
  'Seite hinzufügen': 'Add page',
  'Mehrere aus Liste einfügen': 'Paste multiple from list',
  'Rotation ans Gerät senden': 'Send rotation to device',
  'Rotation': 'Rotation',
  'Standard-Dauer pro Seite': 'Default duration per page',
  'Automatisch rotieren': 'Rotate automatically',
  'Ja': 'Yes',
  'Nein': 'No',
  'Bei Touch pausieren': 'Pause on touch',
  'Touch-Pause Sekunden': 'Touch pause seconds',
  'Vorherige Seite': 'Previous page',
  'Nächste Seite': 'Next page',
  'Rotation pausieren': 'Pause rotation',
  'Rotation fortsetzen': 'Resume rotation',
  'Name': 'Name',
  'URL / Pfad': 'URL / path',
  'Dauer': 'Duration',
  'Größe %': 'Size %',
  'Standard': 'Default',
  'Global': 'Global',
  'Duplizieren': 'Duplicate',
  'Öffnen': 'Open',
  'Löschen': 'Delete',
  'Webbrowser & Kiosk-Modus': 'Web browser & kiosk mode',
  'Optimiert für dein Android-6-Tablet und Android-9-Smartphone: Hintergrund zuerst, transparente WebView, HA-Navigation per eigenem Kiosk-Injector ohne HACS ausblenden und optional mobile Viewport-Logik.': 'Optimized for your Android 6 tablet and Android 9 phone: background first, transparent WebView, HA navigation hidden by the built-in kiosk injector without HACS, and optional mobile viewport logic.',
  'Empfohlenes Kiosk-Profil setzen': 'Set recommended kiosk profile',
  'Navigation': 'Navigation',
  'Viewport': 'Viewport',
  'Größe': 'Size',
  'Selbst-Sync': 'Self-sync',
  'Start & Navigation': 'Start & navigation',
  'Home-/Startseite': 'Home/start page',
  'Jetzt öffnen': 'Open now',
  'Auto-Reload Sekunden': 'Auto reload seconds',
  'Vollbild': 'Fullscreen',
  'Display wach halten': 'Keep display awake',
  'Browser-Auto-Login': 'Browser auto-login',
  'Darstellung & Ladeverhalten': 'Display & loading behavior',
  'Home-Assistant-Navigation ausblenden (ohne HACS / eigener Kiosk-Injector)': 'Hide Home Assistant navigation (without HACS / built-in kiosk injector)',
  'Hintergrund zuerst laden, dann Dashboard einblenden': 'Load background first, then reveal dashboard',
  'WebView transparent über Hintergrund legen': 'Place WebView transparent over background',
  'Tablet als mobile Ansicht rendern': 'Render tablet as mobile view',
  'WebView-Debugging aktivieren': 'Enable WebView debugging',
  'Einblenddauer Dashboard ms': 'Dashboard fade-in ms',
  'Standard-Seitengröße %': 'Default page size %',
  'User-Agent optional': 'Optional user agent',
  'Leer = Standard-WebView': 'Empty = default WebView',
  'Dashboard-Sprache': 'Dashboard language',
  'Selbst-Sync alle Sekunden': 'Self-sync every seconds',
  'Recovery/Watchdog Sekunden': 'Recovery/watchdog seconds',
  'Letzter Geräte-Sync': 'Last device sync',
  'Dashboard-Sprache:': 'Dashboard language:',
  'Deutsch oder Englisch wird an die Android-App gesendet. Die App setzt Accept-Language, schreibt die Sprache in den WebView-Speicher und lädt Home Assistant bei einer Änderung einmal neu.': 'German or English is sent to the Android app. The app sets Accept-Language, stores the language in WebView storage and reloads Home Assistant once when it changes.',
  'Selbst-Sync:': 'Self-sync:',
  'Das Tablet lädt Rotation, Hintergrund und Browser-Einstellungen selbständig neu. 300 Sekunden ist empfohlen; 0 schaltet den zyklischen Abruf aus. Der lokale Cache startet auch, wenn Home Assistant nach langer Pause noch nicht sofort erreichbar ist.': 'The tablet reloads rotation, background and browser settings by itself. 300 seconds is recommended; 0 disables periodic polling. The local cache also starts when Home Assistant is not immediately reachable after a long pause.',
  'Größe/Zoom:': 'Size/zoom:',
  '100% ist normal. 115–130% macht Karten und Widgets größer. In der Seitenliste kannst du für jede Seite einen eigenen Wert setzen; leer bedeutet: Standard-Seitengröße verwenden. Ab dieser Version bleibt der native Hintergrund auch bei 120%+ sichtbar.': '100% is normal. 115-130% makes cards and widgets larger. In the page list you can set a custom value for every page; empty means default page size. The native background stays visible even at 120%+.',
  'Für das SuitePad/RK3288 empfohlen:': 'Recommended for SuitePad/RK3288:',
  'Navigation ausblenden nutzt einen eingebauten Kiosk-Injector ohne HACS. Mobile Viewport nur einschalten, wenn Home Assistant auf dem Tablet weiterhin links eine Sidebar erzwingt.': 'Navigation hiding uses a built-in kiosk injector without HACS. Enable mobile viewport only if Home Assistant still forces a left sidebar on the tablet.',
  'URL öffnen': 'Open URL',
  'Seite neu laden': 'Reload page',
  'Browser-Konfiguration senden': 'Send browser configuration',
  'Sprache anwenden': 'Apply language',
  'Einstellungen vom Tablet abrufen lassen': 'Let tablet fetch settings',
  'Anzeige reparieren': 'Repair display',
  'Meldungen & Overlays': 'Messages & overlays',
  'Live-Ticker Text': 'Live ticker text',
  'Feste Tickerliste, eine Meldung pro Zeile': 'Fixed ticker list, one message per line',
  'Position': 'Position',
  'Höhe px': 'Height px',
  'Auto ausblenden Sekunden': 'Auto-hide seconds',
  'Live-Ticker senden': 'Send live ticker',
  'Ticker-Konfiguration senden': 'Send ticker configuration',
  'Ticker löschen': 'Clear ticker',
  'Toast / Banner / Alert': 'Toast / banner / alert',
  'Titel': 'Title',
  'Meldung': 'Message',
  'Farbe': 'Color',
  'Severity': 'Severity',
  'Dauer Sekunden': 'Duration seconds',
  'Bestätigung verlangen': 'Require acknowledgement',
  'Toast senden': 'Send toast',
  'Banner senden': 'Send banner',
  'Vollbild-Alert senden': 'Send fullscreen alert',
  'Meldungen schließen': 'Close messages',
  'Zusatzmodule': 'Additional modules',
  'Uhr aktiv': 'Clock enabled',
  'Uhr-Position': 'Clock position',
  'Datum anzeigen': 'Show date',
  'Weather-Entity': 'Weather entity',
  'Wetter-Position': 'Weather position',
  'HA Kamera-Entity': 'HA camera entity',
  'Kamera-/Bild-URL': 'Camera/image URL',
  'Kamera-Overlay': 'Camera overlay',
  'Uhr anzeigen': 'Show clock',
  'Uhr ausblenden': 'Hide clock',
  'Wetter anzeigen': 'Show weather',
  'Kamera anzeigen': 'Show camera',
  'Kamera ausblenden': 'Hide camera',
  'Hintergrund-Slideshow': 'Background slideshow',
  'Hintergrund-Alben': 'Background albums',
  'Lege Alben wie': 'Create albums like',
  'Küche': 'Kitchen',
  'Ferien': 'Holiday',
  'Nachtmodus': 'Night mode',
  'an. Pro Gerät wählst du, welches Album als Slideshow läuft.': '. For each device you choose which album is used as slideshow.',
  'Neues Album': 'New album',
  'Hinzufügen': 'Add',
  'Galerie neu laden': 'Reload gallery',
  'Gewähltes Album verwenden': 'Use selected album',
  'Album löschen': 'Delete album',
  'Bilder hochladen': 'Upload images',
  'Du kannst mehrere Bilder gleichzeitig auswählen oder hier hineinziehen. Sie werden im gewählten Album gespeichert.': 'You can select multiple images at once or drag them here. They are saved in the selected album.',
  'oder Dateien auswählen': 'or select files',
  'Noch keine Dateien ausgewählt.': 'No files selected yet.',
  'Hochgeladene Bilder direkt zur Slideshow hinzufügen': 'Add uploaded images directly to slideshow',
  'Hochladen': 'Upload',
  'Hochladen + verwenden + senden': 'Upload + use + send',
  'Galerie:': 'Gallery:',
  'Aktiv': 'Active',
  'Album': 'Album',
  'Intervall Sekunden': 'Interval seconds',
  'Zufällig': 'Random',
  'Bildgröße': 'Image size',
  'Wiederholung': 'Repeat',
  'Deckkraft %': 'Opacity %',
  'Bild-URLs, eine pro Zeile': 'Image URLs, one per line',
  'Slideshow aktivieren': 'Enable slideshow',
  'Slideshow ans Gerät senden': 'Send slideshow to device',
  'Hintergrund entfernen': 'Remove background',
  'Empfohlen:': 'Recommended:',
  'Entferne den statischen': 'Remove the static',
  '-Block aus deiner Dashboard-Seite. Die Slideshow kommt dann aus dem gewählten Album.': ' block from your dashboard page. The slideshow then comes from the selected album.',
  'Die App setzt den Hintergrund über dem geladenen Home-Assistant-Dashboard. Du musst in der Lovelace-YAML keinen einzelnen statischen Hintergrund mehr eintragen.': 'The app places the background behind the loaded Home Assistant dashboard. You no longer need to configure one static background in Lovelace YAML.',
  'Gerätesteuerung': 'Device control',
  'Helligkeit %': 'Brightness %',
  'Systemhelligkeit': 'System brightness',
  'Lautstärke %': 'Volume %',
  'Ausrichtung': 'Orientation',
  'Display an': 'Screen on',
  'Sensorstatus': 'Sensor status',
  'Bewegungssensor': 'Motion sensor',
  'Frontkamera aktiv': 'Front camera active',
  'Frontkamera-Bewegungserkennung': 'Front camera motion detection',
  'Lichtsensor melden': 'Report light sensor',
  'Helligkeit setzen': 'Set brightness',
  'Lautstärke setzen': 'Set volume',
  'Ausrichtung setzen': 'Set orientation',
  'Schwarzbild': 'Black screen',
  'Vibrieren': 'Vibrate',
  'Motion an': 'Motion on',
  'Motion aus': 'Motion off',
  'Frontkamera an': 'Front camera on',
  'Frontkamera aus': 'Front camera off',
  'Frontkamera-Motion an': 'Front camera motion on',
  'Frontkamera-Motion aus': 'Front camera motion off',
  'Kamera aus': 'Camera off',
  'Android-Einstellungen öffnen': 'Open Android settings',
  'App neu starten': 'Restart app',
  'Frontkamera-Bewegung:': 'Front camera motion:',
  'Es wird kein Bild an Home Assistant übertragen. Die App wertet nur Helligkeitsänderungen der Frontkamera lokal aus und meldet Bewegung als Sensor.': 'No image is sent to Home Assistant. The app only evaluates front-camera brightness changes locally and reports motion as a sensor.',
  'Lichtsensor': 'Light sensor',
  'Lichtstärke': 'Light level',
  'Kamera-Wert': 'Camera value',
  'Audio / Medien': 'Audio / media',
  'Media-URL': 'Media URL',
  'Sound-URL': 'Sound URL',
  'Sound-Alias': 'Sound alias',
  'Lautstärke für Medien %': 'Media volume %',
  'TTS-Lautstärke %': 'TTS volume %',
  'Android-Audiokanal': 'Android audio stream',
  'Sprache immer auf Max': 'Voice always max',
  'Audio-Fokus anfordern': 'Request audio focus',
  'Wiederholen / Loop': 'Repeat / loop',
  'Lautsprecher-Boost:': 'Speaker boost:',
  'Die App kann den Android-Audiokanal für Sprache/Medien auf Maximum setzen, Audio-Fokus anfordern und optional den Alarm-Kanal verwenden. Mehr als die physische Lautsprecherleistung des Tablets ist softwareseitig nicht möglich, aber bei vielen RK3288-Tablets ist der Alarm-Kanal lauter als Musik.': "The app can set the Android audio stream for speech/media to maximum, request audio focus and optionally use the alarm stream. Software cannot exceed the tablet's physical speaker capability, but on many RK3288 tablets the alarm stream is louder than music.",
  'Playlist / Warteschlange, eine URL pro Zeile': 'Playlist / queue, one URL per line',
  'Media abspielen': 'Play media',
  'Sound abspielen': 'Play sound',
  'Playlist starten': 'Start playlist',
  '⏮ Zurück': '⏮ Previous',
  '⏸ Pause': '⏸ Pause',
  '▶ Weiter': '▶ Continue',
  '⏭ Weiter': '⏭ Next',
  '⏹ Stop': '⏹ Stop',
  'Seek zu Sekunde': 'Seek to second',
  'Mute': 'Mute',
  'Loop direkt setzen': 'Set loop directly',
  'Springen': 'Seek',
  'Mute an': 'Mute on',
  'Mute aus': 'Mute off',
  'Loop setzen': 'Set loop',
  'Medienlautstärke setzen': 'Set media volume',
  'Audio-Boost senden': 'Send audio boost',
  'TTS': 'TTS',
  'TTS Sprache': 'TTS language',
  'Beispiele': 'Examples',
  'Text wählen...': 'Choose text...',
  'Achtung. Jemand steht vor der Haustür.': 'Attention. Someone is at the front door.',
  'Die Waschmaschine ist fertig.': 'The washing machine is finished.',
  'Bitte Fenster schließen.': 'Please close the window.',
  'Test. Diese Durchsage sollte jetzt lauter sein.': 'Test. This announcement should now be louder.',
  'TTS Text': 'TTS text',
  'TTS sprechen': 'Speak TTS',
  'Lauten Test sprechen': 'Speak loud test',
  'TTS stoppen': 'Stop TTS',
  'Alte Testgeräte bereinigen': 'Clean old test devices',
  'Löscht Geräte aus der Kiosk-Konfiguration und entfernt zusätzlich Entity-Registry- und Device-Registry-Einträge dieser Integration. Danach Home Assistant neu laden oder Integration neu starten, falls eine bereits geladene Entity noch sichtbar bleibt.': 'Deletes devices from the kiosk configuration and also removes entity registry and device registry entries for this integration. Reload Home Assistant or restart the integration if an already-loaded entity remains visible.',
  'Alle markieren': 'Select all',
  'Testgeräte markieren': 'Select test devices',
  'Offline markieren': 'Select offline',
  'Auswahl leeren': 'Clear selection',
  'Keine gespeicherten Geräte vorhanden.': 'No saved devices found.',
  'Markierte vollständig löschen': 'Delete selected completely',
  'Aktuelles Gerät vollständig löschen': 'Delete current device completely',
  'Berechtigungen für Home Assistant': 'Permissions for Home Assistant',
  'Nur aktivierte Funktionen dürfen von Home Assistant an dieses Gerät gesendet werden.': 'Only enabled functions may be sent from Home Assistant to this device.',
  'Display / Seiten': 'Display / pages',
  'Meldungen / Alerts': 'Messages / alerts',
  'Uhr': 'Clock',
  'Wetter': 'Weather',
  'Vibration': 'Vibration',
  'Kamera allgemein': 'Camera general',
  'Frontkamera': 'Front camera',
  'Frontkamera-Bewegung': 'Front camera motion',
  'Mikrofon': 'Microphone',
  'Alles erlauben': 'Allow all',
  'Nur Anzeige/Overlays': 'Only display/overlays',
  'Geräte-ID fehlt.': 'Device ID is missing.',
  'Gespeichert.': 'Saved.',
  'Kein Gerät ausgewählt.': 'No device selected.',
  'Befehl konnte nicht gesendet werden.': 'Command could not be sent.',
  'Bitte zuerst Bilder auswählen.': 'Please select images first.',
  'Bitte einen Albumnamen eingeben.': 'Please enter an album name.',
  'Album konnte nicht erstellt werden.': 'Album could not be created.',
  'Album konnte nicht gelöscht werden.': 'Album could not be deleted.',
  'Bild konnte nicht gelöscht werden.': 'Image could not be deleted.',
  'Empfohlenes Kiosk-Profil gesetzt. Bitte speichern oder direkt senden.': 'Recommended kiosk profile set. Save or send it directly.',
  'Keine Geräte markiert.': 'No devices selected.',
  'Bereinigung fehlgeschlagen.': 'Cleanup failed.',
  'Gerät konnte nicht gelöscht werden.': 'Device could not be deleted.',
  'Geräte-ID aus der Android-App:': 'Device ID from the Android app:',
  'Seiten einfügen, eine pro Zeile. Format: Name | /pfad | Dauer | Größe%\nBeispiel: Wohnzimmer | /lovelace/0 | 20 | 125': 'Paste pages, one per line. Format: Name | /path | duration | size%\nExample: Living room | /lovelace/0 | 20 | 125',
  'Aktuelle Seite': 'Current page',
  'Energie': 'Energy',
  'Wohnzimmer': 'Living room',
  'Kopie': 'copy',
  'Wartungsfunktionen für Geräte, die lange im Kiosk-Betrieb laufen: Status anfordern, Gerät sichtbar identifizieren, WebView-Cache leeren oder die App neu starten.': 'Maintenance functions for devices running in kiosk mode for a long time: request status, visibly identify the device, clear the WebView cache or restart the app.',
  'Gerätestatus anfordern': 'Request device status',
  'Gerät identifizieren': 'Identify device',
  'WebView-Cache leeren': 'Clear WebView cache',
  'Overlays löschen': 'Clear overlays',
  'Android-App-Einstellungen öffnen': 'Open Android app settings',
  'App neu starten': 'Restart app',
  'verfügbar': 'available',
  'nicht verfügbar / noch nicht gemeldet': 'not available / not reported yet',
  'erkannt': 'detected',
  'Bildschirm an': 'Screen on',
  'Bewegungssensor an': 'Motion sensor on',
  'Bewegungssensor aus': 'Motion sensor off',
  'App-Aktionen': 'App actions',
  'Lege Alben wie': 'Create albums such as',
  'Bad': 'Bathroom',
  'Aktives Album für dieses Gerät': 'Active album for this device',
  'z. B. Bad, Küche, Ferien': 'e.g. Bathroom, Kitchen, Holiday',
  'Album erstellen': 'Create album',
  'Bildliste, eine URL pro Zeile': 'Image list, one URL per line',
  'Bilder ins Album hochladen': 'Upload images to album',
  'Bilder hier ablegen': 'Drop images here',
  'Jetzt öffnen': 'Open now',
  'Danach automatisch speichern': 'Save automatically afterwards',
  'Danach direkt ans ausgewählte Gerät senden': 'Send directly to selected device afterwards',
  'Alle Bilder aus Album hinzufügen': 'Add all images from album',
  'Noch keine Bilder in diesem Album hochgeladen.': 'No images uploaded in this album yet.',
  'Aktuelle Seite': 'Current page',
  'Display an': 'Display on',
  'Bildschirm an': 'Screen on',
  'Ausrichtung setzen': 'Set orientation',
  'Schwarzbild': 'Black screen',
  'Kamera aus': 'Camera off',
  'Kamera-Bewegung': 'Camera motion',
  'Kamera-Wert': 'Camera value',
  'Noch nicht verfügbar': 'Not available yet',
  'Audio-Fokus anfordern': 'Request audio focus',
  'Media abspielen': 'Play media',
  'Dauer Sekunden': 'Duration seconds',
  'Auto ausblenden Sekunden': 'Auto-hide seconds',
  'Ticker': 'Ticker',
  'Ticker löschen': 'Clear ticker',
  'Banner senden': 'Send banner',
  'Datum anzeigen': 'Show date',
  'Kamera': 'Camera',
  'Kamera-Overlay': 'Camera overlay',
  'Kamera anzeigen': 'Show camera',
  'Wetter-Position': 'Weather position',
  'Wetter anzeigen': 'Show weather',
  'Wiederholung': 'Repeat',
  'Deckkraft %': 'Opacity %',
  'Bildgröße': 'Image size',
  'Meldung': 'Message',
  'Live-Ticker senden': 'Send live ticker',
  'Bestätigung verlangen': 'Require acknowledgement',
  'Meldungen schließen': 'Close messages',
  'Kamera ausblenden': 'Hide camera',
  'Gerätestatus anfordern': 'Request device status',
  'Gerät identifizieren': 'Identify device',
  'WebView-Cache leeren': 'Clear WebView cache',
  'Overlays löschen': 'Clear overlays',
  'Android-App-Einstellungen öffnen': 'Open Android app settings',
  'App neu starten': 'Restart app',
  'Hochgeladen': 'Uploaded',
  'und zur Slideshow hinzugefügt': 'and added to slideshow',
  'und ans Gerät gesendet': 'and sent to device',
  'Hintergrundbild(er) hochgeladen': 'background image(s) uploaded',
  'Hintergrundbild': 'Background image',
  'aus Album': 'from album',
  'inklusive aller Bilder löschen?': 'including all images?',
  'Das entfernt auch die 27 Entitäten und den Geräte-Registry-Eintrag dieser Integration.': 'This also removes the 27 entities and the device registry entry for this integration.',

  'YAML-Beispiele': 'YAML examples',
  'Diese Beispiele kannst du direkt in Home Assistant kopieren. Ersetze nur die Entity-IDs durch deine eigenen Sensoren, Kameras und Binary-Sensoren.': 'You can copy these examples directly into Home Assistant. Only replace the entity IDs with your own sensors, cameras and binary sensors.',
  'Türklingel mit Kamera': 'Doorbell with camera',
  'Status-Overlay aus Sensoren': 'Status overlay from sensors',
  'Morgen-Sequenz mit Wetter': 'Morning sequence with weather',
  'Meldung mit TTS': 'Message with TTS',
  'Ticker-Textgröße': 'Ticker text size',
  'Ticker-Hintergrund': 'Ticker background',
  'Ticker-Textfarbe': 'Ticker text color',
  'Banner-Position': 'Banner position',
  'Banner-Textgröße': 'Banner text size',
  'Wetter-Overlay': 'Weather overlay',
  'Layout': 'Layout',
  'Breite': 'Width',
  'Höhe': 'Height',
  'Textgröße': 'Text size',
  'Vorhersage ergänzen': 'Add forecast',
  'Luftdruck ergänzen': 'Add pressure',
  'Refresh Sekunden': 'Refresh seconds',
  'Trennzeichen': 'Separator',
  'Hintergrundfarbe': 'Background color',
  'Textfarbe': 'Text color',
  'Textgröße': 'Text size',
  'Banner-Position': 'Banner position',
  'Banner-Dauer Sekunden': 'Banner duration seconds',
  'Banner-Höhe px': 'Banner height px',
  'Bannerfarbe': 'Banner color',
  'Banner-Textfarbe': 'Banner text color',
  'Banner-Textgröße': 'Banner text size',
  'Alert-Modus': 'Alert mode',
  'Banner-/Toast-Meldung': 'Banner/toast message',
  'Bestätigen-Text': 'Acknowledgement text',
  'Alert-Farbe optional': 'Optional alert color',
  'leer = Severity-Farbe': 'empty = severity color',
  'Alert-Textfarbe': 'Alert text color',
  'Alert-Textgröße': 'Alert text size',
  'Alert senden': 'Send alert',
  'Der Ticker kann feste Statusmeldungen oder Live-Meldungen anzeigen. Du kannst Farbe, Textgröße, Höhe, Position und Trennzeichen einstellen.': 'The ticker can show fixed status messages or live messages. You can configure color, text size, height, position and separator.',
  'Banner sind kurze Hinweise am Rand. Alerts können als Vollbild, Panel, Banner oder Toast angezeigt werden und optional TTS, Sound, Vibration und Bestätigung verwenden.': 'Banners are short notices at the screen edge. Alerts can be fullscreen, panel, banner or toast and optionally use TTS, sound, vibration and acknowledgement.',
  'Temperatur-Beispiel': 'Temperature example',
  'Fenster-Beispiel': 'Window example',
  'Energie-Beispiel': 'Energy example',
  'Wetter-Overlay': 'Weather overlay',
  'Du kannst eine Weather-Entity verwenden oder einen fertigen Text per Automation senden. Die Integration ergänzt Temperatur, Feuchte und Wind automatisch, wenn möglich.': 'You can use a weather entity or send a ready-made text from an automation. The integration automatically adds temperature, humidity and wind when possible.',
  'Optionaler Wettertext': 'Optional weather text',
  'leer = aus Weather-Entity automatisch bauen': 'empty = build automatically from weather entity',
  'Luftdruck anzeigen': 'Show pressure',
  'Vorhersage anzeigen, falls vorhanden': 'Show forecast if available',
  'Wetter ausblenden': 'Hide weather',
  'Kamera kann als Vollbild oder als kleines Bild-im-Bild angezeigt werden. Für ältere Tablets ist Snapshot/Proxy oft stabiler als ein echter Stream.': 'Camera can be shown fullscreen or as picture-in-picture. On older tablets, snapshot/proxy is often more stable than a real stream.',
  'Modus': 'Mode',
  'Refresh Sekunden': 'Refresh seconds',
  'Breite px': 'Width px',
  'Höhe px': 'Height px',
  'Kamera-/Bild-URL optional': 'Optional camera/image URL',
  'YAML kopieren': 'Copy YAML',
  'YAML wurde kopiert.': 'YAML copied.',
  'Bitte YAML manuell kopieren.': 'Please copy YAML manually.',
  'Display nach Tageszeit wechseln': 'Switch display by time of day',
  'Fensterwarnung als Ticker': 'Window warning as ticker',
  'Status-Overlay aus Sensoren': 'Status overlay from sensors',
  'Morgen-Sequenz mit Wetter': 'Morning sequence with weather',
  'Meldung mit TTS': 'Message with TTS',
  'Türklingel mit Kamera': 'Doorbell with camera',
  'Feste Tickerliste, eine Meldung pro Zeile': 'Fixed ticker list, one message per line',
  'Live-Ticker Text': 'Live ticker text',
  'Alert-Text': 'Alert text',
  'Bestätigung verlangen': 'Require acknowledgement',
  'Auto-Hide Sekunden': 'Auto-hide seconds',
  'Aktiv': 'Active',
  'Ja': 'Yes',
  'Nein': 'No',
  'Dauer Sekunden': 'Duration seconds',
  'Titel': 'Title',
  'Weather-Entity': 'Weather entity',
  'Layout': 'Layout',
};

customElements.define("ha-android-kiosk-panel", HaAndroidKioskPanel);
