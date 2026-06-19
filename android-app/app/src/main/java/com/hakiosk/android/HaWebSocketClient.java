package com.hakiosk.android;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.net.ssl.SSLSocketFactory;

public class HaWebSocketClient {
    public interface Listener {
        void onStatus(String status);
        void onCommand(JSONObject data);
    }

    private static final String EVENT_TYPE = "ha_android_kiosk_command";

    private final String haBaseUrl;
    private final String token;
    private final String deviceId;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SecureRandom random = new SecureRandom();

    private volatile boolean shouldRun = false;
    private volatile Socket socket;
    private volatile OutputStream output;
    private int nextId = 1;

    public HaWebSocketClient(String haBaseUrl, String token, String deviceId, Listener listener) {
        this.haBaseUrl = haBaseUrl;
        this.token = token;
        this.deviceId = deviceId;
        this.listener = listener;
    }

    public void start() {
        shouldRun = true;
        new Thread(this::runLoop, "ha-websocket").start();
    }

    public void stop() {
        shouldRun = false;
        try {
            Socket s = socket;
            if (s != null) s.close();
        } catch (Exception ignored) {
        }
    }

    private void runLoop() {
        int delayMs = 1000;
        while (shouldRun) {
            try {
                connectOnce();
                delayMs = 1000;
            } catch (Exception e) {
                emitStatus("WebSocket getrennt: " + e.getMessage());
                sleep(delayMs);
                delayMs = Math.min(delayMs * 2, 30000);
            }
        }
    }

    private void connectOnce() throws Exception {
        URI uri = websocketUri();
        String scheme = uri.getScheme();
        boolean ssl = "wss".equalsIgnoreCase(scheme);
        int port = uri.getPort() != -1 ? uri.getPort() : (ssl ? 443 : 80);
        Socket s = ssl
            ? SSLSocketFactory.getDefault().createSocket(uri.getHost(), port)
            : new Socket(uri.getHost(), port);
        socket = s;
        output = s.getOutputStream();
        InputStream input = s.getInputStream();
        String key = createKey();
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) path = "/api/websocket";
        if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery();
        String hostHeader = uri.getHost() + ((uri.getPort() == -1) ? "" : ":" + uri.getPort());
        String request = "GET " + path + " HTTP/1.1\r\n"
            + "Host: " + hostHeader + "\r\n"
            + "Upgrade: websocket\r\n"
            + "Connection: Upgrade\r\n"
            + "Sec-WebSocket-Key: " + key + "\r\n"
            + "Sec-WebSocket-Version: 13\r\n\r\n";
        output.write(request.getBytes(StandardCharsets.US_ASCII));
        output.flush();
        String header = readHttpHeader(input);
        if (!header.startsWith("HTTP/1.1 101") && !header.startsWith("HTTP/1.0 101")) {
            throw new IllegalStateException("Handshake fehlgeschlagen");
        }
        emitStatus("WebSocket verbunden");
        while (shouldRun && !s.isClosed()) {
            String message = readTextFrame(input);
            if (message == null) throw new IllegalStateException("Verbindung geschlossen");
            handleMessage(message);
        }
    }

    private void handleMessage(String text) throws Exception {
        JSONObject msg = new JSONObject(text);
        String type = msg.optString("type", "");
        if ("auth_required".equals(type)) {
            JSONObject auth = new JSONObject();
            auth.put("type", "auth");
            auth.put("access_token", token);
            sendText(auth.toString());
            emitStatus("Authentifiziere bei Home Assistant ...");
            return;
        }
        if ("auth_ok".equals(type)) {
            JSONObject sub = new JSONObject();
            sub.put("id", nextId++);
            sub.put("type", "subscribe_events");
            sub.put("event_type", EVENT_TYPE);
            sendText(sub.toString());
            emitStatus("Lauscht auf " + EVENT_TYPE);
            return;
        }
        if ("auth_invalid".equals(type)) {
            emitStatus("Home-Assistant-Token ist ungültig");
            stop();
            return;
        }
        if ("event".equals(type)) {
            JSONObject event = msg.optJSONObject("event");
            if (event == null) return;
            JSONObject data = event.optJSONObject("data");
            if (data == null) return;
            String target = data.optString("device_id", "");
            if (target.isEmpty() || target.equals(deviceId) || target.equals("*") || target.equalsIgnoreCase("all")) {
                main.post(() -> listener.onCommand(data));
            }
        }
    }

    private URI websocketUri() throws Exception {
        String base = haBaseUrl.trim();
        if (base.startsWith("https://")) base = "wss://" + base.substring("https://".length());
        else if (base.startsWith("http://")) base = "ws://" + base.substring("http://".length());
        else base = "ws://" + base;
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return new URI(base + "/api/websocket");
    }

    private String createKey() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
    }

    private String readHttpHeader(InputStream input) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int state = 0;
        while (true) {
            int b = input.read();
            if (b < 0) throw new IllegalStateException("Kein HTTP-Header");
            buffer.write(b);
            if ((state == 0 || state == 2) && b == '\r') state++;
            else if ((state == 1 || state == 3) && b == '\n') state++;
            else state = 0;
            if (state == 4) break;
        }
        return buffer.toString(StandardCharsets.US_ASCII.name());
    }

    private String readTextFrame(InputStream input) throws Exception {
        int first = input.read();
        if (first < 0) return null;
        int second = input.read();
        if (second < 0) return null;
        int opcode = first & 0x0F;
        boolean masked = (second & 0x80) != 0;
        long length = second & 0x7F;
        if (length == 126) {
            length = ((long) readByte(input) << 8) | readByte(input);
        } else if (length == 127) {
            byte[] eight = readBytes(input, 8);
            length = ByteBuffer.wrap(eight).getLong();
        }
        byte[] mask = masked ? readBytes(input, 4) : null;
        byte[] payload = readBytes(input, (int) length);
        if (masked && mask != null) {
            for (int i = 0; i < payload.length; i++) payload[i] = (byte) (payload[i] ^ mask[i % 4]);
        }
        if (opcode == 0x8) return null;
        if (opcode == 0x9) {
            sendPong(payload);
            return readTextFrame(input);
        }
        if (opcode == 0xA) return readTextFrame(input);
        if (opcode != 0x1) return readTextFrame(input);
        return new String(payload, StandardCharsets.UTF_8);
    }

    private int readByte(InputStream input) throws Exception {
        int value = input.read();
        if (value < 0) throw new IllegalStateException("Unerwartetes Ende");
        return value;
    }

    private byte[] readBytes(InputStream input, int length) throws Exception {
        byte[] bytes = new byte[length];
        int offset = 0;
        while (offset < length) {
            int count = input.read(bytes, offset, length - offset);
            if (count < 0) throw new IllegalStateException("Unerwartetes Ende");
            offset += count;
        }
        return bytes;
    }

    private synchronized void sendText(String text) throws Exception {
        sendFrame(0x1, text.getBytes(StandardCharsets.UTF_8));
    }

    private synchronized void sendPong(byte[] payload) throws Exception {
        sendFrame(0xA, payload == null ? new byte[0] : payload);
    }

    private synchronized void sendFrame(int opcode, byte[] payload) throws Exception {
        OutputStream out = output;
        if (out == null) return;
        int length = payload.length;
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0x80 | opcode);
        byte[] mask = new byte[4];
        random.nextBytes(mask);
        if (length <= 125) {
            frame.write(0x80 | length);
        } else if (length <= 65535) {
            frame.write(0x80 | 126);
            frame.write((length >> 8) & 0xFF);
            frame.write(length & 0xFF);
        } else {
            frame.write(0x80 | 127);
            byte[] lenBytes = ByteBuffer.allocate(8).putLong(length).array();
            frame.write(lenBytes);
        }
        frame.write(mask);
        byte[] maskedPayload = Arrays.copyOf(payload, payload.length);
        for (int i = 0; i < maskedPayload.length; i++) maskedPayload[i] = (byte) (maskedPayload[i] ^ mask[i % 4]);
        frame.write(maskedPayload);
        out.write(frame.toByteArray());
        out.flush();
    }

    private void emitStatus(String status) {
        main.post(() -> listener.onStatus(status));
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
