package com.hakiosk.android;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class HaRestClient {
    private final String baseUrl;
    private final String token;

    public HaRestClient(String baseUrl, String token) {
        this.baseUrl = stripSlash(baseUrl);
        this.token = token;
    }

    public void postAsync(String apiPath, JSONObject body) {
        new Thread(() -> {
            try {
                post(apiPath, body);
            } catch (Exception ignored) {
            }
        }, "ha-rest-post").start();
    }

    public JSONObject post(String apiPath, JSONObject body) throws Exception {
        URL url = new URL(baseUrl + ensureApiPath(apiPath));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", HaAuthClient.USER_AGENT);
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8))) {
            writer.write(body == null ? "{}" : body.toString());
        }
        int code = conn.getResponseCode();
        InputStream stream = code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream();
        String text = readAll(stream);
        conn.disconnect();
        if (text == null || text.trim().isEmpty()) return new JSONObject();
        return new JSONObject(text);
    }


    public JSONObject get(String apiPath) throws Exception {
        URL url = new URL(baseUrl + ensureApiPath(apiPath));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", HaAuthClient.USER_AGENT);
        int code = conn.getResponseCode();
        InputStream stream = code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream();
        String text = readAll(stream);
        conn.disconnect();
        if (text == null || text.trim().isEmpty()) return new JSONObject();
        return new JSONObject(text);
    }

    public void fireEventAsync(String eventType, JSONObject eventData) {
        JSONObject body = eventData == null ? new JSONObject() : eventData;
        postAsync("/api/events/" + eventType, body);
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

    private static String ensureApiPath(String apiPath) {
        String value = apiPath == null ? "" : apiPath.trim();
        if (!value.startsWith("/")) value = "/" + value;
        return value;
    }
}
