package com.jestron.kayit.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Ban komutlarindaki sure ifadelerini (1d, 2h30m, 1w, 1mo, 7days ...) cozer. */
public final class Durations {

    private static final Pattern PART = Pattern.compile(
            "(\\d+)\\s*(years?|y|months?|mo|weeks?|w|days?|d|hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)");

    private Durations() {
    }

    /** @return milisaniye; sure degilse -1 */
    public static long parse(String input) {
        if (input == null) {
            return -1L;
        }
        String s = input.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty() || !Character.isDigit(s.charAt(0))) {
            return -1L;
        }
        Matcher m = PART.matcher(s);
        long total = 0L;
        int end = 0;
        while (m.find()) {
            if (m.start() != end) {
                return -1L;
            }
            end = m.end();
            long n;
            try {
                n = Long.parseLong(m.group(1));
            } catch (NumberFormatException e) {
                return -1L;
            }
            String u = m.group(2);
            long unit;
            if (u.startsWith("y")) {
                unit = 365L * 86400000L;
            } else if (u.startsWith("mo")) {
                unit = 30L * 86400000L;
            } else if (u.startsWith("w")) {
                unit = 7L * 86400000L;
            } else if (u.startsWith("d")) {
                unit = 86400000L;
            } else if (u.startsWith("h")) {
                unit = 3600000L;
            } else if (u.startsWith("m")) {
                unit = 60000L;
            } else {
                unit = 1000L;
            }
            total += n * unit;
        }
        return end == s.length() && total > 0 ? total : -1L;
    }
}
