package com.jestron.kayit.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Dogrulama yardimcilari (e-posta, kullanici adi, UUID anahtari, kod).
 */
public final class Checks {

    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$");
    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_.*]{1,32}$");
    private static final Pattern HEX32 = Pattern.compile("^[0-9a-f]{32}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private Checks() {
    }

    public static boolean isEmail(String s) {
        return s != null && s.length() <= 254 && EMAIL.matcher(s).matches();
    }

    public static boolean isPlayerName(String s) {
        return s != null && NAME.matcher(s).matches();
    }

    /** UUID'yi tiresiz kucuk harf 32 karakterli anahtara cevirir; gecersizse null. */
    public static String uuidKey(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().replace("-", "").toLowerCase(Locale.ROOT);
        return HEX32.matcher(s).matches() ? s : null;
    }

    public static String uuidKey(UUID uuid) {
        return uuid == null ? null : uuidKey(uuid.toString());
    }

    public static UUID parseUuid(String raw) {
        String key = uuidKey(raw);
        if (key == null) {
            return null;
        }
        String dashed = key.substring(0, 8) + "-" + key.substring(8, 12) + "-" + key.substring(12, 16)
                + "-" + key.substring(16, 20) + "-" + key.substring(20);
        try {
            return UUID.fromString(dashed);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String lower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }

    public static String randomDigits(int length) {
        int len = Math.max(4, Math.min(10, length));
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    /** Zamanlama saldirilarina karsi sabit sureli karsilastirma. */
    public static boolean safeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
