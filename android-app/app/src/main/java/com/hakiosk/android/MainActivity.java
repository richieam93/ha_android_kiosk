package com.hakiosk.android;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebResourceRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class MainActivity extends Activity implements HaWebSocketClient.Listener, SensorEventListener {
    private static final String EVENT_STATUS = "ha_android_kiosk_status";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<PageSpec> pages = new ArrayList<>();
    private int currentPageIndex = 0;
    private boolean rotationEnabled = false;
    private boolean pauseOnTouch = true;
    private long rotationPausedUntil = 0;
    private int touchPauseSeconds = 30;
    private long lastSecretTap = 0;
    private int secretTapCount = 0;
    private String currentUrl = "";
    private int browserReloadSeconds = 0;
    private boolean browserExternalAuth = true;
    private boolean hideHomeAssistantHeader = false;
    private boolean fullscreenEnabled = false;
    private int browserZoomPercent = 100;
    private int currentPageZoomPercent = 100;
    private final ArrayList<String> backgroundImages = new ArrayList<>();
    private int backgroundIndex = 0;
    private int backgroundIntervalSeconds = 20;
    private boolean backgroundEnabled = false;
    private boolean backgroundRandom = false;
    private String backgroundSize = "cover";
    private String backgroundPosition = "center";
    private String backgroundRepeat = "no-repeat";
    private int backgroundOpacity = 100;
    private boolean waitForBackgroundBeforeLayout = true;
    private boolean transparentWebView = true;
    private boolean forceMobileViewport = false;
    private int firstRevealDelayMs = 250;
    private boolean startupPageLoadStarted = false;
    private boolean firstPageFinished = false;
    private boolean firstPageRevealed = false;
    private boolean firstBackgroundReady = false;
    private boolean initialConfigFetchCompleted = false;
    private boolean applyingStoredConfig = false;
    private boolean settingsSyncInFlight = false;
    private int settingsSyncIntervalSeconds = 300;
    private long lastSettingsSyncAt = 0L;
    private String lastSettingsSyncStatus = "nie";
    private boolean visualWatchdogEnabled = true;
    private int visualWatchdogSeconds = 45;
    private int ttsVolumePercent = 100;
    private int mediaVolumePercent = 100;
    private String preferredAudioStream = "music";
    private boolean forceMaxVolumeForSpeech = true;
    private boolean requestAudioFocus = true;
    private String nativeBackgroundUrl = "";
    private String nativeBackgroundLoadingUrl = "";
    private int nativeBackgroundLoadSeq = 0;
    private long lastNativeBackgroundFailAt = 0L;
    private String lastNativeBackgroundFailUrl = "";
    private Bitmap nativeBackgroundBitmap;
    private final Random random = new Random();

    private FrameLayout root;
    private ImageView backgroundImageView;
    private TextView startupLoadingView;
    private WebView webView;
    private TextView tickerView;
    private TextView bannerView;
    private TextView clockView;
    private TextView weatherView;
    private TextView statusView;
    private FrameLayout alertView;
    private TextView alertText;
    private WebView cameraView;
    private View blackView;

    private HaWebSocketClient wsClient;
    private HaRestClient restClient;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor lightSensor;
    private float lastAccel = -1;
    private float lastLightLux = -1;
    private boolean lightSensorAvailable = false;
    private long lastMotionEvent = 0;
    private boolean motionEnabled = false;

    @SuppressWarnings("deprecation")
    private Camera activeCamera;
    private SurfaceTexture cameraTexture;
    private int activeCameraFacing = -1;
    private boolean cameraMotionEnabled = false;
    private long lastCameraMotionEvent = 0;
    private double lastFrameLuma = -1;
    private double lastCameraMotionScore = 0;
    private int cameraPreviewWidth = 0;
    private int cameraPreviewHeight = 0;

    private MediaPlayer mediaPlayer;
    private String mediaState = "idle";
    private boolean mediaMuted = false;
    private final ArrayList<String> mediaQueue = new ArrayList<>();
    private int mediaQueueIndex = -1;
    private boolean mediaLoop = false;
    private String currentMediaUrl = "";
    private int lastBrightnessPercent = 100;
    private int lastVolumePercent = 0;
    private TextToSpeech tts;

    private final Runnable rotationRunnable = new Runnable() {
        @Override public void run() { rotateIfNeeded(); }
    };

    private final Runnable clockRunnable = new Runnable() {
        @Override public void run() {
            if (clockView != null && clockView.getVisibility() == View.VISIBLE) {
                clockView.setText(new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()));
            }
            handler.postDelayed(this, 1000);
        }
    };

    private final Runnable statusRunnable = new Runnable() {
        @Override public void run() {
            if (AppSettings.needsTokenRefresh(MainActivity.this)) {
                refreshAccessTokenIfNeeded(() -> sendStatus("online", null));
            } else {
                sendStatus("online", null);
            }
            handler.postDelayed(this, 60000);
        }
    };

    private final Runnable settingsSyncRunnable = new Runnable() {
        @Override public void run() {
            fetchStoredSettingsFromHomeAssistant(null, false);
            scheduleSettingsSync();
        }
    };

    private final Runnable visualWatchdogRunnable = new Runnable() {
        @Override public void run() {
            reapplyVisualState("watchdog");
            scheduleVisualWatchdog();
        }
    };

    private final Runnable browserReloadRunnable = new Runnable() {
        @Override public void run() {
            if (webView != null && browserReloadSeconds > 0) {
                webView.reload();
                handler.postDelayed(this, browserReloadSeconds * 1000L);
            }
        }
    };

    private final Runnable kioskChromeRunnable = new Runnable() {
        @Override public void run() {
            applyHomeAssistantHeaderVisibility();
        }
    };

    private final Runnable dashboardZoomRunnable = new Runnable() {
        @Override public void run() {
            applyDashboardZoom();
        }
    };

    private final Runnable fullscreenRunnable = new Runnable() {
        @Override public void run() {
            if (fullscreenEnabled) applyFullscreen(true);
        }
    };


    private final Runnable backgroundRunnable = new Runnable() {
        @Override public void run() {
            rotateBackground();
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WebView.setWebContentsDebuggingEnabled(Build.VERSION.SDK_INT >= 19 && AppSettings.getBoolean(this, "debug_webview", false));
        if (handleAuthIntent(getIntent())) return;
        if (!AppSettings.isConfigured(this)) showSettings(); else startKiosk();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleAuthIntent(intent);
    }

    @Override protected void onResume() {
        super.onResume();
        if (fullscreenEnabled) handler.postDelayed(fullscreenRunnable, 150);
        scheduleHomeAssistantChromeRefresh();
        scheduleDashboardZoomRefresh();
        handler.postDelayed(() -> fetchStoredSettingsFromHomeAssistant(null, false), 900);
        scheduleSettingsSync();
        scheduleVisualWatchdog();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && fullscreenEnabled) handler.postDelayed(fullscreenRunnable, 100);
        if (hasFocus) {
            scheduleHomeAssistantChromeRefresh();
            scheduleDashboardZoomRefresh();
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (wsClient != null) wsClient.stop();
        stopMotionDetection();
        stopLightSensor();
        stopDeviceCamera();
        releaseMediaPlayer();
        if (tts != null) { try { tts.shutdown(); } catch (Exception ignored) {} tts = null; }
        handler.removeCallbacksAndMessages(null);
        if (nativeBackgroundBitmap != null) { try { nativeBackgroundBitmap.recycle(); } catch (Exception ignored) {} nativeBackgroundBitmap = null; }
        if (webView != null) webView.destroy();
        if (cameraView != null) cameraView.destroy();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            if (pauseOnTouch) rotationPausedUntil = System.currentTimeMillis() + touchPauseSeconds * 1000L;
            if (fullscreenEnabled) handler.postDelayed(fullscreenRunnable, 200);
            maybeOpenSettingsBySecretTap(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    private void showSettings() {
        if (wsClient != null) wsClient.stop();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(null);
        getWindow().getDecorView().setSystemUiVisibility(0);

        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(20), dp(20), dp(28));
        scroll.addView(box);

        TextView title = new TextView(this);
        title.setText("HA Android Kiosk");
        title.setTextSize(26);
        title.setTextColor(Color.BLACK);
        title.setPadding(0, 0, 0, dp(10));
        box.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Eigenständige Kiosk-App. Trage deine Home-Assistant-URL ein und melde dich wie in der originalen Home-Assistant-App über die Home-Assistant-Login-Seite an. Die App speichert danach automatisch Access-/Refresh-Token und registriert das Gerät.");
        hint.setTextColor(Color.DKGRAY);
        hint.setPadding(0, 0, 0, dp(14));
        box.addView(hint);

        EditText haUrl = field("Home-Assistant-URL", AppSettings.baseUrl(this), "http://homeassistant.local:8123", false);
        EditText token = field("Optional: manueller Long-Lived Access Token", AppSettings.token(this), "Leer lassen, wenn du Login verwenden willst", true);
        EditText deviceId = field("Geräte-ID", AppSettings.deviceId(this), "wandtablet_kueche", false);
        EditText name = field("Gerätename", AppSettings.get(this, "device_name", Build.MANUFACTURER + " " + Build.MODEL), "Wandtablet Küche", false);
        EditText dashboard = field("Standard-Dashboard-Pfad", AppSettings.get(this, "default_url", "/lovelace/0"), "/lovelace/0", false);
        CheckBox keepAwake = check("Display wach halten", AppSettings.getBoolean(this, "keep_awake", true));
        CheckBox browserAuth = check("Dashboard nach App-Login automatisch anmelden (wie Companion-App)", AppSettings.getBoolean(this, "browser_external_auth", true));
        CheckBox startBoot = check("Nach Geräteneustart automatisch starten", AppSettings.getBoolean(this, "start_on_boot", false));
        CheckBox debugWeb = check("WebView-Debugging aktivieren", AppSettings.getBoolean(this, "debug_webview", false));

        addLabeled(box, "Home Assistant", haUrl);
        addLabeled(box, "Optionaler Token", token);
        addLabeled(box, "Geräte-ID", deviceId);
        addLabeled(box, "Gerätename", name);
        addLabeled(box, "Dashboard", dashboard);
        box.addView(keepAwake);
        box.addView(browserAuth);
        box.addView(startBoot);
        box.addView(debugWeb);

        Button login = button("Mit Home Assistant anmelden");
        login.setOnClickListener(v -> {
            saveLocalSettings(haUrl, token, deviceId, name, dashboard, keepAwake, browserAuth, startBoot, debugWeb);
            if (AppSettings.baseUrl(this).isEmpty()) {
                Toast.makeText(this, "Home-Assistant-URL ist erforderlich", Toast.LENGTH_LONG).show();
                return;
            }
            startHomeAssistantLogin();
        });
        box.addView(login);

        Button save = button("Mit manuellem Token starten");
        save.setOnClickListener(v -> {
            saveLocalSettings(haUrl, token, deviceId, name, dashboard, keepAwake, browserAuth, startBoot, debugWeb);
            if (!AppSettings.isConfigured(this)) {
                Toast.makeText(this, "Bitte anmelden oder einen Token eintragen", Toast.LENGTH_LONG).show();
                return;
            }
            startKiosk();
        });
        box.addView(save);

        Button writeSettings = button("Android-Systemhelligkeit erlauben");
        writeSettings.setOnClickListener(v -> requestWriteSettingsPermission());
        box.addView(writeSettings);

        setContentView(scroll);
    }


    private void saveLocalSettings(EditText haUrl, EditText token, EditText deviceId, EditText name, EditText dashboard, CheckBox keepAwake, CheckBox browserAuth, CheckBox startBoot, CheckBox debugWeb) {
        AppSettings.put(this, "ha_url", haUrl.getText().toString());
        String tokenValue = token.getText().toString();
        if (!tokenValue.trim().isEmpty()) AppSettings.put(this, "ha_token", tokenValue);
        AppSettings.put(this, "device_id", deviceId.getText().toString());
        AppSettings.put(this, "device_name", name.getText().toString());
        AppSettings.put(this, "default_url", dashboard.getText().toString());
        AppSettings.putBoolean(this, "keep_awake", keepAwake.isChecked());
        AppSettings.putBoolean(this, "browser_external_auth", browserAuth.isChecked());
        AppSettings.putBoolean(this, "start_on_boot", startBoot.isChecked());
        AppSettings.putBoolean(this, "debug_webview", debugWeb.isChecked());
    }

    private void startHomeAssistantLogin() {
        try {
            WebView authView = new WebView(this);
            setupWebView(authView);
            authView.setWebViewClient(new WebViewClient() {
                @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    if (handleAuthCallbackUrl(url)) return true;
                    return false;
                }

                @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    if (Build.VERSION.SDK_INT >= 21 && request != null && request.getUrl() != null) {
                        if (handleAuthCallbackUrl(request.getUrl().toString())) return true;
                    }
                    return false;
                }
            });
            setContentView(authView);
            authView.loadUrl(HaAuthClient.authorizeUrl(AppSettings.baseUrl(this)));
            Toast.makeText(this, "Bitte bei Home Assistant anmelden", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Login konnte nicht gestartet werden: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private boolean handleAuthIntent(Intent intent) {
        if (intent == null || intent.getData() == null) return false;
        return handleAuthCallbackUrl(intent.getData().toString());
    }

    private boolean handleAuthCallbackUrl(String url) {
        if (url == null || !url.startsWith(HaAuthClient.REDIRECT_URI)) return false;
        try {
            Uri uri = Uri.parse(url);
            String error = uri.getQueryParameter("error");
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, "Home-Assistant-Login abgebrochen: " + error, Toast.LENGTH_LONG).show();
                showSettings();
                return true;
            }
            String code = uri.getQueryParameter("code");
            if (code == null || code.isEmpty()) {
                Toast.makeText(this, "Kein Login-Code erhalten", Toast.LENGTH_LONG).show();
                showSettings();
                return true;
            }
            exchangeAuthCode(code);
        } catch (Exception e) {
            Toast.makeText(this, "Login-Antwort konnte nicht gelesen werden: " + e.getMessage(), Toast.LENGTH_LONG).show();
            showSettings();
        }
        return true;
    }

    private void exchangeAuthCode(final String code) {
        TextView progress = new TextView(this);
        progress.setText("Anmeldung erfolgreich. Token wird erstellt und Gerät wird registriert ...");
        progress.setTextSize(18);
        progress.setTextColor(Color.BLACK);
        progress.setGravity(Gravity.CENTER);
        progress.setPadding(dp(24), dp(24), dp(24), dp(24));
        setContentView(progress);
        new Thread(() -> {
            try {
                JSONObject tokens = HaAuthClient.exchangeAuthCode(AppSettings.baseUrl(this), code);
                AppSettings.saveTokens(
                    this,
                    tokens.optString("access_token", ""),
                    tokens.optString("refresh_token", ""),
                    tokens.optInt("expires_in", 0)
                );
                clearWebFrontendSession();
                restClient = new HaRestClient(AppSettings.baseUrl(this), AppSettings.token(this));
                registerMobileApp();
                handler.post(() -> {
                    Toast.makeText(this, "Gerät erfolgreich angemeldet", Toast.LENGTH_LONG).show();
                    startKiosk();
                });
            } catch (Exception e) {
                handler.post(() -> {
                    Toast.makeText(this, "Token konnte nicht erstellt werden: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    showSettings();
                });
            }
        }, "ha-auth-exchange").start();
    }

    private void refreshAccessTokenIfNeeded(final Runnable afterRefresh) {
        if (!AppSettings.needsTokenRefresh(this)) {
            afterRefresh.run();
            return;
        }
        new Thread(() -> {
            try {
                JSONObject tokens = HaAuthClient.refreshAccessToken(AppSettings.baseUrl(this), AppSettings.refreshToken(this));
                AppSettings.saveTokens(this, tokens.optString("access_token", ""), "", tokens.optInt("expires_in", 0));
                restClient = new HaRestClient(AppSettings.baseUrl(this), AppSettings.token(this));
                handler.post(afterRefresh);
            } catch (Exception e) {
                handler.post(() -> {
                    Toast.makeText(this, "Home-Assistant-Token konnte nicht erneuert werden: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    showSettings();
                });
            }
        }, "ha-token-refresh").start();
    }

    private EditText field(String label, String value, String hint, boolean password) {
        EditText edit = new EditText(this);
        edit.setText(value == null ? "" : value);
        edit.setHint(hint);
        edit.setSingleLine(!label.contains("Token"));
        edit.setInputType(password ? (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD) : InputType.TYPE_CLASS_TEXT);
        edit.setTextSize(15);
        return edit;
    }

    private CheckBox check(String text, boolean checked) {
        CheckBox c = new CheckBox(this);
        c.setText(text);
        c.setChecked(checked);
        c.setPadding(0, dp(4), 0, dp(4));
        return c;
    }

    private void addLabeled(LinearLayout box, String label, View view) {
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(Color.BLACK);
        l.setPadding(0, dp(10), 0, dp(4));
        box.addView(l);
        box.addView(view);
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setPadding(dp(8), dp(8), dp(8), dp(8));
        return b;
    }

    private void startKiosk() {
        refreshAccessTokenIfNeeded(this::startKioskInternal);
    }

    private void startKioskInternal() {
        requestRuntimePermissions();
        applyFullscreen(true);
        if (AppSettings.getBoolean(this, "keep_awake", true)) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        waitForBackgroundBeforeLayout = AppSettings.getBoolean(this, "wait_for_background", true);
        transparentWebView = AppSettings.getBoolean(this, "transparent_webview", true);
        forceMobileViewport = AppSettings.getBoolean(this, "force_mobile_viewport", false);
        firstRevealDelayMs = Math.max(0, Math.min(2000, AppSettings.getInt(this, "first_reveal_ms", 250)));
        startupPageLoadStarted = false;
        firstPageFinished = false;
        firstPageRevealed = false;
        firstBackgroundReady = false;
        initialConfigFetchCompleted = false;
        nativeBackgroundUrl = "";

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        backgroundImageView = new ImageView(this);
        backgroundImageView.setBackgroundColor(Color.BLACK);
        backgroundImageView.setVisibility(View.GONE);
        root.addView(backgroundImageView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        webView = new WebView(this);
        webView.setAlpha(0f);
        root.addView(webView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        startupLoadingView = overlayText(16, Color.WHITE, 0x66000000, Gravity.CENTER);
        startupLoadingView.setText("Kiosk wird geladen ...");
        startupLoadingView.setVisibility(View.VISIBLE);
        root.addView(startupLoadingView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        browserExternalAuth = AppSettings.getBoolean(this, "browser_external_auth", true);
        hideHomeAssistantHeader = AppSettings.getBoolean(this, "hide_ha_header", false);
        browserZoomPercent = clampZoom(AppSettings.getInt(this, "browser_zoom_percent", 100));
        settingsSyncIntervalSeconds = Math.max(0, Math.min(86400, AppSettings.getInt(this, "settings_sync_seconds", 300)));
        visualWatchdogSeconds = Math.max(0, Math.min(3600, AppSettings.getInt(this, "visual_watchdog_seconds", 45)));
        visualWatchdogEnabled = visualWatchdogSeconds > 0;
        ttsVolumePercent = Math.max(0, Math.min(100, AppSettings.getInt(this, "tts_volume_percent", 100)));
        mediaVolumePercent = Math.max(0, Math.min(100, AppSettings.getInt(this, "media_volume_percent", 100)));
        preferredAudioStream = AppSettings.get(this, "preferred_audio_stream", "music");
        forceMaxVolumeForSpeech = AppSettings.getBoolean(this, "force_max_volume_for_speech", true);
        requestAudioFocus = AppSettings.getBoolean(this, "request_audio_focus", true);
        currentPageZoomPercent = browserZoomPercent;
        setupWebView(webView);
        applyWebViewTransparency(webView);
        String savedUserAgent = AppSettings.get(this, "user_agent", "");
        if (!savedUserAgent.isEmpty()) webView.getSettings().setUserAgentString(savedUserAgent);
        browserReloadSeconds = AppSettings.getInt(this, "browser_reload_seconds", 0);
        createOverlays();
        setContentView(root);

        lastVolumePercent = getVolumePercent("music");
        restClient = new HaRestClient(AppSettings.baseUrl(this), AppSettings.token(this));
        if (AppSettings.get(this, "mobile_app_webhook_id", "").isEmpty()) new Thread(this::registerMobileApp, "ha-mobile-app-register").start();
        registerDevice();
        connectWebSocket();
        startLightSensor();
        applyCachedStoredConfig();
        handler.postDelayed(() -> loadInitialPageIfNeeded(), 3200);
        fetchStoredSettingsFromHomeAssistant(() -> loadInitialPageIfNeeded(), true);
        handler.postDelayed(() -> rescueAndRevealDashboard(), 6500);
        handler.postDelayed(() -> rescueAndRevealDashboard(), 12000);
        handler.post(clockRunnable);
        handler.postDelayed(statusRunnable, 1500);
        scheduleBrowserReload();
        scheduleSettingsSync();
        scheduleVisualWatchdog();
    }

    private void setupWebView(WebView view) {
        WebSettings s = view.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(!forceMobileViewport);
        s.setUseWideViewPort(!forceMobileViewport);
        s.setSupportZoom(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false);
        try { s.setGeolocationEnabled(true); } catch (Exception ignored) {}
        try { s.setRenderPriority(WebSettings.RenderPriority.HIGH); } catch (Exception ignored) {}
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        if (Build.VERSION.SDK_INT >= 14) s.setTextZoom(100);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        if (Build.VERSION.SDK_INT >= 21) s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        try {
            CookieManager cookies = CookieManager.getInstance();
            cookies.setAcceptCookie(true);
            if (Build.VERSION.SDK_INT >= 21) cookies.setAcceptThirdPartyCookies(view, true);
        } catch (Exception ignored) {}
        applyWebViewTransparency(view);
        view.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        view.setOverScrollMode(View.OVER_SCROLL_NEVER);
        view.setLongClickable(false);
        view.setHapticFeedbackEnabled(false);
        view.setOnLongClickListener(v -> true);
        view.addJavascriptInterface(new ExternalAuthBridge(view), "externalApp");
        view.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, String url) {
                if (url == null) return false;
                if (handleAuthCallbackUrl(url)) return true;
                if (url.startsWith("http://") || url.startsWith("https://")) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); return true; } catch (Exception ignored) { return false; }
            }

            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                if (Build.VERSION.SDK_INT >= 21 && request != null && request.getUrl() != null) {
                    return shouldOverrideUrlLoading(v, request.getUrl().toString());
                }
                return false;
            }

            @Override public void onPageStarted(WebView v, String url, Bitmap favicon) {
                if (v == webView && !firstPageRevealed) {
                    firstPageFinished = false;
                    prepareFirstPageRevealGate();
                }
            }

            @Override public void onPageFinished(WebView v, String url) {
                if (v == webView) {
                    firstPageFinished = true;
                    scheduleHomeAssistantChromeRefresh();
                    scheduleDashboardZoomRefresh();
                    if (backgroundEnabled) {
                        handler.postDelayed(() -> applyCurrentBackground(), 350);
                        handler.postDelayed(() -> applyCurrentBackgroundLight(), 1400);
                    }
                    handler.postDelayed(() -> revealWebViewIfReady(false), backgroundEnabled ? 500 : 120);
                    handler.postDelayed(() -> rescueHomeAssistantLayout(), 900);
                    handler.postDelayed(() -> applyDashboardZoom(), 1100);
                    handler.postDelayed(() -> rescueAndRevealDashboard(), 2500);
                }
            }
        });
        view.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest request) {
                if (Build.VERSION.SDK_INT >= 21) request.grant(request.getResources());
            }
        });
    }

    private void createOverlays() {
        tickerView = overlayText(16, Color.WHITE, 0xCC000000, Gravity.CENTER_VERTICAL);
        tickerView.setSingleLine(true);
        tickerView.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        tickerView.setMarqueeRepeatLimit(-1);
        tickerView.setSelected(true);
        FrameLayout.LayoutParams tickerParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(38), Gravity.BOTTOM);
        tickerView.setVisibility(View.GONE);
        root.addView(tickerView, tickerParams);

        bannerView = overlayText(18, Color.WHITE, 0xDD0D47A1, Gravity.CENTER);
        FrameLayout.LayoutParams bannerParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(58), Gravity.TOP);
        bannerView.setVisibility(View.GONE);
        root.addView(bannerView, bannerParams);

        clockView = overlayText(24, Color.WHITE, 0x99000000, Gravity.CENTER);
        FrameLayout.LayoutParams clockParams = new FrameLayout.LayoutParams(dp(160), dp(56), Gravity.TOP | Gravity.RIGHT);
        clockParams.setMargins(0, dp(12), dp(12), 0);
        clockView.setVisibility(View.GONE);
        root.addView(clockView, clockParams);

        weatherView = overlayText(16, Color.WHITE, 0x99000000, Gravity.CENTER);
        FrameLayout.LayoutParams weatherParams = new FrameLayout.LayoutParams(dp(220), dp(52), Gravity.TOP | Gravity.LEFT);
        weatherParams.setMargins(dp(12), dp(12), 0, 0);
        weatherView.setVisibility(View.GONE);
        root.addView(weatherView, weatherParams);

        statusView = overlayText(12, Color.WHITE, 0x88000000, Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(24), Gravity.TOP);
        statusView.setVisibility(View.GONE);
        root.addView(statusView, statusParams);

        cameraView = new WebView(this);
        setupWebView(cameraView);
        cameraView.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout.LayoutParams cameraParams = new FrameLayout.LayoutParams(dp(320), dp(190), Gravity.RIGHT | Gravity.BOTTOM);
        cameraParams.setMargins(0, 0, dp(12), dp(50));
        cameraView.setVisibility(View.GONE);
        root.addView(cameraView, cameraParams);

        alertView = new FrameLayout(this);
        alertView.setBackgroundColor(0xEE000000);
        alertText = overlayText(28, Color.WHITE, Color.TRANSPARENT, Gravity.CENTER);
        alertText.setPadding(dp(28), dp(28), dp(28), dp(28));
        alertView.addView(alertText, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        alertView.setVisibility(View.GONE);
        root.addView(alertView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        alertView.setOnClickListener(v -> alertView.setVisibility(View.GONE));

        blackView = new View(this);
        blackView.setBackgroundColor(Color.BLACK);
        blackView.setVisibility(View.GONE);
        root.addView(blackView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private TextView overlayText(int sp, int textColor, int bgColor, int gravity) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTextColor(textColor);
        t.setBackgroundColor(bgColor);
        t.setGravity(gravity);
        t.setPadding(dp(12), 0, dp(12), 0);
        return t;
    }

    private void connectWebSocket() {
        if (wsClient != null) wsClient.stop();
        wsClient = new HaWebSocketClient(AppSettings.baseUrl(this), AppSettings.token(this), AppSettings.deviceId(this), this);
        wsClient.start();
    }

    @Override public void onStatus(String status) {
        if (status != null && status.contains("Token ist ungültig") && !AppSettings.refreshToken(this).isEmpty()) {
            refreshAccessTokenIfNeeded(this::connectWebSocket);
        }
        if (statusView == null) return;
        statusView.setText(status);
        statusView.setVisibility(View.VISIBLE);
        handler.postDelayed(() -> {
            if (statusView != null) statusView.setVisibility(View.GONE);
        }, 4000);
    }

    @Override public void onCommand(JSONObject data) {
        try {
            handleCommand(data);
            sendStatus("command_ok", data.optString("command", data.optString("kiosk_action", "")));
        } catch (Exception e) {
            Toast.makeText(this, "Kiosk-Befehl fehlgeschlagen: " + e.getMessage(), Toast.LENGTH_LONG).show();
            sendStatus("command_error: " + e.getMessage(), data.optString("command", ""));
        }
    }

    private void handleCommand(JSONObject data) throws Exception {
        String command = data.optString("command", data.optString("kiosk_action", ""));
        JSONObject payload = data.optJSONObject("payload");
        if (payload == null) payload = data;
        if (command.isEmpty()) command = payload.optString("kiosk_action", "");

        switch (command) {
            case "browser_config":
            case "set_browser_config":
                applyBrowserConfig(payload);
                break;
            case "show_page":
            case "set_page":
                loadPage(
                    payload.optString("url", payload.optString("path", payload.optString("page", ""))),
                    readZoomFromPayload(payload, 0)
                );
                break;
            case "goto_screen":
                gotoScreen(payload.optString("screen_id", payload.optString("page_id", payload.optString("path", payload.optString("url", "")))));
                break;
            case "next_screen":
                goRelativeScreen(1);
                break;
            case "previous_screen":
                goRelativeScreen(-1);
                break;
            case "pause_rotation":
                pauseRotation(payload.optInt("duration", payload.optInt("seconds", 0)));
                break;
            case "resume_rotation":
                rotationEnabled = true;
                rotationPausedUntil = 0;
                scheduleRotation();
                break;
            case "reload_page":
                if (webView != null) webView.reload();
                break;
            case "rotation":
            case "set_pages":
                configureRotation(payload);
                break;
            case "rotation_start":
                rotationEnabled = true;
                scheduleRotation();
                break;
            case "rotation_stop":
                rotationEnabled = false;
                handler.removeCallbacks(rotationRunnable);
                break;
            case "ticker":
            case "send_ticker_message":
                showTickerPayload(payload);
                break;
            case "clear_ticker":
                showTicker("", false);
                break;
            case "ticker_config":
            case "update_ticker_config":
                updateTickerConfig(payload);
                break;
            case "banner":
            case "show_banner":
                showBannerPayload(payload);
                break;
            case "toast":
            case "show_toast":
                showToastPayload(payload);
                break;
            case "alert":
            case "fullscreen_alert":
            case "show_alert":
                showAlertPayload(payload);
                break;
            case "clear_alert":
                clear("alerts");
                break;
            case "show_overlay":
                showOverlay(payload);
                break;
            case "background_slideshow":
            case "set_background_slideshow":
                configureBackgroundSlideshow(payload);
                break;
            case "set_background":
            case "set_background_image":
                setSingleBackground(payload);
                break;
            case "clear_background":
                clearBackground();
                break;
            case "clock":
            case "show_clock":
                showClockPayload(payload);
                break;
            case "weather":
            case "show_weather":
                showWeatherPayload(payload);
                break;
            case "camera":
            case "camera_overlay":
            case "show_camera":
                showCameraPayload(payload);
                break;
            case "camera_front":
                setDeviceCamera("front", payload.optBoolean("enabled", payload.optBoolean("value", true)), payload.optBoolean("motion", cameraMotionEnabled));
                break;
            case "camera_control":
                setDeviceCamera("front", payload.optBoolean("enabled", true), payload.optBoolean("motion", cameraMotionEnabled));
                break;
            case "camera_motion_detection":
                setCameraMotionDetection(payload.optBoolean("enabled", true), "front");
                break;
            case "camera_all_off":
                stopDeviceCamera();
                break;
            case "fullscreen":
                applyFullscreen(payload.optBoolean("enabled", payload.optBoolean("value", true)));
                break;
            case "orientation":
            case "set_screen_orientation":
                setOrientation(payload.optString("value", payload.optString("orientation", "unspecified")));
                break;
            case "screen":
                setScreen(payload.optString("value", payload.optString("state", "on")));
                break;
            case "set_screen_power":
                setScreen(payload.optBoolean("power", payload.optBoolean("value", true)) ? "on" : "blank");
                break;
            case "brightness":
            case "set_brightness":
                setBrightness(payload.optDouble("value", payload.optDouble("brightness", payload.optDouble("level", 1.0))), payload.optBoolean("system", false));
                break;
            case "volume":
            case "set_volume":
                setVolume(payload.optDouble("value", payload.optDouble("volume", payload.optDouble("level", 0.5))), payload.optString("stream", "music"));
                break;
            case "set_device_setting":
                setDeviceSetting(payload.optString("setting", ""), payload.opt("value"));
                try {
                    String setting = payload.optString("setting", "");
                    if (!setting.isEmpty()) updateCachedDeviceSettings(new JSONObject().put(setting, payload.opt("value")));
                } catch (Exception ignored) {}
                break;
            case "open_android_settings":
                openAndroidSettings();
                break;
            case "fetch_config":
            case "sync_settings":
            case "refresh_config":
                fetchStoredSettingsFromHomeAssistant(() -> reapplyVisualState("manual"), true);
                break;
            case "recover_dashboard":
            case "reapply_visuals":
                reapplyVisualState("manual");
                break;
            case "report_device_state":
                sendStatus("online", "report_device_state");
                break;
            case "identify_device":
                identifyDevice();
                break;
            case "media_play_url":
            case "play_media":
            case "play_sound":
            case "play_announcement":
                playMediaFromPayload(payload);
                break;
            case "media_play":
            case "media_resume":
                resumeMedia();
                break;
            case "media_pause":
                pauseMedia();
                break;
            case "media_stop":
            case "stop_media":
            case "stop_audio":
                stopMedia();
                break;
            case "media_next":
                playNextMedia(true);
                break;
            case "media_previous":
                playPreviousMedia();
                break;
            case "media_seek":
                seekMedia(payload.optInt("position", payload.optInt("seconds", 0)));
                break;
            case "media_loop":
                mediaLoop = payload.optBoolean("loop", payload.optBoolean("enabled", payload.optBoolean("value", false)));
                if (mediaPlayer != null) mediaPlayer.setLooping(mediaLoop && mediaQueue.size() <= 1);
                sendStatus(mediaLoop ? "media_loop_on" : "media_loop_off", "media_loop");
                break;
            case "set_media_volume":
                setVolume(payload.optDouble("volume", payload.optDouble("value", 70)), payload.optString("stream", preferredAudioStream));
                sendStatus("media_volume_set", "set_media_volume");
                break;
            case "audio_config":
            case "set_audio_config":
                applyAudioSettingsFromPayload(payload);
                sendStatus("audio_config_applied", "audio_config");
                break;
            case "media_mute":
                setMediaMuted(payload.optBoolean("muted", payload.optBoolean("value", true)));
                break;
            case "media_unmute":
                setMediaMuted(false);
                break;
            case "tts_speak":
                applyAudioSettingsFromPayload(payload);
                speakText(
                    payload.optString("message", payload.optString("text", "")),
                    payload.optString("language", payload.optString("tts_language", "de-DE")),
                    payload.optDouble("volume", payload.optDouble("tts_volume", ttsVolumePercent)),
                    payload.optString("stream", preferredAudioStream)
                );
                break;
            case "tts_stop":
                if (tts != null) tts.stop();
                sendStatus("tts_stopped", "tts_stop");
                break;
            case "vibrate":
            case "vibrate_device":
                vibrate(payload.optInt("duration", payload.optInt("value", 250)));
                break;
            case "restart_app":
                recreate();
                break;
            case "clear":
                clear(payload.optString("target", "all"));
                break;
            case "motion_detection":
                if (payload.optBoolean("enabled", true)) startMotionDetection(); else stopMotionDetection();
                break;
            default:
                Toast.makeText(this, "Unbekannter Kiosk-Befehl: " + command, Toast.LENGTH_LONG).show();
        }
    }

    private void configureBackgroundSlideshow(JSONObject payload) {
        backgroundEnabled = payload.optBoolean("enabled", true);
        backgroundImages.clear();
        JSONArray arr = payload.optJSONArray("images");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                String item = arr.optString(i, "").trim();
                if (!item.isEmpty()) backgroundImages.add(item);
            }
        }
        String text = payload.optString("images_text", payload.optString("image_list", "")).trim();
        if (!text.isEmpty()) {
            for (String line : text.split("\\n")) {
                String item = line.trim();
                if (!item.isEmpty()) backgroundImages.add(item);
            }
        }
        backgroundIntervalSeconds = Math.max(2, payload.optInt("interval", payload.optInt("interval_seconds", 20)));
        backgroundRandom = payload.optBoolean("random", false);
        backgroundSize = payload.optString("size", "cover");
        backgroundPosition = payload.optString("position", "center");
        backgroundRepeat = payload.optString("repeat", "no-repeat");
        backgroundOpacity = Math.max(0, Math.min(100, payload.optInt("opacity", 100)));
        backgroundIndex = 0;
        if (!backgroundEnabled || backgroundImages.isEmpty()) {
            clearBackground();
            return;
        }
        applyCurrentBackground();
        scheduleBackgroundRotation();
        if (!applyingStoredConfig) updateCachedSection("background", payload);
        sendStatus("background_slideshow_on", "background_slideshow");
    }

    private void setSingleBackground(JSONObject payload) {
        String image = payload.optString("image", payload.optString("url", "")).trim();
        if (image.isEmpty()) return;
        backgroundImages.clear();
        backgroundImages.add(image);
        backgroundEnabled = true;
        backgroundIntervalSeconds = 0;
        backgroundSize = payload.optString("size", backgroundSize);
        backgroundPosition = payload.optString("position", backgroundPosition);
        backgroundRepeat = payload.optString("repeat", backgroundRepeat);
        backgroundOpacity = Math.max(0, Math.min(100, payload.optInt("opacity", backgroundOpacity)));
        applyCurrentBackground();
        handler.removeCallbacks(backgroundRunnable);
        if (!applyingStoredConfig) {
            try {
                JSONObject cached = new JSONObject();
                cached.put("enabled", true);
                cached.put("images", new JSONArray().put(image));
                cached.put("interval", 0);
                cached.put("size", backgroundSize);
                cached.put("position", backgroundPosition);
                cached.put("repeat", backgroundRepeat);
                cached.put("opacity", backgroundOpacity);
                updateCachedSection("background", cached);
            } catch (Exception ignored) {}
        }
        sendStatus("background_image_set", "set_background");
    }

    private void scheduleBackgroundRotation() {
        handler.removeCallbacks(backgroundRunnable);
        if (backgroundEnabled && backgroundImages.size() > 1 && backgroundIntervalSeconds > 0) {
            handler.postDelayed(backgroundRunnable, backgroundIntervalSeconds * 1000L);
        }
    }

    private void rotateBackground() {
        if (!backgroundEnabled || backgroundImages.isEmpty()) return;
        if (backgroundRandom && backgroundImages.size() > 1) {
            int next = random.nextInt(backgroundImages.size());
            if (next == backgroundIndex) next = (next + 1) % backgroundImages.size();
            backgroundIndex = next;
        } else {
            backgroundIndex = (backgroundIndex + 1) % backgroundImages.size();
        }
        applyCurrentBackground();
        scheduleBackgroundRotation();
    }

    private void applyCurrentBackground() {
        if (webView == null || !backgroundEnabled || backgroundImages.isEmpty()) return;
        applyNativeBackground();
        // Der Hintergrund wird ab v17.12 nur noch nativ hinter der WebView gezeichnet.
        // Keine background-image-CSS-Injektion mehr in Home Assistant: das spart auf
        // Android 6 viel WebView/GPU-Speicher und verhindert doppelte Bild-Decodes.
        applyBackgroundTransparencyForZoom();
    }

    private void applyCurrentBackgroundLight() {
        if (webView == null || !backgroundEnabled) return;
        applyBackgroundTransparencyForZoom();
    }

    private void clearBackground() {
        backgroundEnabled = false;
        firstBackgroundReady = true;
        nativeBackgroundUrl = "";
        nativeBackgroundLoadingUrl = "";
        lastNativeBackgroundFailUrl = "";
        handler.removeCallbacks(backgroundRunnable);
        if (backgroundImageView != null) {
            backgroundImageView.setImageDrawable(null);
            backgroundImageView.setVisibility(View.GONE);
        }
        if (nativeBackgroundBitmap != null) {
            try { nativeBackgroundBitmap.recycle(); } catch (Exception ignored) {}
            nativeBackgroundBitmap = null;
        }
        if (webView != null) {
            webView.evaluateJavascript("(function(){var st=document.getElementById('ha-kiosk-bg-indicator'); if(st) st.remove(); var tr=document.getElementById('ha-android-kiosk-bg-transparent'); if(tr) tr.remove();})();", null);
        }
        revealWebViewIfReady(false);
        if (!applyingStoredConfig) {
            try { JSONObject bg = new JSONObject(); bg.put("enabled", false); updateCachedSection("background", bg); } catch (Exception ignored) {}
        }
        sendStatus("background_off", "clear_background");
    }

    private void applyWebViewTransparency(WebView view) {
        if (view == null) return;
        try {
            view.setBackgroundColor(transparentWebView ? Color.TRANSPARENT : Color.WHITE);
            if (Build.VERSION.SDK_INT >= 11) {
                // Wichtig fuer alte Android-6/RK3288-Tablets: keine Software-Layer erzwingen.
                // Ein Software-Layer rastert die komplette WebView in den Java-Heap und fuehrt
                // bei Dashboard-Zoom + Hintergrundbildern sehr schnell zu OutOfMemoryError.
                view.setLayerType(View.LAYER_TYPE_NONE, null);
            }
        } catch (Throwable ignored) {}
    }

    private void prepareFirstPageRevealGate() {
        if (webView == null || firstPageRevealed) return;
        if (waitForBackgroundBeforeLayout) {
            webView.setAlpha(0f);
            if (startupLoadingView != null) startupLoadingView.setVisibility(View.VISIBLE);
            handler.postDelayed(() -> rescueAndRevealDashboard(), 4500);
            handler.postDelayed(() -> revealWebViewIfReady(true), 8500);
        } else {
            firstBackgroundReady = true;
            revealWebViewIfReady(true);
        }
    }

    private void revealWebViewIfReady(boolean force) {
        if (webView == null || firstPageRevealed) return;
        boolean needsBackground = waitForBackgroundBeforeLayout && backgroundEnabled && !backgroundImages.isEmpty();
        if (!force) {
            if (!firstPageFinished) return;
            if (needsBackground && !firstBackgroundReady) return;
        }
        firstPageRevealed = true;
        try {
            webView.setVisibility(View.VISIBLE);
            webView.bringToFront();
            if (Build.VERSION.SDK_INT >= 12) webView.animate().cancel();
            if (Build.VERSION.SDK_INT >= 12 && firstRevealDelayMs > 0 && !force) {
                webView.setAlpha(0f);
                webView.animate().alpha(1f).setDuration(firstRevealDelayMs).start();
            } else {
                webView.setAlpha(1f);
            }
        } catch (Exception ignored) {
            webView.setAlpha(1f);
        }
        if (startupLoadingView != null) {
            startupLoadingView.setVisibility(View.GONE);
            startupLoadingView.bringToFront();
        }
        bringOverlaysToFront();
    }

    private void markBackgroundReady() {
        firstBackgroundReady = true;
        revealWebViewIfReady(false);
    }

    private void applyNativeBackground() {
        if (backgroundImageView == null || !backgroundEnabled || backgroundImages.isEmpty()) {
            firstBackgroundReady = true;
            revealWebViewIfReady(false);
            return;
        }
        final String raw = backgroundImages.get(Math.max(0, Math.min(backgroundIndex, backgroundImages.size() - 1)));
        final String url = resolveUrl(raw);
        applyBackgroundScaleType();
        backgroundImageView.setAlpha((float) Math.max(0.0, Math.min(1.0, backgroundOpacity / 100.0)));
        backgroundImageView.setVisibility(View.VISIBLE);

        if (url.equals(nativeBackgroundUrl) && nativeBackgroundBitmap != null) {
            markBackgroundReady();
            return;
        }
        if (url.equals(nativeBackgroundLoadingUrl)) {
            // Schon unterwegs. Keine zweite Decode-Thread-Kette starten.
            return;
        }
        long now = System.currentTimeMillis();
        if (url.equals(lastNativeBackgroundFailUrl) && now - lastNativeBackgroundFailAt < 10000L) {
            markBackgroundReady();
            return;
        }

        final int loadSeq;
        nativeBackgroundLoadingUrl = url;
        nativeBackgroundLoadSeq += 1;
        loadSeq = nativeBackgroundLoadSeq;
        firstBackgroundReady = false;

        new Thread(() -> {
            Bitmap bmp = null;
            Throwable failure = null;
            try {
                bmp = decodeScaledBitmapFromUrl(url);
            } catch (Throwable t) {
                failure = t;
            }
            final Bitmap finalBmp = bmp;
            final Throwable finalFailure = failure;
            handler.post(() -> {
                if (loadSeq != nativeBackgroundLoadSeq || !url.equals(nativeBackgroundLoadingUrl)) {
                    if (finalBmp != null) try { finalBmp.recycle(); } catch (Throwable ignored) {}
                    return;
                }
                nativeBackgroundLoadingUrl = "";
                if (finalBmp != null) {
                    if (nativeBackgroundBitmap != null && nativeBackgroundBitmap != finalBmp) {
                        try { nativeBackgroundBitmap.recycle(); } catch (Throwable ignored) {}
                    }
                    nativeBackgroundBitmap = finalBmp;
                    nativeBackgroundUrl = url;
                    lastNativeBackgroundFailUrl = "";
                    backgroundImageView.setImageBitmap(finalBmp);
                } else {
                    lastNativeBackgroundFailUrl = url;
                    lastNativeBackgroundFailAt = System.currentTimeMillis();
                    if (nativeBackgroundBitmap == null) {
                        // Kein Crash und kein schwarzer Bildschirm: Dashboard trotzdem zeigen.
                        try { backgroundImageView.setBackgroundColor(Color.TRANSPARENT); } catch (Throwable ignored) {}
                    }
                    String reason = finalFailure == null ? "decode_failed" : finalFailure.getClass().getSimpleName();
                    sendStatus("background_decode_failed", reason);
                }
                markBackgroundReady();
            });
        }, "ha-kiosk-bg-loader").start();
    }

    private void applyBackgroundScaleType() {
        if (backgroundImageView == null) return;
        String size = backgroundSize == null ? "cover" : backgroundSize.toLowerCase(Locale.US);
        if ("contain".equals(size)) backgroundImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        else if ("stretch".equals(size) || "fill".equals(size)) backgroundImageView.setScaleType(ImageView.ScaleType.FIT_XY);
        else backgroundImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
    }

    private HttpURLConnection openBackgroundConnection(String url) throws Exception {
        URL u = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent", HaAuthClient.USER_AGENT);
        if (isHomeAssistantUrl(url) && !AppSettings.token(this).isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + AppSettings.token(this));
        }
        return conn;
    }

    private Bitmap decodeScaledBitmapFromUrl(String url) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        HttpURLConnection first = openBackgroundConnection(url);
        try (InputStream input = new BufferedInputStream(first.getInputStream(), 32 * 1024)) {
            BitmapFactory.decodeStream(input, null, bounds);
        } finally {
            try { first.disconnect(); } catch (Throwable ignored) {}
        }

        int screenW = Math.max(1, getResources().getDisplayMetrics().widthPixels);
        int screenH = Math.max(1, getResources().getDisplayMetrics().heightPixels);
        int longEdge = Math.min(1280, Math.max(screenW, screenH));
        int shortEdge = Math.min(900, Math.min(screenW, screenH));
        int targetW = Math.max(320, screenW >= screenH ? longEdge : shortEdge);
        int targetH = Math.max(320, screenH >= screenW ? longEdge : shortEdge);

        int sample = calculateInSampleSize(bounds, targetW, targetH);
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = Math.max(1, sample);
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        opts.inDither = true;
        if (Build.VERSION.SDK_INT < 21) {
            opts.inPurgeable = true;
            opts.inInputShareable = true;
        }

        HttpURLConnection second = openBackgroundConnection(url);
        try (InputStream input = new BufferedInputStream(second.getInputStream(), 32 * 1024)) {
            return BitmapFactory.decodeStream(input, null, opts);
        } finally {
            try { second.disconnect(); } catch (Throwable ignored) {}
        }
    }

    private int calculateInSampleSize(BitmapFactory.Options bounds, int targetW, int targetH) {
        int width = bounds == null ? 0 : bounds.outWidth;
        int height = bounds == null ? 0 : bounds.outHeight;
        if (width <= 0 || height <= 0) return 2;
        int sample = 1;
        while ((width / sample) > targetW || (height / sample) > targetH) {
            sample *= 2;
            if (sample >= 16) break;
        }
        return Math.max(1, sample);
    }


    private void bringOverlaysToFront() {
        try { if (tickerView != null) tickerView.bringToFront(); } catch (Exception ignored) {}
        try { if (bannerView != null) bannerView.bringToFront(); } catch (Exception ignored) {}
        try { if (clockView != null) clockView.bringToFront(); } catch (Exception ignored) {}
        try { if (weatherView != null) weatherView.bringToFront(); } catch (Exception ignored) {}
        try { if (statusView != null) statusView.bringToFront(); } catch (Exception ignored) {}
        try { if (cameraView != null) cameraView.bringToFront(); } catch (Exception ignored) {}
        try { if (blackView != null) blackView.bringToFront(); } catch (Exception ignored) {}
        try { if (alertView != null) alertView.bringToFront(); } catch (Exception ignored) {}
    }

    private void rescueAndRevealDashboard() {
        firstBackgroundReady = true;
        firstPageFinished = true;
        rescueHomeAssistantLayout();
        applyDashboardZoom();
        revealWebViewIfReady(true);
    }

    private void rescueHomeAssistantLayout() {
        if (webView == null) return;
        String js = "(function(){try{" +
            "function q(r,s){try{return r&&r.querySelector?r.querySelector(s):null;}catch(e){return null;}}" +
            "function qa(r,s){try{return r&&r.querySelectorAll?r.querySelectorAll(s):[];}catch(e){return [];}}" +
            "function findDeep(root,selector){var hit=q(root,selector);if(hit)return hit;var all=qa(root,'*');for(var i=0;i<all.length;i++){if(all[i].shadowRoot){hit=findDeep(all[i].shadowRoot,selector);if(hit)return hit;}}return null;}" +
            "function fixVisible(el){if(!el)return;try{el.style.removeProperty('display');el.style.removeProperty('visibility');el.style.removeProperty('opacity');el.style.removeProperty('transform');el.removeAttribute('aria-hidden');el.style.setProperty('visibility','visible','important');el.style.setProperty('opacity','1','important');el.style.setProperty('pointer-events','auto','important');}catch(e){}}" +
            "var keep=['home-assistant','home-assistant-main','partial-panel-resolver','ha-panel-lovelace','hui-root','hui-view','hui-masonry-view','hui-sections-view','hui-panel-view'];" +
            "for(var i=0;i<keep.length;i++){var el=findDeep(document,keep[i]);fixVisible(el);}" +
            "try{window.dispatchEvent(new Event('resize'));}catch(e){}" +
            "}catch(e){}})();";
        try { webView.evaluateJavascript(js, null); } catch (Exception ignored) {}
    }

    private void scheduleHomeAssistantChromeRefresh() {
        if (!hideHomeAssistantHeader || webView == null) return;
        handler.removeCallbacks(kioskChromeRunnable);
        handler.postDelayed(kioskChromeRunnable, 80);
        handler.postDelayed(kioskChromeRunnable, 350);
        handler.postDelayed(kioskChromeRunnable, 900);
        handler.postDelayed(kioskChromeRunnable, 1800);
        handler.postDelayed(kioskChromeRunnable, 3600);
    }

    private void applyHomeAssistantHeaderVisibility() {
        if (webView == null) return;
        String js;
        if (!hideHomeAssistantHeader) {
            js = "(function(){try{" +
                "var ID='ha-android-kiosk-native-km';" +
                "function q(r,s){try{return r&&r.querySelector?r.querySelector(s):null;}catch(e){return null;}}" +
                "function qa(r,s){try{return r&&r.querySelectorAll?Array.prototype.slice.call(r.querySelectorAll(s)):[];}catch(e){return [];}}" +
                "function sr(el){return el&&el.shadowRoot?el.shadowRoot:null;}" +
                "function findMain(){var ha=q(document,'home-assistant');var root=sr(ha)||document;return q(root,'home-assistant-main');}" +
                "function findPanel(main){return main&&sr(main)?q(sr(main),'ha-panel-lovelace'):null;}" +
                "function findHuiRoot(main){var panel=findPanel(main);return panel&&sr(panel)?q(sr(panel),'hui-root'):null;}" +
                "function rmStyle(root){try{if(!root)return;var st=q(root,'style#'+ID);if(st)st.remove();}catch(e){}}" +
                "if(window.__haAndroidKioskNativeKmObserver){try{window.__haAndroidKioskNativeKmObserver.disconnect();}catch(e){}window.__haAndroidKioskNativeKmObserver=null;}" +
                "var main=findMain();var drawer=main&&sr(main)?q(sr(main),'ha-drawer'):null;var hui=findHuiRoot(main);" +
                "rmStyle(document.head);rmStyle(document.documentElement);rmStyle(sr(hui));rmStyle(sr(drawer));rmStyle(sr(main));rmStyle(drawer);rmStyle(hui);" +
                "if(main){try{main.removeEventListener('hass-toggle-menu',window.__haAndroidKioskNativeKmBlocker,true);}catch(e){}try{main.style.removeProperty('--mdc-drawer-width');main.style.removeProperty('--ha-sidebar-width');main.style.removeProperty('--kiosk-sidebar-width');main.style.removeProperty('--mdc-top-app-bar-width');}catch(e){}}" +
                "if(drawer){try{drawer.style.removeProperty('--mdc-drawer-width');drawer.style.removeProperty('--ha-sidebar-width');drawer.style.removeProperty('--kiosk-sidebar-width');drawer.style.removeProperty('--mdc-top-app-bar-width');}catch(e){}}" +
                "try{window.dispatchEvent(new Event('resize'));}catch(e){}" +
                "}catch(e){console.warn('HA Android Kiosk native kiosk reset failed',e);}})();";
            webView.evaluateJavascript(js, null);
            return;
        }
        js = "(function(){try{" +
            "var ID='ha-android-kiosk-native-km';" +
            "function q(r,s){try{return r&&r.querySelector?r.querySelector(s):null;}catch(e){return null;}}" +
            "function qa(r,s){try{return r&&r.querySelectorAll?Array.prototype.slice.call(r.querySelectorAll(s)):[];}catch(e){return [];}}" +
            "function sr(el){return el&&el.shadowRoot?el.shadowRoot:null;}" +
            "function findMain(){var ha=q(document,'home-assistant');var root=sr(ha)||document;return q(root,'home-assistant-main');}" +
            "function findPanel(main){return main&&sr(main)?q(sr(main),'ha-panel-lovelace'):null;}" +
            "function findHuiRoot(main){var panel=findPanel(main);return panel&&sr(panel)?q(sr(panel),'hui-root'):null;}" +
            "function putStyle(root,css){try{if(!root)return false;var st=q(root,'style#'+ID);if(!st){st=document.createElement('style');st.id=ID;(root.head||root.documentElement||root.body||root).appendChild(st);}if(st.textContent!==css)st.textContent=css;return true;}catch(e){return false;}}" +
            "function removeStyle(root){try{var st=q(root,'style#'+ID);if(st)st.remove();}catch(e){}}" +
            "function blockEvent(ev){try{ev.preventDefault();ev.stopImmediatePropagation();}catch(e){}}" +
            "window.__haAndroidKioskNativeKmBlocker=blockEvent;" +
            "function apply(){" +
            "var main=findMain();if(!main||!sr(main))return false;var mainRoot=sr(main);var panel=findPanel(main);var hui=findHuiRoot(main);var drawer=q(mainRoot,'ha-drawer');" +
            "var headerCss='#view{min-height:100vh!important;--kiosk-header-height:0px!important;padding-top:calc(var(--kiosk-header-height) + env(safe-area-inset-top))!important;} .header{display:none!important;} app-toolbar{display:none!important;} div.toolbar{display:none!important;} ha-menu-button{display:none!important;}';" +
            "var drawerCss=':host{--mdc-drawer-width:0px!important;--ha-sidebar-width:0px!important;--kiosk-sidebar-width:0px!important;} partial-panel-resolver{--mdc-top-app-bar-width:100%!important;} .header{width:100%!important;} ha-sidebar,.sidebar-shell{display:none!important;}';" +
            "var asideCss='.mdc-drawer{display:none!important;} aside{display:none!important;} .sidebar-shell{display:none!important;}';" +
            "if(hui&&sr(hui))putStyle(sr(hui),headerCss);" +
            "if(drawer){putStyle(sr(drawer),drawerCss+' '+asideCss);putStyle(drawer,drawerCss);try{drawer.style.setProperty('--mdc-drawer-width','0px','important');drawer.style.setProperty('--ha-sidebar-width','0px','important');drawer.style.setProperty('--kiosk-sidebar-width','0px','important');drawer.style.setProperty('--mdc-top-app-bar-width','100%','important');}catch(e){}}" +
            "try{main.style.setProperty('--mdc-drawer-width','0px','important');main.style.setProperty('--ha-sidebar-width','0px','important');main.style.setProperty('--kiosk-sidebar-width','0px','important');main.style.setProperty('--mdc-top-app-bar-width','100%','important');}catch(e){}" +
            "try{main.removeEventListener('hass-toggle-menu',blockEvent,true);main.addEventListener('hass-toggle-menu',blockEvent,true);}catch(e){}" +
            "try{var appToolbar=null;if(hui&&sr(hui)){appToolbar=q(sr(hui),'app-toolbar')||q(sr(hui),'div.toolbar')||q(sr(hui),'.header');} if(appToolbar){putStyle(appToolbar,'ha-menu-button{display:none!important;} mwc-icon-button,ha-icon-button{display:none!important;}');}}catch(e){}" +
            "try{var view=hui&&sr(hui)?q(sr(hui),'#view'):null;if(view){view.style.setProperty('min-height','100vh','important');view.style.setProperty('padding-top','env(safe-area-inset-top)','important');view.style.setProperty('margin-top','0','important');}}catch(e){}" +
            "try{var r=q(document,'home-assistant')||document.documentElement;putStyle(document.head,'home-assistant{--mdc-drawer-width:0px!important;--ha-sidebar-width:0px!important;--kiosk-sidebar-width:0px!important;}');}catch(e){}" +
            "try{window.dispatchEvent(new Event('resize'));}catch(e){}" +
            "return true;" +
            "}" +
            "if(window.__haAndroidKioskNativeKmObserver){try{window.__haAndroidKioskNativeKmObserver.disconnect();}catch(e){}}" +
            "var pending=0;window.__haAndroidKioskNativeKmApply=apply;window.__haAndroidKioskNativeKmObserver=new MutationObserver(function(){clearTimeout(pending);pending=setTimeout(apply,80);});" +
            "try{window.__haAndroidKioskNativeKmObserver.observe(document.documentElement||document,{childList:true,subtree:true,attributes:false});}catch(e){}" +
            "apply();setTimeout(apply,80);setTimeout(apply,250);setTimeout(apply,700);setTimeout(apply,1500);setTimeout(apply,3000);setTimeout(apply,6000);" +
            "}catch(e){console.warn('HA Android Kiosk native kiosk-mode injection failed',e);}})();";
        webView.evaluateJavascript(js, null);
    }

    private int clampZoom(int value) {
        if (value <= 0) return 100;
        if (value < 50) return 50;
        if (value > 200) return 200;
        return value;
    }

    private int readZoomFromPayload(JSONObject payload, int fallback) {
        if (payload == null) return fallback;
        int value = fallback;
        if (payload.has("zoom_percent")) value = payload.optInt("zoom_percent", fallback);
        else if (payload.has("zoom")) value = payload.optInt("zoom", fallback);
        else if (payload.has("scale_percent")) value = payload.optInt("scale_percent", fallback);
        else if (payload.has("page_scale")) value = (int)Math.round(payload.optDouble("page_scale", fallback > 0 ? fallback / 100.0 : 1.0) * 100.0);
        return value > 0 ? clampZoom(value) : fallback;
    }

    private void scheduleDashboardZoomRefresh() {
        if (webView == null) return;
        handler.removeCallbacks(dashboardZoomRunnable);
        handler.postDelayed(dashboardZoomRunnable, 120);
        handler.postDelayed(dashboardZoomRunnable, 450);
        handler.postDelayed(dashboardZoomRunnable, 1200);
        handler.postDelayed(dashboardZoomRunnable, 2600);
        if (backgroundEnabled) {
            handler.postDelayed(() -> applyBackgroundTransparencyForZoom(), 300);
        }
    }

    private void applyBackgroundTransparencyForZoom() {
        if (webView == null || !backgroundEnabled) return;
        applyWebViewTransparency(webView);
        String js = "(function(){try{" +
            "var ID='ha-android-kiosk-bg-transparent';" +
            "var CSS='html,body,home-assistant,home-assistant-main,partial-panel-resolver,ha-panel-lovelace,hui-root,hui-view,hui-masonry-view,hui-sections-view,hui-panel-view,hui-panel-view > *,#view,.view,.content,.root,.container,#columns,.columns,.column,hui-section,hui-card,hui-card-preview{background:transparent!important;background-color:transparent!important;} body:before,body:after{pointer-events:none!important;} home-assistant,ha-panel-lovelace,hui-root,hui-view{--primary-background-color:transparent!important;--lovelace-background:transparent!important;}';" +
            "var TAGS={'HOME-ASSISTANT':1,'HOME-ASSISTANT-MAIN':1,'PARTIAL-PANEL-RESOLVER':1,'HA-PANEL-LOVELACE':1,'HUI-ROOT':1,'HUI-VIEW':1,'HUI-MASONRY-VIEW':1,'HUI-SECTIONS-VIEW':1,'HUI-PANEL-VIEW':1,'HUI-SECTION':1,'HUI-CARD':1,'HUI-CARD-PREVIEW':1};" +
            "function q(r,s){try{return r&&r.querySelector?r.querySelector(s):null;}catch(e){return null;}}" +
            "function qa(r,s){try{return r&&r.querySelectorAll?Array.prototype.slice.call(r.querySelectorAll(s)):[];}catch(e){return [];}}" +
            "function put(root){try{if(!root)return;var st=q(root,'style#'+ID);if(!st){st=document.createElement('style');st.id=ID;(root.head||root.documentElement||root.body||root).appendChild(st);}if(st.textContent!==CSS)st.textContent=CSS;}catch(e){}}" +
            "function transparent(el){try{if(!el)return;var tag=el.tagName||'';if(tag==='HA-CARD'||tag.indexOf('CARD')>0&&tag.indexOf('HUI-')!==0)return;el.style.setProperty('background','transparent','important');el.style.setProperty('background-color','transparent','important');el.style.setProperty('--primary-background-color','transparent','important');el.style.setProperty('--lovelace-background','transparent','important');}catch(e){}}" +
            "function walk(root){put(root);var all=qa(root,'*');for(var i=0;i<all.length;i++){var el=all[i];var tag=el.tagName||'';if(TAGS[tag]||el.id==='view'||el.classList&&(['view','content','root','container','columns','column'].some(function(c){return el.classList.contains(c);})))transparent(el);if(el.shadowRoot)walk(el.shadowRoot);}}" +
            "transparent(document.documentElement);transparent(document.body);walk(document);" +
            "}catch(e){console.warn('HA Android Kiosk background transparency failed',e);}})();";
        try { webView.evaluateJavascript(js, null); } catch (Throwable ignored) {}
    }

    private int effectiveDashboardZoom() {
        int pageZoom = currentPageZoomPercent > 0 ? currentPageZoomPercent : browserZoomPercent;
        return clampZoom(pageZoom > 0 ? pageZoom : 100);
    }

    private void applyDashboardZoom() {
        if (webView == null) return;
        final int zoomPercent = effectiveDashboardZoom();
        final String zoom = String.format(Locale.US, "%.4f", zoomPercent / 100.0);
        String js = "(function(){try{" +
            "var ID='ha-android-kiosk-page-scale';var Z=" + zoom + ";var P=" + zoomPercent + ";" +
            "var TRANSPARENT='ha-panel-lovelace,hui-root,hui-view,hui-sections-view,hui-panel-view,#view,hui-masonry-view{background-color:transparent!important;}';" +
            "function q(r,s){try{return r&&r.querySelector?r.querySelector(s):null;}catch(e){return null;}}" +
            "function sr(el){return el&&el.shadowRoot?el.shadowRoot:null;}" +
            "function putStyle(root,css){try{if(!root)return false;var st=q(root,'style#'+ID);if(!st){st=document.createElement('style');st.id=ID;(root.head||root.documentElement||root.body||root).appendChild(st);}if(st.textContent!==css)st.textContent=css;return true;}catch(e){return false;}}" +
            "function rmStyle(root){try{if(!root)return;var st=q(root,'style#'+ID);if(st)st.remove();}catch(e){}}" +
            "function findMain(){var ha=q(document,'home-assistant');var root=sr(ha)||document;return q(root,'home-assistant-main');}" +
            "function findPanel(main){return main&&sr(main)?q(sr(main),'ha-panel-lovelace'):null;}" +
            "function findHuiRoot(main){var panel=findPanel(main);return panel&&sr(panel)?q(sr(panel),'hui-root'):null;}" +
            "function makeTransparent(main,hui,root){try{if(document.documentElement)document.documentElement.style.setProperty('background-color','transparent','important');if(document.body)document.body.style.setProperty('background-color','transparent','important');if(main)main.style.setProperty('background-color','transparent','important');if(hui)hui.style.setProperty('background-color','transparent','important');if(root){var view=q(root,'#view');if(view)view.style.setProperty('background-color','transparent','important');}}catch(e){}}" +
            "function apply(){var main=findMain();var hui=findHuiRoot(main);var root=hui&&sr(hui);makeTransparent(main,hui,root);" +
            "if(P===100){rmStyle(root);try{if(document.body)document.body.style.removeProperty('zoom');}catch(e){}try{if(root){var view=q(root,'#view');if(view){view.style.removeProperty('zoom');view.style.removeProperty('transform');view.style.removeProperty('transform-origin');view.style.removeProperty('width');view.style.removeProperty('min-height');view.style.removeProperty('margin-left');view.style.removeProperty('margin-right');view.style.removeProperty('padding-left');view.style.removeProperty('padding-right');view.style.setProperty('background-color','transparent','important');}}}catch(e){}try{window.dispatchEvent(new Event('resize'));}catch(e){}return true;}" +
            "if(!root)return false;" +
            "var css=TRANSPARENT+' #view{zoom:'+Z+'!important;transform:none!important;transform-origin:top center!important;width:calc(100% / '+Z+')!important;min-height:calc(100vh / '+Z+')!important;margin-left:auto!important;margin-right:auto!important;padding-left:0!important;padding-right:0!important;background-color:transparent!important;} hui-masonry-view,hui-sections-view,hui-panel-view{max-width:100%!important;background-color:transparent!important;}';" +
            "putStyle(root,css);try{var view=q(root,'#view');if(view){view.style.setProperty('zoom',String(Z),'important');view.style.setProperty('transform','none','important');view.style.setProperty('transform-origin','top center','important');view.style.setProperty('width','calc(100% / '+Z+')','important');view.style.setProperty('min-height','calc(100vh / '+Z+')','important');view.style.setProperty('margin-left','auto','important');view.style.setProperty('margin-right','auto','important');view.style.setProperty('background-color','transparent','important');}}catch(e){}" +
            "try{window.dispatchEvent(new Event('resize'));}catch(e){}return true;}" +
            "if(window.__haAndroidKioskPageScaleObserver){try{window.__haAndroidKioskPageScaleObserver.disconnect();}catch(e){}}" +
            "var pending=0;window.__haAndroidKioskPageScaleApply=apply;window.__haAndroidKioskPageScaleObserver=new MutationObserver(function(){clearTimeout(pending);pending=setTimeout(apply,80);});" +
            "try{window.__haAndroidKioskPageScaleObserver.observe(document.documentElement||document,{childList:true,subtree:true,attributes:false});}catch(e){}" +
            "apply();setTimeout(apply,120);setTimeout(apply,500);setTimeout(apply,1500);" +
            "}catch(e){console.warn('HA Android Kiosk page scale failed',e);}})();";
        try { webView.evaluateJavascript(js, null); } catch (Exception ignored) {}
        if (backgroundEnabled) {
            handler.postDelayed(() -> applyBackgroundTransparencyForZoom(), 200);
        }
    }

    private int zoomForPath(String pathOrUrl) {
        if (pathOrUrl == null) return browserZoomPercent;
        String target = normalizeUrlForMatch(pathOrUrl);
        for (PageSpec page : pages) {
            if (page != null && page.zoomPercent > 0 && normalizeUrlForMatch(page.url).equals(target)) return clampZoom(page.zoomPercent);
        }
        return browserZoomPercent;
    }

    private String normalizeUrlForMatch(String value) {
        String text = value == null ? "" : value.trim();
        try {
            Uri uri = Uri.parse(resolveUrl(text));
            String path = uri.getPath() == null ? "" : uri.getPath();
            String query = uri.getQuery();
            if (query != null && !query.isEmpty()) {
                String[] parts = query.split("&");
                StringBuilder clean = new StringBuilder();
                for (String part : parts) {
                    if (part == null || part.startsWith("external_auth=")) continue;
                    if (clean.length() > 0) clean.append('&');
                    clean.append(part);
                }
                query = clean.toString();
            }
            String fragment = uri.getFragment();
            return path + (query == null || query.isEmpty() ? "" : "?" + query) + (fragment == null || fragment.isEmpty() ? "" : "#" + fragment);
        } catch (Exception ignored) {
            return text;
        }
    }

    private void fetchStoredSettingsFromHomeAssistant() {
        fetchStoredSettingsFromHomeAssistant(null, false);
    }

    private void fetchStoredSettingsFromHomeAssistant(final Runnable afterFetch) {
        fetchStoredSettingsFromHomeAssistant(afterFetch, false);
    }

    private void fetchStoredSettingsFromHomeAssistant(final Runnable afterFetch, final boolean force) {
        if (restClient == null) { if (afterFetch != null) handler.post(afterFetch); return; }
        long now = System.currentTimeMillis();
        if (!force && settingsSyncInFlight) { if (afterFetch != null) handler.post(afterFetch); return; }
        if (!force && lastSettingsSyncAt > 0 && now - lastSettingsSyncAt < 15000L) { if (afterFetch != null) handler.post(afterFetch); return; }
        settingsSyncInFlight = true;
        new Thread(() -> {
            try {
                JSONObject resp = restClient.get("/api/ha_android_kiosk/device_config/" + Uri.encode(AppSettings.deviceId(this)));
                if (resp != null && resp.optBoolean("ok", false)) {
                    JSONObject device = resp.optJSONObject("device");
                    if (device != null) saveCachedDeviceConfig(device, "ha_fetch");
                    handler.post(() -> {
                        settingsSyncInFlight = false;
                        lastSettingsSyncAt = System.currentTimeMillis();
                        AppSettings.putLong(this, "last_settings_sync_at", lastSettingsSyncAt);
                        lastSettingsSyncStatus = "ok";
                        if (device != null) applyStoredDeviceConfig(device, "ha_fetch");
                        initialConfigFetchCompleted = true;
                        if (afterFetch != null) afterFetch.run();
                    });
                    return;
                }
                throw new IllegalStateException(resp == null ? "Leere Antwort" : resp.optString("error", "config_not_found"));
            } catch (Exception e) {
                final String message = e.getMessage() == null ? String.valueOf(e) : e.getMessage();
                handler.post(() -> {
                    settingsSyncInFlight = false;
                    lastSettingsSyncStatus = "fehler: " + message;
                    initialConfigFetchCompleted = true;
                    if (!startupPageLoadStarted) applyCachedStoredConfig();
                    if (afterFetch != null) afterFetch.run();
                });
            }
        }, "ha-kiosk-fetch-config").start();
    }

    private void scheduleSettingsSync() {
        handler.removeCallbacks(settingsSyncRunnable);
        if (settingsSyncIntervalSeconds <= 0) return;
        handler.postDelayed(settingsSyncRunnable, Math.max(30, settingsSyncIntervalSeconds) * 1000L);
    }

    private void scheduleVisualWatchdog() {
        handler.removeCallbacks(visualWatchdogRunnable);
        if (!visualWatchdogEnabled || visualWatchdogSeconds <= 0) return;
        handler.postDelayed(visualWatchdogRunnable, Math.max(10, visualWatchdogSeconds) * 1000L);
    }

    private void reapplyVisualState(String reason) {
        if (webView == null) return;
        if (fullscreenEnabled) applyFullscreen(true);
        if (backgroundEnabled) {
            applyNativeBackground();
            applyCurrentBackgroundLight();
        }
        scheduleHomeAssistantChromeRefresh();
        scheduleDashboardZoomRefresh();
        handler.postDelayed(() -> rescueHomeAssistantLayout(), 350);
        handler.postDelayed(() -> revealWebViewIfReady(true), 650);
        if ("manual".equals(reason) || "sync".equals(reason)) sendStatus("visual_recovered", reason);
    }

    private void applyCachedStoredConfig() {
        try {
            String raw = AppSettings.get(this, "cached_device_config_json", "");
            if (raw == null || raw.trim().isEmpty()) return;
            JSONObject device = new JSONObject(raw);
            applyStoredDeviceConfig(device, "local_cache");
            lastSettingsSyncStatus = "lokaler cache";
        } catch (Exception e) {
            lastSettingsSyncStatus = "cache fehler";
        }
    }

    private JSONObject cachedDeviceConfigOrNew() {
        try {
            String raw = AppSettings.get(this, "cached_device_config_json", "");
            if (raw != null && !raw.trim().isEmpty()) return new JSONObject(raw);
        } catch (Exception ignored) {}
        JSONObject obj = new JSONObject();
        try {
            obj.put("device_id", AppSettings.deviceId(this));
            obj.put("name", AppSettings.get(this, "device_name", Build.MANUFACTURER + " " + Build.MODEL));
        } catch (Exception ignored) {}
        return obj;
    }

    private void saveCachedDeviceConfig(JSONObject device, String source) {
        if (device == null) return;
        try {
            JSONObject copy = new JSONObject(device.toString());
            copy.put("cached_by_app", true);
            copy.put("cached_source", source == null ? "unknown" : source);
            copy.put("cached_at", System.currentTimeMillis());
            AppSettings.put(this, "cached_device_config_json", copy.toString());
        } catch (Exception ignored) {}
    }

    private void updateCachedSection(String section, JSONObject payload) {
        if (applyingStoredConfig || section == null || payload == null) return;
        try {
            JSONObject cfg = cachedDeviceConfigOrNew();
            JSONObject merged = cfg.optJSONObject(section);
            if (merged == null) merged = new JSONObject();
            JSONArray names = payload.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String key = names.optString(i, "");
                    if (!key.isEmpty()) merged.put(key, payload.opt(key));
                }
            }
            cfg.put(section, merged);
            saveCachedDeviceConfig(cfg, "command_" + section);
        } catch (Exception ignored) {}
    }

    private void updateCachedRotation(JSONObject payload) {
        if (applyingStoredConfig || payload == null) return;
        try {
            JSONObject cfg = cachedDeviceConfigOrNew();
            JSONArray arr = payload.optJSONArray("pages");
            if (arr != null) cfg.put("pages", new JSONArray(arr.toString()));
            cfg.put("duration", payload.optInt("duration", 15));
            cfg.put("auto_rotate", payload.optBoolean("auto_rotate", payload.optBoolean("enabled", true)));
            cfg.put("pause_on_touch", payload.optBoolean("pause_on_touch", true));
            cfg.put("touch_pause", payload.optInt("touch_pause", payload.optInt("touch_pause_seconds", 30)));
            saveCachedDeviceConfig(cfg, "command_rotation");
        } catch (Exception ignored) {}
    }

    private void updateCachedDeviceSettings(JSONObject settings) {
        if (applyingStoredConfig || settings == null) return;
        try {
            JSONObject cfg = cachedDeviceConfigOrNew();
            JSONObject existing = cfg.optJSONObject("device_settings");
            if (existing == null) existing = new JSONObject();
            JSONArray names = settings.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String key = names.optString(i, "");
                    if (!key.isEmpty()) existing.put(key, settings.opt(key));
                }
            }
            cfg.put("device_settings", existing);
            saveCachedDeviceConfig(cfg, "command_device_settings");
        } catch (Exception ignored) {}
    }

    private void applyStoredDeviceConfig(JSONObject device) {
        applyStoredDeviceConfig(device, "stored");
    }

    private void applyStoredDeviceConfig(JSONObject device, String source) {
        if (device == null) return;
        boolean oldApplying = applyingStoredConfig;
        try {
            applyingStoredConfig = true;
            JSONObject browser = device.optJSONObject("browser");
            if (browser != null) applyBrowserConfig(browser);
            JSONObject background = device.optJSONObject("background");
            if (background != null && background.optBoolean("enabled", false)) configureBackgroundSlideshow(background);
            else firstBackgroundReady = true;
            JSONArray pageArr = device.optJSONArray("pages");
            if (pageArr != null && pageArr.length() > 0) {
                JSONObject p = new JSONObject();
                p.put("pages", pageArr);
                p.put("duration", device.optInt("duration", 15));
                p.put("auto_rotate", device.optBoolean("auto_rotate", true));
                p.put("pause_on_touch", device.optBoolean("pause_on_touch", true));
                p.put("touch_pause", device.optInt("touch_pause", 30));
                configureRotation(p, false);
            }
            JSONObject media = device.optJSONObject("media");
            if (media != null) applyAudioSettingsFromPayload(media);
            JSONObject settings = device.optJSONObject("device_settings");
            if (settings != null) {
                if (settings.has("brightness")) setBrightness(settings.optDouble("brightness", 80), settings.optBoolean("brightness_system", false));
                if (settings.has("volume")) setVolume(settings.optDouble("volume", 50), preferredAudioStream);
                if (settings.has("orientation")) setOrientation(settings.optString("orientation", "unspecified"));
                if (settings.has("screen_power")) setScreen(settings.optBoolean("screen_power", true) ? "on" : "blank");
                if (settings.optBoolean("accelerometer_motion", false)) startMotionDetection(); else stopMotionDetection();
                if (settings.optBoolean("camera_motion", false)) setCameraMotionDetection(true, "front"); else setCameraMotionDetection(false, "front");
                if (settings.optBoolean("light_sensor", true)) startLightSensor(); else stopLightSensor();
            }
            applyingStoredConfig = oldApplying;
            reapplyVisualState("sync");
            sendStatus("settings_synced_" + (source == null ? "stored" : source), "fetch_config");
        } catch (Exception e) {
            applyingStoredConfig = oldApplying;
            firstBackgroundReady = true;
            sendStatus("settings_sync_error: " + e.getMessage(), "fetch_config");
        }
    }

    private void showOverlay(JSONObject payload) {
        String type = payload.optString("type", "banner");
        if ("ticker".equals(type)) showTickerPayload(payload);
        else if ("toast".equals(type)) showToastPayload(payload);
        else if ("alert".equals(type) || "fullscreen".equals(type)) showAlertPayload(payload);
        else if ("clock".equals(type)) showClockPayload(payload);
        else if ("weather".equals(type)) showWeatherPayload(payload);
        else if ("camera".equals(type)) showCameraPayload(payload);
        else showBannerPayload(payload);
    }


    private void showTickerPayload(JSONObject payload) {
        if (payload.optBoolean("wake_screen", false)) setScreen("on");
        String text = payload.optString("text", payload.optString("message", ""));
        JSONArray messages = payload.optJSONArray("messages");
        if ((text == null || text.isEmpty()) && messages != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < messages.length(); i++) {
                if (i > 0) sb.append("   •   ");
                Object item = messages.opt(i);
                if (item instanceof JSONObject) {
                    JSONObject obj = (JSONObject) item;
                    String icon = obj.optString("icon", "");
                    if (!icon.isEmpty()) sb.append(icon).append(" ");
                    sb.append(obj.optString("text", obj.optString("message", obj.toString())));
                } else {
                    sb.append(String.valueOf(item));
                }
            }
            text = sb.toString();
        }
        String icon = payload.optString("icon", "");
        if (!icon.isEmpty() && text != null && !text.startsWith(icon)) text = icon + " " + text;
        applyViewColors(tickerView, payload);
        showTicker(text, payload.optBoolean("enabled", payload.optBoolean("visible", true)));
        int duration = payload.optInt("duration", payload.optInt("auto_hide_seconds", 0));
        if (duration > 0) handler.postDelayed(() -> showTicker("", false), duration * 1000L);
    }

    private void updateTickerConfig(JSONObject payload) {
        boolean enabled = payload.optBoolean("enabled", true);
        String text = payload.optString("text", payload.optString("message", ""));
        if (text.isEmpty()) {
            JSONArray messages = payload.optJSONArray("fixed_messages");
            if (messages == null) messages = payload.optJSONArray("messages");
            if (messages != null) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < messages.length(); i++) {
                    if (i > 0) sb.append("   •   ");
                    sb.append(messages.optString(i));
                }
                text = sb.toString();
            }
        }
        applyViewColors(tickerView, payload);
        int height = payload.optInt("height", 0);
        if (height > 0) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) tickerView.getLayoutParams();
            lp.height = dp(height);
            String pos = payload.optString("position", "bottom").toLowerCase(Locale.US);
            lp.gravity = "top".equals(pos) ? Gravity.TOP : Gravity.BOTTOM;
            tickerView.setLayoutParams(lp);
        }
        showTicker(text, enabled && !text.isEmpty());
    }

    private void showToastPayload(JSONObject payload) {
        if (payload.optBoolean("wake_screen", false)) setScreen("on");
        String title = payload.optString("title", "");
        String message = payload.optString("text", payload.optString("message", ""));
        String text = title.isEmpty() ? message : title + ": " + message;
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    private void showBannerPayload(JSONObject payload) {
        if (payload.optBoolean("wake_screen", false)) setScreen("on");
        String text = joinTitleMessage(payload);
        applyViewColors(bannerView, payload);
        showBanner(text, payload.optInt("duration", 0));
        maybeSoundVibrateTts(payload);
    }

    private void showAlertPayload(JSONObject payload) {
        if (payload.optBoolean("wake_screen", true)) setScreen("on");
        String mode = payload.optString("mode", "fullscreen");
        if ("toast".equals(mode)) {
            showToastPayload(payload);
        } else if ("banner".equals(mode)) {
            showBannerPayload(payload);
        } else {
            String text = joinTitleMessage(payload);
            applyViewColors(alertText, payload);
            showAlert(text, payload.optInt("duration", 0));
        }
        maybeSoundVibrateTts(payload);
    }

    private void showClockPayload(JSONObject payload) {
        if (payload.optBoolean("wake_screen", false)) setScreen("on");
        applyViewColors(clockView, payload);
        clockView.setVisibility(payload.optBoolean("visible", true) ? View.VISIBLE : View.GONE);
        int duration = payload.optInt("duration", 0);
        if (duration > 0) handler.postDelayed(() -> clockView.setVisibility(View.GONE), duration * 1000L);
    }

    private void showWeatherPayload(JSONObject payload) {
        if (payload.optBoolean("wake_screen", false)) setScreen("on");
        String title = payload.optString("title", "Wetter");
        String text = payload.optString("text", payload.optString("message", payload.optString("entity_id", "")));
        weatherView.setText(title.isEmpty() ? text : title + "\n" + text);
        applyViewColors(weatherView, payload);
        weatherView.setVisibility(View.VISIBLE);
        int duration = payload.optInt("duration", 0);
        if (duration > 0) handler.postDelayed(() -> weatherView.setVisibility(View.GONE), duration * 1000L);
    }

    private void showCameraPayload(JSONObject payload) {
        if (payload.optBoolean("wake_screen", false)) setScreen("on");
        String url = payload.optString("url", payload.optString("media_url", payload.optString("path", "")));
        String entityId = payload.optString("entity_id", "");
        if (url.isEmpty() && entityId.startsWith("camera.")) url = "/api/camera_proxy/" + entityId;
        showCamera(url, payload.optBoolean("visible", true));
        int duration = payload.optInt("duration", 0);
        if (duration > 0) handler.postDelayed(() -> cameraView.setVisibility(View.GONE), duration * 1000L);
    }

    private String joinTitleMessage(JSONObject payload) {
        String title = payload.optString("title", "");
        String message = payload.optString("text", payload.optString("message", ""));
        if (title.isEmpty()) return message;
        if (message.isEmpty()) return title;
        return title + "\n" + message;
    }

    private void applyViewColors(TextView view, JSONObject payload) {
        String color = payload.optString("color", "");
        if (!color.isEmpty()) {
            try { view.setBackgroundColor(Color.parseColor(color)); } catch (Exception ignored) {}
        }
        String textColor = payload.optString("text_color", payload.optString("foreground", ""));
        if (!textColor.isEmpty()) {
            try { view.setTextColor(Color.parseColor(textColor)); } catch (Exception ignored) {}
        }
    }

    private void maybeSoundVibrateTts(JSONObject payload) {
        applyAudioSettingsFromPayload(payload);
        if (payload.optBoolean("vibrate", false)) vibrate(payload.optInt("vibrate_duration", 500));
        String tts = payload.optString("tts_message", "");
        if (!tts.isEmpty()) speakText(tts, payload.optString("tts_language", "de-DE"), payload.optDouble("volume", payload.optDouble("tts_volume", ttsVolumePercent)), payload.optString("stream", preferredAudioStream));
        String soundUrl = payload.optString("sound_url", "");
        if (!soundUrl.isEmpty()) {
            double volume = payload.optDouble("volume", payload.optDouble("media_volume", mediaVolumePercent));
            if (volume >= 0) setVolume(volume, payload.optString("stream", preferredAudioStream));
            playMedia(soundUrl);
        }
    }

    private void goRelativeScreen(int delta) {
        if (pages.isEmpty()) return;
        currentPageIndex = (currentPageIndex + delta + pages.size()) % pages.size();
        PageSpec page = pages.get(currentPageIndex);
        loadPage(page.url, page.zoomPercent);
        scheduleRotation();
    }

    private void gotoScreen(String idOrPath) {
        if (idOrPath == null || idOrPath.trim().isEmpty()) return;
        for (int i = 0; i < pages.size(); i++) {
            PageSpec p = pages.get(i);
            if (idOrPath.equals(String.valueOf(i)) || idOrPath.equals(p.url) || p.url.endsWith(idOrPath)) {
                currentPageIndex = i;
                loadPage(p.url, p.zoomPercent);
                scheduleRotation();
                return;
            }
        }
        loadPage(idOrPath);
    }

    private void pauseRotation(int seconds) {
        rotationPausedUntil = seconds > 0 ? System.currentTimeMillis() + seconds * 1000L : Long.MAX_VALUE;
        handler.removeCallbacks(rotationRunnable);
    }

    private void identifyDevice() {
        showBanner("Android Kiosk\n" + AppSettings.deviceId(this), 6);
        vibrate(250);
    }

    private void openAndroidSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void setDeviceSetting(String setting, Object value) {
        String key = setting == null ? "" : setting.toLowerCase(Locale.US);
        boolean enabled = value instanceof Boolean ? (Boolean) value : "true".equalsIgnoreCase(String.valueOf(value)) || "on".equalsIgnoreCase(String.valueOf(value));
        if ("motion_detection".equals(key) || "accelerometer_motion".equals(key)) {
            if (enabled) startMotionDetection(); else stopMotionDetection();
        } else if ("camera_motion_detection".equals(key)) {
            setCameraMotionDetection(enabled, "front");
        } else if ("front_camera".equals(key) || "camera_front".equals(key)) {
            setDeviceCamera("front", enabled, cameraMotionEnabled);
        } else if ("hide_header".equals(key) || "hide_ha_header".equals(key)) {
            hideHomeAssistantHeader = enabled;
            AppSettings.putBoolean(this, "hide_ha_header", enabled);
            applyHomeAssistantHeaderVisibility();
        } else if ("light_sensor".equals(key)) {
            if (enabled) startLightSensor(); else stopLightSensor();
        } else if ("keep_screen_on".equals(key)) {
            if (enabled) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else if ("fullscreen".equals(key)) {
            applyFullscreen(enabled);
        } else if ("wait_for_background".equals(key) || "background_first".equals(key)) {
            waitForBackgroundBeforeLayout = enabled;
            AppSettings.putBoolean(this, "wait_for_background", enabled);
        } else if ("transparent_webview".equals(key)) {
            transparentWebView = enabled;
            AppSettings.putBoolean(this, "transparent_webview", enabled);
            applyWebViewTransparency(webView);
        } else if ("force_mobile_viewport".equals(key)) {
            forceMobileViewport = enabled;
            AppSettings.putBoolean(this, "force_mobile_viewport", enabled);
            if (webView != null) {
                webView.getSettings().setLoadWithOverviewMode(!forceMobileViewport);
                webView.getSettings().setUseWideViewPort(!forceMobileViewport);
            }
        }
    }

    private void playMediaFromPayload(JSONObject payload) {
        applyAudioSettingsFromPayload(payload);
        double volume = payload.optDouble("volume", payload.optDouble("media_volume", -1));
        if (volume >= 0) setVolume(volume, payload.optString("stream", preferredAudioStream));
        else if (mediaVolumePercent > 0) setVolume(mediaVolumePercent, preferredAudioStream);
        requestAudioFocusIfNeeded(audioStreamForName(preferredAudioStream));
        mediaLoop = payload.optBoolean("loop", mediaLoop);

        ArrayList<String> urls = new ArrayList<>();
        JSONArray playlist = payload.optJSONArray("playlist");
        if (playlist != null) {
            for (int i = 0; i < playlist.length(); i++) {
                String item = playlist.optString(i, "").trim();
                if (!item.isEmpty()) urls.add(item);
            }
        }
        String playlistText = payload.optString("playlist_text", "").trim();
        if (!playlistText.isEmpty()) {
            for (String line : playlistText.split("\n")) {
                String item = line.trim();
                if (!item.isEmpty()) urls.add(item);
            }
        }

        String url = payload.optString("url", payload.optString("media_url", payload.optString("sound_url", payload.optString("media_content_id", ""))));
        if (url.isEmpty()) {
            String sound = payload.optString("sound", "");
            if ("doorbell".equals(sound)) url = "/media/local/doorbell.mp3";
            else if ("alarm".equals(sound)) url = "/media/local/alarm.mp3";
            else if ("notification".equals(sound) || "ding".equals(sound)) url = "/media/local/notification.mp3";
        }
        if (!url.isEmpty()) urls.add(0, url);
        if (!urls.isEmpty()) playMediaQueue(urls, 0);
    }

    private void playMediaQueue(ArrayList<String> urls, int startIndex) {
        mediaQueue.clear();
        mediaQueue.addAll(urls);
        mediaQueueIndex = Math.max(0, Math.min(startIndex, mediaQueue.size() - 1));
        playMedia(mediaQueue.get(mediaQueueIndex));
    }

    private void applyAudioSettingsFromPayload(JSONObject payload) {
        if (payload == null) return;
        if (payload.has("tts_volume") || payload.has("tts_volume_percent")) {
            ttsVolumePercent = Math.max(0, Math.min(100, payload.optInt("tts_volume_percent", payload.optInt("tts_volume", ttsVolumePercent))));
            AppSettings.putInt(this, "tts_volume_percent", ttsVolumePercent);
        }
        if (payload.has("media_volume") || payload.has("media_volume_percent")) {
            mediaVolumePercent = Math.max(0, Math.min(100, payload.optInt("media_volume_percent", payload.optInt("media_volume", mediaVolumePercent))));
            AppSettings.putInt(this, "media_volume_percent", mediaVolumePercent);
        }
        if (payload.has("audio_stream") || payload.has("stream")) {
            preferredAudioStream = payload.optString("audio_stream", payload.optString("stream", preferredAudioStream));
            if (preferredAudioStream == null || preferredAudioStream.trim().isEmpty()) preferredAudioStream = "music";
            AppSettings.put(this, "preferred_audio_stream", preferredAudioStream);
        }
        if (payload.has("force_max_volume") || payload.has("force_max_volume_for_speech")) {
            forceMaxVolumeForSpeech = payload.optBoolean("force_max_volume_for_speech", payload.optBoolean("force_max_volume", forceMaxVolumeForSpeech));
            AppSettings.putBoolean(this, "force_max_volume_for_speech", forceMaxVolumeForSpeech);
        }
        if (payload.has("audio_focus") || payload.has("request_audio_focus")) {
            requestAudioFocus = payload.optBoolean("request_audio_focus", payload.optBoolean("audio_focus", requestAudioFocus));
            AppSettings.putBoolean(this, "request_audio_focus", requestAudioFocus);
        }
    }

    private void prepareSpeakerForPlayback(String streamName, double requestedVolume, boolean speech) {
        try {
            AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (audio == null) return;
            audio.setMode(AudioManager.MODE_NORMAL);
            try { audio.setSpeakerphoneOn(true); } catch (Exception ignored) {}
            int stream = audioStreamForName(streamName);
            requestAudioFocusIfNeeded(stream);
            if (speech && forceMaxVolumeForSpeech) {
                setStreamToMax(audio, stream);
                if (stream != AudioManager.STREAM_MUSIC) setStreamToMax(audio, AudioManager.STREAM_MUSIC);
                if (stream != AudioManager.STREAM_ALARM) setStreamToMax(audio, AudioManager.STREAM_ALARM);
            } else if (requestedVolume >= 0) {
                setVolume(requestedVolume, streamName);
            }
        } catch (Exception ignored) {}
    }

    private void setStreamToMax(AudioManager audio, int stream) {
        try {
            int max = audio.getStreamMaxVolume(stream);
            if (max > 0) audio.setStreamVolume(stream, max, 0);
        } catch (Exception ignored) {}
    }

    private void requestAudioFocusIfNeeded(int stream) {
        if (!requestAudioFocus) return;
        try {
            AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (audio != null) audio.requestAudioFocus(new AudioManager.OnAudioFocusChangeListener() {
                @Override public void onAudioFocusChange(int focusChange) {}
            }, stream, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        } catch (Exception ignored) {}
    }

    private int audioStreamForName(String streamName) {
        String name = streamName == null ? "" : streamName.toLowerCase(Locale.US);
        if ("alarm".equals(name)) return AudioManager.STREAM_ALARM;
        if ("ring".equals(name) || "ringtone".equals(name)) return AudioManager.STREAM_RING;
        if ("notification".equals(name) || "notify".equals(name)) return AudioManager.STREAM_NOTIFICATION;
        if ("system".equals(name)) return AudioManager.STREAM_SYSTEM;
        return AudioManager.STREAM_MUSIC;
    }

    private void speakText(final String text, final String language) {
        speakText(text, language, ttsVolumePercent, preferredAudioStream);
    }

    private void speakText(final String text, final String language, final double requestedVolume, final String streamName) {
        if (text == null || text.trim().isEmpty()) return;
        final Locale locale = Build.VERSION.SDK_INT >= 21 ? Locale.forLanguageTag(language == null || language.isEmpty() ? "de-DE" : language) : Locale.getDefault();
        final int stream = audioStreamForName(streamName);
        final double volume = requestedVolume >= 0 ? requestedVolume : ttsVolumePercent;
        prepareSpeakerForPlayback(streamName, volume, true);
        if (tts == null) {
            tts = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    try { tts.setLanguage(locale); } catch (Exception ignored) {}
                    try { tts.setSpeechRate(0.96f); } catch (Exception ignored) {}
                    speakTextNow(text, stream, volume);
                }
            });
        } else {
            try { tts.setLanguage(locale); } catch (Exception ignored) {}
            try { tts.setSpeechRate(0.96f); } catch (Exception ignored) {}
            speakTextNow(text, stream, volume);
        }
    }

    private void speakTextNow(String text, int stream, double requestedVolume) {
        if (tts == null) return;
        float normalized = (float) Math.max(0.0, Math.min(1.0, (requestedVolume > 1.0 ? requestedVolume / 100.0 : requestedVolume)));
        if (forceMaxVolumeForSpeech) normalized = 1.0f;
        try {
            if (Build.VERSION.SDK_INT >= 21) {
                Bundle params = new Bundle();
                params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, stream);
                params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, normalized);
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "ha-kiosk-tts");
            } else {
                HashMap<String, String> params = new HashMap<>();
                params.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(stream));
                params.put(TextToSpeech.Engine.KEY_PARAM_VOLUME, String.valueOf(normalized));
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, params);
            }
            sendStatus("tts_speaking", "tts_speak");
        } catch (Exception ignored) {
            try { tts.speak(text, TextToSpeech.QUEUE_FLUSH, null); } catch (Exception ignoredAgain) {}
        }
    }

    private void applyBrowserConfig(JSONObject payload) {
        if (payload.has("fullscreen")) applyFullscreen(payload.optBoolean("fullscreen", true));
        if (payload.has("keep_screen_on")) {
            boolean keep = payload.optBoolean("keep_screen_on", true);
            AppSettings.putBoolean(this, "keep_awake", keep);
            if (keep) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        if (payload.has("webview_debug")) {
            boolean debug = payload.optBoolean("webview_debug", false);
            AppSettings.putBoolean(this, "debug_webview", debug);
            if (Build.VERSION.SDK_INT >= 19) WebView.setWebContentsDebuggingEnabled(debug);
        }
        if (payload.has("wait_for_background") || payload.has("background_first")) {
            waitForBackgroundBeforeLayout = payload.optBoolean("wait_for_background", payload.optBoolean("background_first", true));
            AppSettings.putBoolean(this, "wait_for_background", waitForBackgroundBeforeLayout);
        }
        if (payload.has("transparent_webview")) {
            transparentWebView = payload.optBoolean("transparent_webview", true);
            AppSettings.putBoolean(this, "transparent_webview", transparentWebView);
            applyWebViewTransparency(webView);
        }
        if (payload.has("force_mobile_viewport")) {
            forceMobileViewport = payload.optBoolean("force_mobile_viewport", false);
            AppSettings.putBoolean(this, "force_mobile_viewport", forceMobileViewport);
            if (webView != null) {
                webView.getSettings().setLoadWithOverviewMode(!forceMobileViewport);
                webView.getSettings().setUseWideViewPort(!forceMobileViewport);
            }
        }
        if (payload.has("reveal_ms")) {
            firstRevealDelayMs = Math.max(0, Math.min(2000, payload.optInt("reveal_ms", 250)));
            AppSettings.putInt(this, "first_reveal_ms", firstRevealDelayMs);
        }
        if (payload.has("zoom_percent") || payload.has("dashboard_zoom") || payload.has("page_zoom")) {
            int zoom = payload.has("zoom_percent") ? payload.optInt("zoom_percent", browserZoomPercent) : payload.optInt("dashboard_zoom", payload.optInt("page_zoom", browserZoomPercent));
            browserZoomPercent = clampZoom(zoom);
            AppSettings.putInt(this, "browser_zoom_percent", browserZoomPercent);
            currentPageZoomPercent = zoomForPath(currentUrl);
            scheduleDashboardZoomRefresh();
            if (backgroundEnabled) {
                handler.postDelayed(() -> applyBackgroundTransparencyForZoom(), 350);
            }
        }
        if (payload.has("settings_sync_seconds") || payload.has("self_sync_seconds") || payload.has("sync_interval_seconds")) {
            settingsSyncIntervalSeconds = Math.max(0, Math.min(86400, payload.optInt("settings_sync_seconds", payload.optInt("self_sync_seconds", payload.optInt("sync_interval_seconds", settingsSyncIntervalSeconds)))));
            AppSettings.putInt(this, "settings_sync_seconds", settingsSyncIntervalSeconds);
            scheduleSettingsSync();
        }
        if (payload.has("visual_watchdog_seconds") || payload.has("watchdog_seconds") || payload.has("auto_recover_seconds")) {
            visualWatchdogSeconds = Math.max(0, Math.min(3600, payload.optInt("visual_watchdog_seconds", payload.optInt("watchdog_seconds", payload.optInt("auto_recover_seconds", visualWatchdogSeconds)))));
            visualWatchdogEnabled = visualWatchdogSeconds > 0;
            AppSettings.putInt(this, "visual_watchdog_seconds", visualWatchdogSeconds);
            scheduleVisualWatchdog();
        }
        applyAudioSettingsFromPayload(payload);
        String userAgent = payload.optString("user_agent", "");
        AppSettings.put(this, "user_agent", userAgent);
        if (webView != null) {
            if (!userAgent.isEmpty()) webView.getSettings().setUserAgentString(userAgent);
            else webView.getSettings().setUserAgentString(null);
        }
        if (payload.has("external_auth") || payload.has("browser_external_auth")) {
            browserExternalAuth = payload.optBoolean("external_auth", payload.optBoolean("browser_external_auth", true));
            AppSettings.putBoolean(this, "browser_external_auth", browserExternalAuth);
        }
        if (payload.has("hide_header") || payload.has("hide_ha_header")) {
            hideHomeAssistantHeader = payload.optBoolean("hide_header", payload.optBoolean("hide_ha_header", false));
            AppSettings.putBoolean(this, "hide_ha_header", hideHomeAssistantHeader);
            applyHomeAssistantHeaderVisibility();
        }
        browserReloadSeconds = payload.optInt("reload_seconds", payload.optInt("auto_reload_seconds", 0));
        AppSettings.putInt(this, "browser_reload_seconds", browserReloadSeconds);
        scheduleBrowserReload();

        String homeUrl = payload.optString("home_url", "");
        if (!homeUrl.isEmpty()) AppSettings.put(this, "default_url", homeUrl);
        String url = payload.optString("url", payload.optString("start_url", homeUrl));
        if (!applyingStoredConfig) updateCachedSection("browser", payload);
        if (!applyingStoredConfig && !url.isEmpty()) loadPage(url);
        reapplyVisualState("browser_config");
        sendStatus("browser_config_applied", "browser_config");
    }

    private void scheduleBrowserReload() {
        handler.removeCallbacks(browserReloadRunnable);
        if (browserReloadSeconds > 0) handler.postDelayed(browserReloadRunnable, browserReloadSeconds * 1000L);
    }

    private void configureRotation(JSONObject payload) throws Exception {
        configureRotation(payload, true);
    }

    private void configureRotation(JSONObject payload, boolean loadFirstPage) throws Exception {
        JSONArray arr = payload.optJSONArray("pages");
        pages.clear();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                Object item = arr.get(i);
                if (item instanceof JSONObject) {
                    JSONObject page = (JSONObject) item;
                    pages.add(new PageSpec(
                        page.optString("url", page.optString("path", "")),
                        page.optInt("duration", payload.optInt("duration", 15)),
                        readZoomFromPayload(page, 0)
                    ));
                } else {
                    pages.add(new PageSpec(String.valueOf(item), payload.optInt("duration", 15), 0));
                }
            }
        }
        rotationEnabled = payload.optBoolean("auto_rotate", payload.optBoolean("enabled", true));
        pauseOnTouch = payload.optBoolean("pause_on_touch", true);
        touchPauseSeconds = payload.optInt("touch_pause", payload.optInt("touch_pause_seconds", 30));
        currentPageIndex = 0;
        if (!applyingStoredConfig) updateCachedRotation(payload);
        if (!pages.isEmpty() && loadFirstPage) loadPage(pages.get(0).url, pages.get(0).zoomPercent);
        scheduleRotation();
    }

    private void scheduleRotation() {
        handler.removeCallbacks(rotationRunnable);
        if (!rotationEnabled || pages.size() < 2) return;
        int seconds = pages.get(Math.max(0, Math.min(currentPageIndex, pages.size() - 1))).durationSeconds;
        handler.postDelayed(rotationRunnable, seconds * 1000L);
    }

    private void rotateIfNeeded() {
        if (!rotationEnabled || pages.size() < 2) return;
        long now = System.currentTimeMillis();
        if (pauseOnTouch && rotationPausedUntil > now) {
            handler.postDelayed(rotationRunnable, Math.max(1000, rotationPausedUntil - now));
            return;
        }
        currentPageIndex = (currentPageIndex + 1) % pages.size();
        PageSpec page = pages.get(currentPageIndex);
        loadPage(page.url, page.zoomPercent);
        scheduleRotation();
    }

    private void loadPage(String pathOrUrl) {
        loadPage(pathOrUrl, 0);
    }

    private void loadPage(String pathOrUrl, int zoomPercent) {
        if (pathOrUrl == null || pathOrUrl.trim().isEmpty() || webView == null) return;
        startupPageLoadStarted = true;
        prepareFirstPageRevealGate();
        currentPageZoomPercent = zoomPercent > 0 ? clampZoom(zoomPercent) : zoomForPath(pathOrUrl);
        currentUrl = maybeAddExternalAuth(resolveUrl(pathOrUrl));
        webView.loadUrl(currentUrl);
    }

    private void loadInitialPageIfNeeded() {
        initialConfigFetchCompleted = true;
        if (startupPageLoadStarted) return;
        PageSpec page = !pages.isEmpty() ? pages.get(Math.max(0, Math.min(currentPageIndex, pages.size() - 1))) : null;
        String url = page != null ? page.url : AppSettings.get(this, "default_url", "/lovelace/0");
        loadPage(url, page != null ? page.zoomPercent : 0);
        if (rotationEnabled && pages.size() > 1) scheduleRotation();
    }

    private String resolveUrl(String pathOrUrl) {
        String value = pathOrUrl == null ? "" : pathOrUrl.trim();
        if (value.startsWith("http://") || value.startsWith("https://")) return value;
        if (!value.startsWith("/")) value = "/" + value;
        return AppSettings.baseUrl(this) + value;
    }

    private String maybeAddExternalAuth(String url) {
        if (!browserExternalAuth || AppSettings.token(this).isEmpty() || !isHomeAssistantUrl(url)) return url;
        try {
            Uri uri = Uri.parse(url);
            if (!uri.isHierarchical()) return url;
            if (uri.getQueryParameter("external_auth") != null) return url;
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (path.startsWith("/auth/")) return url;
            return uri.buildUpon().appendQueryParameter("external_auth", "1").build().toString();
        } catch (Exception ignored) {
            return url;
        }
    }

    private boolean isHomeAssistantUrl(String url) {
        try {
            Uri target = Uri.parse(url);
            Uri base = Uri.parse(AppSettings.baseUrl(this));
            String targetHost = target.getHost();
            String baseHost = base.getHost();
            if (targetHost == null || baseHost == null) return false;
            int targetPort = target.getPort();
            int basePort = base.getPort();
            return targetHost.equalsIgnoreCase(baseHost) && targetPort == basePort;
        } catch (Exception ignored) {
            return false;
        }
    }

    private final class ExternalAuthBridge {
        private final WebView targetView;

        ExternalAuthBridge(WebView targetView) {
            this.targetView = targetView;
        }

        @JavascriptInterface public void getExternalAuth(String options) {
            final String callback = externalAuthCallback(options, "externalAuthSetToken");
            if (callback.isEmpty()) return;
            Runnable send = () -> sendExternalAuthResponse(targetView, callback, true, null);
            if (externalAuthForce(options) || AppSettings.needsTokenRefresh(MainActivity.this)) {
                refreshAccessTokenIfNeeded(send);
            } else {
                handler.post(send);
            }
        }

        @JavascriptInterface public void revokeExternalAuth(String options) {
            final String callback = externalAuthCallback(options, "externalAuthRevokeToken");
            if (callback.isEmpty()) return;
            handler.post(() -> sendExternalAuthRevokeResponse(targetView, callback, true));
        }
    }

    private String externalAuthCallback(String options, String expected) {
        if (options == null) return "";
        String raw = options.trim();
        try {
            JSONObject obj = new JSONObject(raw);
            String callback = obj.optString("callback", "");
            return expected.equals(callback) ? callback : "";
        } catch (Exception ignored) {
            return expected.equals(raw) ? raw : "";
        }
    }

    private boolean externalAuthForce(String options) {
        if (options == null) return false;
        try { return new JSONObject(options).optBoolean("force", false); } catch (Exception ignored) { return false; }
    }

    private void sendExternalAuthResponse(WebView target, String callback, boolean success, String error) {
        if (target == null || callback == null || callback.trim().isEmpty()) return;
        if (!isHomeAssistantUrl(target.getUrl())) return;
        try {
            JSONObject token = new JSONObject();
            if (success) {
                token.put("access_token", AppSettings.token(this));
                token.put("token_type", "Bearer");
                token.put("expires_in", externalAuthExpiresInSeconds());
            } else {
                token.put("error", error == null ? "external_auth_failed" : error);
            }
            String callbackRef = callbackReference(callback.trim());
            String js = "(function(){try{var cb=" + callbackRef + "; if(typeof cb==='function'){cb(" + success + "," + token.toString() + ");}}catch(e){console.error('HA Kiosk external auth callback failed', e);}})();";
            target.evaluateJavascript(js, null);
        } catch (Exception ignored) {}
    }

    private void sendExternalAuthRevokeResponse(WebView target, String callback, boolean success) {
        if (target == null || callback == null || callback.trim().isEmpty()) return;
        if (!isHomeAssistantUrl(target.getUrl())) return;
        try {
            String callbackRef = callbackReference(callback.trim());
            String js = "(function(){try{var cb=" + callbackRef + "; if(typeof cb==='function'){cb(" + success + ");}}catch(e){console.error('HA Kiosk external auth revoke failed', e);}})();";
            target.evaluateJavascript(js, null);
        } catch (Exception ignored) {}
    }

    private int externalAuthExpiresInSeconds() {
        long expiresAt = AppSettings.tokenExpiresAt(this);
        if (expiresAt <= 0) return 3600;
        long seconds = (expiresAt - System.currentTimeMillis()) / 1000L;
        return (int) Math.max(60, Math.min(seconds, 86400));
    }

    private String callbackReference(String callback) {
        if (callback.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")) return "window." + callback;
        return "window[" + JSONObject.quote(callback) + "]";
    }

    private void clearWebFrontendSession() {
        try {
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
        } catch (Exception ignored) {}
        try { WebStorage.getInstance().deleteAllData(); } catch (Exception ignored) {}
    }

    private void showTicker(String text, boolean visible) {
        tickerView.setText(text == null ? "" : text);
        tickerView.setVisibility(visible && text != null && !text.isEmpty() ? View.VISIBLE : View.GONE);
        tickerView.setSelected(true);
    }

    private void showBanner(String text, int durationSeconds) {
        bannerView.setText(text == null ? "" : text);
        bannerView.setVisibility(text != null && !text.isEmpty() ? View.VISIBLE : View.GONE);
        if (durationSeconds > 0) handler.postDelayed(() -> bannerView.setVisibility(View.GONE), durationSeconds * 1000L);
    }

    private void showAlert(String text, int durationSeconds) {
        alertText.setText(text == null ? "" : text);
        alertView.setVisibility(text != null && !text.isEmpty() ? View.VISIBLE : View.GONE);
        if (durationSeconds > 0) handler.postDelayed(() -> alertView.setVisibility(View.GONE), durationSeconds * 1000L);
    }

    private void showCamera(String url, boolean visible) {
        if (!visible || url == null || url.isEmpty()) {
            cameraView.setVisibility(View.GONE);
            return;
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + AppSettings.token(this));
        cameraView.loadUrl(resolveUrl(url), headers);
        cameraView.setVisibility(View.VISIBLE);
    }

    private void clear(String target) {
        boolean alerts = "alerts".equals(target) || "alert".equals(target);
        if ("all".equals(target) || "ticker".equals(target)) tickerView.setVisibility(View.GONE);
        if ("all".equals(target) || alerts || "banner".equals(target)) bannerView.setVisibility(View.GONE);
        if ("all".equals(target) || alerts || "alert".equals(target)) alertView.setVisibility(View.GONE);
        if ("all".equals(target) || alerts || "weather".equals(target)) weatherView.setVisibility(View.GONE);
        if ("all".equals(target) || alerts || "camera".equals(target)) cameraView.setVisibility(View.GONE);
        if ("all".equals(target) || "screen".equals(target)) blackView.setVisibility(View.GONE);
    }

    private void applyFullscreen(boolean enabled) {
        fullscreenEnabled = enabled;
        Window window = getWindow();
        View decor = window.getDecorView();
        if (enabled) {
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            decor.setOnSystemUiVisibilityChangeListener(visibility -> {
                if (fullscreenEnabled && (visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                    handler.removeCallbacks(fullscreenRunnable);
                    handler.postDelayed(fullscreenRunnable, 250);
                }
            });
            if (Build.VERSION.SDK_INT >= 28) {
                window.getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
            if (Build.VERSION.SDK_INT >= 30) {
                window.setDecorFitsSystemWindows(false);
                WindowInsetsController controller = decor.getWindowInsetsController();
                if (controller != null) {
                    controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            }
            decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            decor.setOnSystemUiVisibilityChangeListener(null);
            if (Build.VERSION.SDK_INT >= 30) {
                window.setDecorFitsSystemWindows(true);
                WindowInsetsController controller = decor.getWindowInsetsController();
                if (controller != null) controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            }
            decor.setSystemUiVisibility(0);
        }
    }

    private void setOrientation(String value) {
        String v = value == null ? "" : value.toLowerCase(Locale.US);
        if (v.contains("landscape")) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        else if (v.contains("portrait")) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        else if (v.contains("sensor")) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        else setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    private void setScreen(String value) {
        String v = value == null ? "on" : value.toLowerCase(Locale.US);
        if (v.equals("off") || v.equals("blank") || v.equals("black")) {
            blackView.setVisibility(View.VISIBLE);
            setBrightness(0.01, false);
        } else {
            blackView.setVisibility(View.GONE);
            setBrightness(1.0, false);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            try {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "haandroidkiosk:screenon");
                wl.acquire(5000);
            } catch (Exception ignored) {}
        }
    }

    private void setBrightness(double value, boolean system) {
        float level = (float) Math.max(0.01, Math.min(1.0, value > 1.0 ? value / 100.0 : value));
        lastBrightnessPercent = Math.round(level * 100);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = level;
        getWindow().setAttributes(lp);
        if (system && Build.VERSION.SDK_INT >= 23 && Settings.System.canWrite(this)) {
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, Math.round(level * 255));
        }
    }

    private void setVolume(double value, String streamName) {
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        int stream = audioStreamForName(streamName);
        int max = audio.getStreamMaxVolume(stream);
        double normalized = Math.max(0, Math.min(1.0, value > 1.0 ? value / 100.0 : value));
        int volume = (int) Math.round(normalized * max);
        audio.setStreamVolume(stream, volume, 0);
        lastVolumePercent = max <= 0 ? 0 : (int) Math.round(volume * 100.0 / max);
    }

    private int getVolumePercent(String streamName) {
        try {
            AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
            int stream = audioStreamForName(streamName);
            int max = audio.getStreamMaxVolume(stream);
            if (max <= 0) return 0;
            return (int) Math.round(audio.getStreamVolume(stream) * 100.0 / max);
        } catch (Exception ignored) {
            return lastVolumePercent;
        }
    }

    private void playMedia(String url) {
        if (url == null || url.trim().isEmpty()) return;
        try {
            releaseMediaPlayer();
            currentMediaUrl = url;
            mediaPlayer = new MediaPlayer();
            int stream = audioStreamForName(preferredAudioStream);
            requestAudioFocusIfNeeded(stream);
            mediaPlayer.setAudioStreamType(stream);
            mediaPlayer.setLooping(mediaLoop && mediaQueue.size() <= 1);
            mediaPlayer.setDataSource(this, Uri.parse(resolveUrl(url)));
            mediaPlayer.setOnPreparedListener(mp -> {
                mediaState = "playing";
                float vol = mediaMuted ? 0f : (float) Math.max(0.0, Math.min(1.0, mediaVolumePercent / 100.0));
                mp.setVolume(vol, vol);
                mp.start();
                sendStatus("media_playing", "media_play_url");
            });
            mediaPlayer.setOnCompletionListener(mp -> {
                if (playNextMedia(false)) return;
                mediaState = "idle";
                sendStatus("media_idle", "media_complete");
            });
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                mediaState = "error";
                sendStatus("media_error", "media_error");
                return true;
            });
            mediaState = "buffering";
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            mediaState = "error";
            Toast.makeText(this, "Media konnte nicht gestartet werden: " + e.getMessage(), Toast.LENGTH_LONG).show();
            sendStatus("media_error: " + e.getMessage(), "media_play_url");
        }
    }

    private boolean playNextMedia(boolean manual) {
        if (mediaQueue.isEmpty()) {
            if (manual) sendStatus("media_next_unavailable", "media_next");
            return false;
        }
        int next = mediaQueueIndex + 1;
        if (next >= mediaQueue.size()) {
            if (mediaLoop) next = 0;
            else {
                if (manual) sendStatus("media_next_unavailable", "media_next");
                return false;
            }
        }
        mediaQueueIndex = next;
        playMedia(mediaQueue.get(mediaQueueIndex));
        return true;
    }

    private void playPreviousMedia() {
        if (mediaQueue.isEmpty()) {
            sendStatus("media_previous_unavailable", "media_previous");
            return;
        }
        int prev = mediaQueueIndex - 1;
        if (prev < 0) prev = mediaLoop ? mediaQueue.size() - 1 : 0;
        mediaQueueIndex = prev;
        playMedia(mediaQueue.get(mediaQueueIndex));
    }

    private void seekMedia(int seconds) {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.seekTo(Math.max(0, seconds) * 1000);
                sendStatus("media_seeked", "media_seek");
            }
        } catch (Exception e) {
            sendStatus("media_seek_error: " + e.getMessage(), "media_seek");
        }
    }

    private void resumeMedia() {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.start();
                mediaState = "playing";
                sendStatus("media_playing", "media_play");
            }
        } catch (Exception ignored) {}
    }

    private void pauseMedia() {
        try {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                mediaState = "paused";
                sendStatus("media_paused", "media_pause");
            }
        } catch (Exception ignored) {}
    }

    private void stopMedia() {
        releaseMediaPlayer();
        mediaState = "idle";
        sendStatus("media_idle", "media_stop");
    }

    private void releaseMediaPlayer() {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.reset();
                mediaPlayer.release();
            }
        } catch (Exception ignored) {
        } finally {
            mediaPlayer = null;
        }
    }

    private void setMediaMuted(boolean muted) {
        mediaMuted = muted;
        try {
            if (mediaPlayer != null) {
                float vol = muted ? 0f : 1f;
                mediaPlayer.setVolume(vol, vol);
            }
        } catch (Exception ignored) {}
        sendStatus(muted ? "media_muted" : "media_unmuted", "media_mute");
    }

    @SuppressWarnings("deprecation")
    private void setCameraMotionDetection(boolean enabled, String facing) {
        cameraMotionEnabled = enabled;
        if (!enabled) {
            if (activeCamera != null) activeCamera.setPreviewCallback(null);
            lastFrameLuma = -1;
            sendStatus("camera_motion_off", "camera_motion_detection");
            return;
        }
        if (activeCamera == null) setDeviceCamera("front", true, true);
        else activeCamera.setPreviewCallback((data, camera) -> handleCameraFrame(data));
        sendStatus("camera_motion_on", "camera_motion_detection");
    }

    private void setDeviceCamera(String facingName, boolean enabled, boolean motion) {
        int facing = cameraFacingFromName(facingName);
        if (!enabled) {
            if (activeCameraFacing == facing || facingName == null || "all".equalsIgnoreCase(facingName)) stopDeviceCamera();
            return;
        }
        startDeviceCamera(facing, motion);
    }

    @SuppressWarnings("deprecation")
    private void startDeviceCamera(int facing, boolean motion) {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Kamera-Berechtigung fehlt", Toast.LENGTH_LONG).show();
            requestRuntimePermissions();
            sendStatus("camera_permission_missing", "camera_control");
            return;
        }
        try {
            if (activeCamera != null && activeCameraFacing == facing) {
                cameraMotionEnabled = motion;
                activeCamera.setPreviewCallback(motion ? (data, camera) -> handleCameraFrame(data) : null);
                sendStatus("camera_on", "camera_control");
                return;
            }
            stopDeviceCamera();
            int cameraId = findCameraId(facing);
            if (cameraId < 0) throw new IllegalStateException("Kamera nicht gefunden: " + cameraFacingName(facing));
            activeCamera = Camera.open(cameraId);
            activeCameraFacing = facing;
            Camera.Parameters params = activeCamera.getParameters();
            Camera.Size chosen = choosePreviewSize(params);
            if (chosen != null) {
                params.setPreviewSize(chosen.width, chosen.height);
                cameraPreviewWidth = chosen.width;
                cameraPreviewHeight = chosen.height;
            }
            activeCamera.setParameters(params);
            cameraTexture = new SurfaceTexture(99);
            activeCamera.setPreviewTexture(cameraTexture);
            cameraMotionEnabled = motion;
            if (motion) activeCamera.setPreviewCallback((data, camera) -> handleCameraFrame(data));
            activeCamera.startPreview();
            sendStatus("camera_on", "camera_control");
        } catch (Exception e) {
            stopDeviceCamera();
            Toast.makeText(this, "Kamera konnte nicht gestartet werden: " + e.getMessage(), Toast.LENGTH_LONG).show();
            sendStatus("camera_error: " + e.getMessage(), "camera_control");
        }
    }

    @SuppressWarnings("deprecation")
    private void stopDeviceCamera() {
        try {
            if (activeCamera != null) {
                activeCamera.setPreviewCallback(null);
                activeCamera.stopPreview();
                activeCamera.release();
            }
        } catch (Exception ignored) {
        } finally {
            activeCamera = null;
            activeCameraFacing = -1;
            cameraMotionEnabled = false;
            lastFrameLuma = -1;
            cameraPreviewWidth = 0;
            cameraPreviewHeight = 0;
            cameraTexture = null;
            sendStatus("camera_off", "camera_control");
        }
    }

    @SuppressWarnings("deprecation")
    private int findCameraId(int facing) {
        int count = Camera.getNumberOfCameras();
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < count; i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == facing) return i;
        }
        return count > 0 ? 0 : -1;
    }

    @SuppressWarnings("deprecation")
    private Camera.Size choosePreviewSize(Camera.Parameters params) {
        Camera.Size best = null;
        for (Camera.Size size : params.getSupportedPreviewSizes()) {
            if (best == null) {
                best = size;
                continue;
            }
            int score = Math.abs(size.width - 320) + Math.abs(size.height - 240);
            int bestScore = Math.abs(best.width - 320) + Math.abs(best.height - 240);
            if (score < bestScore) best = size;
        }
        return best;
    }

    @SuppressWarnings("deprecation")
    private int cameraFacingFromName(String name) {
        return Camera.CameraInfo.CAMERA_FACING_FRONT;
    }

    @SuppressWarnings("deprecation")
    private String cameraFacingName(int facing) {
        if (facing == Camera.CameraInfo.CAMERA_FACING_FRONT) return "front";
        return "none";
    }

    private void handleCameraFrame(byte[] data) {
        if (!cameraMotionEnabled || data == null || data.length == 0) return;
        long now = System.currentTimeMillis();
        double sum = 0;
        int samples = 0;
        int limit = cameraPreviewWidth > 0 && cameraPreviewHeight > 0 ? Math.min(data.length, cameraPreviewWidth * cameraPreviewHeight) : data.length;
        int step = Math.max(1, limit / 900);
        for (int i = 0; i < limit; i += step) {
            sum += data[i] & 0xFF;
            samples++;
        }
        if (samples == 0) return;
        double luma = sum / samples;
        if (lastFrameLuma >= 0) {
            lastCameraMotionScore = Math.abs(luma - lastFrameLuma);
            if (lastCameraMotionScore > 7.5 && now - lastCameraMotionEvent > 3500) {
                lastCameraMotionEvent = now;
                try {
                    JSONObject dataOut = new JSONObject();
                    dataOut.put("device_id", AppSettings.deviceId(this));
                    dataOut.put("event", "camera_motion_detected");
                    dataOut.put("camera", cameraFacingName(activeCameraFacing));
                    dataOut.put("score", lastCameraMotionScore);
                    dataOut.put("current_url", currentUrl);
                    if (restClient != null) restClient.fireEventAsync(EVENT_STATUS, dataOut);
                    sendStatus("camera_motion_detected", "camera_motion_detection");
                } catch (Exception ignored) {}
            }
        }
        lastFrameLuma = luma;
    }

    private void vibrate(int ms) {
        try {
            android.os.Vibrator vib = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vib == null) return;
            if (Build.VERSION.SDK_INT >= 26) vib.vibrate(android.os.VibrationEffect.createOneShot(ms, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            else vib.vibrate(ms);
        } catch (Exception ignored) {}
    }

    private void maybeOpenSettingsBySecretTap(MotionEvent ev) {
        if (ev.getX() > dp(90) || ev.getY() > dp(90)) return;
        long now = System.currentTimeMillis();
        if (now - lastSecretTap > 2200) secretTapCount = 0;
        lastSecretTap = now;
        secretTapCount++;
        if (secretTapCount >= 5) {
            secretTapCount = 0;
            showSettings();
        }
    }

    private void requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT < 23) return;
        ArrayList<String> missing = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.CAMERA);
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.RECORD_AUDIO);
        if (!missing.isEmpty()) requestPermissions(missing.toArray(new String[0]), 42);
    }

    private void requestWriteSettingsPermission() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.System.canWrite(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } else {
            Toast.makeText(this, "Systemhelligkeit ist bereits erlaubt", Toast.LENGTH_SHORT).show();
        }
    }

    private void registerMobileApp() {
        if (restClient == null) return;
        try {
            JSONObject body = new JSONObject();
            body.put("device_id", AppSettings.deviceId(this));
            body.put("app_id", "ha_android_kiosk");
            body.put("app_name", "HA Android Kiosk");
            body.put("app_version", "1.9.12");
            body.put("device_name", AppSettings.get(this, "device_name", Build.MANUFACTURER + " " + Build.MODEL));
            body.put("manufacturer", Build.MANUFACTURER);
            body.put("model", Build.MODEL);
            body.put("os_name", "Android");
            body.put("os_version", Build.VERSION.RELEASE == null ? String.valueOf(Build.VERSION.SDK_INT) : Build.VERSION.RELEASE);
            body.put("supports_encryption", false);
            JSONObject appData = new JSONObject();
            appData.put("kiosk_integration", "ha_android_kiosk");
            appData.put("kiosk_device_id", AppSettings.deviceId(this));
            body.put("app_data", appData);
            JSONObject response = restClient.post("/api/mobile_app/registrations", body);
            if (response != null) {
                AppSettings.put(this, "mobile_app_webhook_id", response.optString("webhook_id", AppSettings.get(this, "mobile_app_webhook_id", "")));
                AppSettings.put(this, "mobile_app_cloudhook_url", response.optString("cloudhook_url", AppSettings.get(this, "mobile_app_cloudhook_url", "")));
                AppSettings.put(this, "mobile_app_remote_ui_url", response.optString("remote_ui_url", AppSettings.get(this, "mobile_app_remote_ui_url", "")));
            }
        } catch (Exception ignored) {
            // Some installations do not have the mobile_app integration loaded. The custom kiosk integration still registers below.
        }
    }

    private void registerDevice() {
        try {
            JSONObject body = new JSONObject();
            body.put("device_id", AppSettings.deviceId(this));
            body.put("name", AppSettings.get(this, "device_name", Build.MANUFACTURER + " " + Build.MODEL));
            body.put("manufacturer", Build.MANUFACTURER);
            body.put("model", Build.MODEL);
            body.put("app_version", "1.9.12");
            JSONArray caps = new JSONArray();
            String[] values = {"webview", "set_page", "rotation", "touch_pause", "ticker", "banner", "toast", "alert", "clock", "weather", "camera_overlay", "brightness", "volume", "orientation", "vibrate", "screen_blank", "motion_detection", "camera_front", "camera_motion_detection", "light_sensor", "hide_ha_header", "media_player", "services_v3", "services_v4", "oauth_login", "mobile_app_registration", "tts", "background_slideshow", "background_first", "transparent_webview", "force_mobile_viewport", "self_sync", "cached_config", "visual_watchdog", "page_zoom_background_safe", "audio_boost", "memory_safe_background", "low_ram_zoom"};
            for (String v : values) caps.put(v);
            body.put("capabilities", caps);
            body.put("current_url", currentUrl);
            restClient.postAsync("/api/ha_android_kiosk/register", body);
        } catch (Exception ignored) {}
    }

    private void sendStatus(String status, String command) {
        if (restClient == null) return;
        try {
            JSONObject body = new JSONObject();
            body.put("device_id", AppSettings.deviceId(this));
            body.put("name", AppSettings.get(this, "device_name", Build.MANUFACTURER + " " + Build.MODEL));
            body.put("status", status);
            body.put("command", command == null ? "" : command);
            body.put("current_url", currentUrl);
            body.put("brightness", lastBrightnessPercent);
            body.put("volume", getVolumePercent("music"));
            body.put("screen_blank", blackView != null && blackView.getVisibility() == View.VISIBLE);
            body.put("rotation_enabled", rotationEnabled);
            body.put("motion_enabled", motionEnabled);
            body.put("motion_detected", System.currentTimeMillis() - lastMotionEvent < 15000);
            body.put("camera", cameraFacingName(activeCameraFacing));
            body.put("camera_front_active", "front".equals(cameraFacingName(activeCameraFacing)));
            body.put("camera_motion_enabled", cameraMotionEnabled);
            body.put("camera_motion_detected", System.currentTimeMillis() - lastCameraMotionEvent < 15000);
            body.put("camera_motion_score", lastCameraMotionScore);
            body.put("media_state", mediaState);
            body.put("media_muted", mediaMuted);
            body.put("media_loop", mediaLoop);
            body.put("media_url", currentMediaUrl);
            body.put("media_queue_index", mediaQueueIndex);
            body.put("media_queue_size", mediaQueue.size());
            body.put("background_enabled", backgroundEnabled);
            body.put("background_index", backgroundIndex);
            body.put("background_count", backgroundImages.size());
            body.put("background_ready", firstBackgroundReady);
            body.put("wait_for_background", waitForBackgroundBeforeLayout);
            body.put("transparent_webview", transparentWebView);
            body.put("force_mobile_viewport", forceMobileViewport);
            body.put("hide_ha_header", hideHomeAssistantHeader);
            body.put("browser_zoom_percent", browserZoomPercent);
            body.put("current_page_zoom_percent", effectiveDashboardZoom());
            body.put("settings_sync_seconds", settingsSyncIntervalSeconds);
            body.put("last_settings_sync_at", lastSettingsSyncAt);
            body.put("last_settings_sync_status", lastSettingsSyncStatus);
            body.put("visual_watchdog_seconds", visualWatchdogSeconds);
            body.put("preferred_audio_stream", preferredAudioStream);
            body.put("tts_volume_percent", ttsVolumePercent);
            body.put("media_volume_percent", mediaVolumePercent);
            body.put("force_max_volume_for_speech", forceMaxVolumeForSpeech);
            if (lastLightLux >= 0) body.put("light_lux", lastLightLux); else body.put("light_lux", JSONObject.NULL);
            body.put("light_sensor_available", lightSensorAvailable);
            try {
                if (mediaPlayer != null) {
                    body.put("media_position", Math.max(0, mediaPlayer.getCurrentPosition() / 1000));
                    body.put("media_duration", Math.max(0, mediaPlayer.getDuration() / 1000));
                }
            } catch (Exception ignored) {}
            body.put("camera_permission", Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED);
            body.put("microphone_permission", Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);
            body.put("write_settings_permission", Build.VERSION.SDK_INT < 23 || Settings.System.canWrite(this));
            restClient.postAsync("/api/ha_android_kiosk/status", body);
        } catch (Exception ignored) {}
    }

    private void startMotionDetection() {
        if (motionEnabled) return;
        if (sensorManager == null) sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager == null) return;
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer == null) return;
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        motionEnabled = true;
    }

    private void stopMotionDetection() {
        if (sensorManager != null && accelerometer != null) sensorManager.unregisterListener(this, accelerometer);
        motionEnabled = false;
        lastAccel = -1;
    }

    private void startLightSensor() {
        if (sensorManager == null) sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager == null) return;
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        lightSensorAvailable = lightSensor != null;
        if (lightSensor != null) sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void stopLightSensor() {
        if (sensorManager != null && lightSensor != null) sensorManager.unregisterListener(this, lightSensor);
        lightSensor = null;
        lightSensorAvailable = false;
        lastLightLux = -1;
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (event == null || event.sensor == null) return;
        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            if (event.values != null && event.values.length > 0) lastLightLux = event.values[0];
            return;
        }
        if (!motionEnabled || event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
        float x = event.values[0], y = event.values[1], z = event.values[2];
        float magnitude = (float) Math.sqrt(x * x + y * y + z * z);
        if (lastAccel < 0) {
            lastAccel = magnitude;
            return;
        }
        float delta = Math.abs(magnitude - lastAccel);
        lastAccel = magnitude;
        long now = System.currentTimeMillis();
        if (delta > 2.2f && now - lastMotionEvent > 5000) {
            lastMotionEvent = now;
            try {
                JSONObject data = new JSONObject();
                data.put("device_id", AppSettings.deviceId(this));
                data.put("event", "motion_detected");
                data.put("delta", delta);
                data.put("current_url", currentUrl);
                if (restClient != null) restClient.fireEventAsync(EVENT_STATUS, data);
                sendStatus("motion_detected", "motion_detection");
            } catch (Exception ignored) {}
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
