package com.jestron.kayit.core;

import com.jestron.kayit.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Dil dosyasi yoneticisi. plugins/KayitPlugin/lang/<dil>.yml okunur; eksik anahtarlar JAR'dan tamamlanir.
 */
public final class Lang {

    private static final String[] BUNDLED = {"tr", "en"};

    private final JavaPlugin plugin;
    private YamlConfiguration yaml = new YamlConfiguration();
    private String code = "tr";
    private String prefix = "";

    public Lang(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(String requested) {
        File dir = new File(plugin.getDataFolder(), "lang");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Could not create lang folder: " + dir.getAbsolutePath());
        }
        for (String b : BUNDLED) {
            File f = new File(dir, b + ".yml");
            if (!f.exists()) {
                try {
                    plugin.saveResource("lang/" + b + ".yml", false);
                } catch (Exception e) {
                    plugin.getLogger().warning("Could not save lang/" + b + ".yml: " + e.getMessage());
                }
            }
        }

        String c = requested == null ? "tr" : requested.trim().toLowerCase(Locale.ROOT);
        File file = new File(dir, c + ".yml");
        if (!file.exists()) {
            plugin.getLogger().warning("Language file lang/" + c + ".yml not found, falling back to 'tr'.");
            c = "tr";
            file = new File(dir, "tr.yml");
        }

        YamlConfiguration loaded = new YamlConfiguration();
        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            loaded.load(r);
        } catch (Exception e) {
            plugin.getLogger().severe("Language file " + file.getName() + " is broken (" + e.getMessage()
                    + "). Using built-in defaults.");
            loaded = new YamlConfiguration();
        }

        String defName = isBundled(c) ? c : "en";
        YamlConfiguration defaults = readResource("lang/" + defName + ".yml");
        if (defaults != null) {
            loaded.setDefaults(defaults);
        }
        this.yaml = loaded;
        this.code = c;
        this.prefix = Text.color(yaml.getString("prefix", ""));
    }

    private boolean isBundled(String c) {
        for (String b : BUNDLED) {
            if (b.equals(c)) {
                return true;
            }
        }
        return false;
    }

    private YamlConfiguration readResource(String path) {
        InputStream in = plugin.getResource(path);
        if (in == null) {
            return null;
        }
        YamlConfiguration y = new YamlConfiguration();
        try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            y.load(r);
            return y;
        } catch (Exception e) {
            return null;
        }
    }

    public String code() {
        return code;
    }

    /** Ham (renksiz) deger. Bulunamazsa anahtarin kendisi doner. */
    public String raw(String path) {
        Object o = yaml.get(path);
        if (o == null) {
            return path;
        }
        if (o instanceof List) {
            List<?> l = (List<?>) o;
            StringBuilder sb = new StringBuilder();
            for (Object x : l) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(String.valueOf(x));
            }
            return sb.toString();
        }
        return String.valueOf(o);
    }

    public String rawOr(String path, String def) {
        String v = yaml.getString(path);
        return v == null ? def : v;
    }

    public List<String> rawList(String path) {
        if (!yaml.contains(path)) {
            return Collections.emptyList();
        }
        if (yaml.isList(path)) {
            return yaml.getStringList(path);
        }
        String s = yaml.getString(path);
        if (s == null) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<String>();
        Collections.addAll(out, s.split("\n", -1));
        return out;
    }

    /** Renkli, yer tutuculari doldurulmus mesaj. */
    public String get(String path, Object... kv) {
        return format(raw(path), kv);
    }

    public String format(String rawText, Object... kv) {
        String s = Text.replace(rawText, kv);
        s = s.replace("{prefix}", prefix);
        return Text.color(s);
    }

    public List<String> list(String path, Object... kv) {
        List<String> in = rawList(path);
        List<String> out = new ArrayList<String>(in.size());
        for (String s : in) {
            out.add(format(s, kv));
        }
        return out;
    }

    /** Mesaji gonderir. Bos mesajlar ("") gonderilmez. Cok satirli mesajlar desteklenir. */
    public void send(CommandSender to, String path, Object... kv) {
        if (to == null) {
            return;
        }
        Object o = yaml.get(path);
        if (o instanceof List) {
            for (String line : list(path, kv)) {
                to.sendMessage(line);
            }
            return;
        }
        String msg = raw(path);
        if (msg.isEmpty()) {
            return;
        }
        for (String line : format(msg, kv).split("\n", -1)) {
            to.sendMessage(line);
        }
    }

    public boolean isEmpty(String path) {
        Object o = yaml.get(path);
        return o == null || (o instanceof String && ((String) o).isEmpty());
    }

    // ---------------------------------------------------------------- zaman

    public String date(Settings s, long epochMs) {
        if (epochMs <= 0) {
            return raw("alts.unknown-date");
        }
        try {
            return s.dateFormat.withZone(s.zone).format(Instant.ofEpochMilli(epochMs));
        } catch (Exception e) {
            return String.valueOf(epochMs);
        }
    }

    /** "2 gun", "5 dakika" gibi goreli sure (gecmis zaman eki dil dosyasindadir). */
    public String duration(long millis) {
        long sec = Math.max(0, millis / 1000L);
        long[] sizes = {365L * 86400L, 30L * 86400L, 86400L, 3600L, 60L, 1L};
        String[] keys = {"year", "month", "day", "hour", "minute", "second"};
        for (int i = 0; i < sizes.length; i++) {
            long n = sec / sizes[i];
            if (n >= 1 || i == sizes.length - 1) {
                String key = "time." + keys[i] + (n == 1 ? "" : "s");
                return Text.replace(raw(key), "n", n);
            }
        }
        return Text.replace(raw("time.seconds"), "n", sec);
    }
}
