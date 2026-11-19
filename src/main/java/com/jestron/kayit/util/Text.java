package com.jestron.kayit.util;

import org.bukkit.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renk ve metin yardimcilari. &-kodlari ve 1.16+ HEX (&#RRGGBB) desteklenir.
 */
public final class Text {

    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final char SECTION = '§';

    private Text() {
    }

    public static String color(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String s = input;
        if (s.indexOf("&#") >= 0) {
            Matcher m = HEX.matcher(s);
            StringBuffer sb = new StringBuffer(s.length() + 32);
            while (m.find()) {
                String hex = m.group(1);
                StringBuilder rep = new StringBuilder(14);
                rep.append(SECTION).append('x');
                for (int i = 0; i < hex.length(); i++) {
                    rep.append(SECTION).append(hex.charAt(i));
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(rep.toString()));
            }
            m.appendTail(sb);
            s = sb.toString();
        }
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public static String strip(String input) {
        if (input == null) {
            return "";
        }
        String s = ChatColor.stripColor(color(input));
        return s == null ? "" : s;
    }

    /** {anahtar} -> deger degistirme. kv: anahtar, deger, anahtar, deger ... */
    public static String replace(String input, Object... kv) {
        if (input == null) {
            return "";
        }
        if (kv == null || kv.length < 2 || input.indexOf('{') < 0) {
            return input;
        }
        String s = input;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            String key = String.valueOf(kv[i]);
            Object val = kv[i + 1];
            s = s.replace("{" + key + "}", val == null ? "" : String.valueOf(val));
        }
        return s;
    }

    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    public static String join(Iterable<String> parts, String sep) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) {
                sb.append(sep);
            }
            sb.append(p);
        }
        return sb.toString();
    }
}
