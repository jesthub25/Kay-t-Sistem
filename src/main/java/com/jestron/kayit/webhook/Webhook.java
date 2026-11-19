package com.jestron.kayit.webhook;

import com.jestron.kayit.KayitPlugin;
import com.jestron.kayit.core.Settings;
import com.jestron.kayit.util.Text;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Istege bagli Discord webhook kaydi (arka planda gonderilir). */
public final class Webhook {

    public static final int COLOR_INFO = 0x1A73E8;
    public static final int COLOR_OK = 0x2ECC71;
    public static final int COLOR_WARN = 0xE67E22;
    public static final int COLOR_ALERT = 0xE74C3C;

    private final KayitPlugin plugin;

    public Webhook(KayitPlugin plugin) {
        this.plugin = plugin;
    }

    public void send(final String title, final String description, final int color) {
        final Settings s = plugin.settings();
        if (!s.webhookEnabled || s.webhookUrl.isEmpty() || !s.webhookUrl.startsWith("https://")) {
            return;
        }
        plugin.tasks().async(new Runnable() {
            @Override
            public void run() {
                post(s.webhookUrl, s.webhookUsername, title, description, color);
            }
        });
    }

    private void post(String url, String username, String title, String description, int color) {
        HttpURLConnection conn = null;
        try {
            String json = "{\"username\":\"" + json(username) + "\",\"embeds\":[{\"title\":\"" + json(Text.strip(title))
                    + "\",\"description\":\"" + json(Text.strip(description)) + "\",\"color\":" + color
                    + ",\"footer\":{\"text\":\"" + json(plugin.settings().serverName) + "\"}}]}";
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("User-Agent", "KayitPlugin/" + plugin.getDescription().getVersion());
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                plugin.getLogger().warning("Discord webhook HTTP " + code);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Discord webhook failed: " + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String json(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
