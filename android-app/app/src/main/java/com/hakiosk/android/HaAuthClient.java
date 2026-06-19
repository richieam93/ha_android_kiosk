package com.hakiosk.android;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class HaAuthClient {
    public static final String CLIENT_ID = "https://home-assistant.io/android";
    public static final String REDIRECT_URI = "homeassistant://auth-callback";
    public static final String USER_AGENT = "HomeAssistantKiosk/Android";

    private HaAuthClient() {}

    public static String authorizeUrl(String baseUrl) throws Exception {
        String base = stripSlash(baseUrl);
        return base + "/auth/authorize?response_type=code"
            + "&client_id=" + enc(CLIENT_ID)
            + "&redirect_uri=" + enc(REDIRECT_URI);
    }

    public static JSONObject exchangeAuthCode(String baseUrl, String code) throws Exception {
        String body = "grant_type=authorization_code"
            + "&code=" + enc(code)
            + "&client_id=" + enc(CLIENT_ID);
        return tokenRequest(baseUrl, body);
    }

    public static JSONObject refreshAccessToken(String baseUrl, String refreshToken) throws Exception {
        String body = "grant_type=refresh_token"
            + "&refresh_token=" + enc(refreshToken)
            + "&client_id=" + enc(CLIENT_ID);
        return tokenRequest(baseUrl, body);
    }

    private static JSONObject tokenRequest(String baseUrl, String body) throws Exception {
        URL url = new URL(stripSlash(baseUrl) + "/auth/token");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8))) {
            writer.write(body);
        }
        int code = conn.getResponseCode();
        InputStream stream = code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream();
        String text = readAll(stream);
        conn.disconnect();
        if (code < 200 || code >= 400) {
            throw new IllegalStateException(text == null || text.trim().isEmpty() ? "HTTP " + code : text.trim());
        }
        return new JSONObject(text == null || text.trim().isEmpty() ? "{}" : text);
    }

    private static String enc(String value) throws Exception {
        return URLEncoder.encode(value == null ? "" : value, "UTF-8");
    }

    private static String readAll(InputStream input) throws Exception {
        if (input == null) return "";
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) out.append(line).append('\n');
        }
        return out.toString();
    }

    private static String stripSlash(String value) {
        String out = value == null ? "" : value.trim();
        while (out.endsWith("/")) out = out.substring(0, out.length() - 1);
        return out;
    }
}
