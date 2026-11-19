package com.jestron.kayit.ban;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Ban kaynaklari icin kucuk SQL yardimcilari. */
final class SqlRows {

    private SqlRows() {
    }

    static String placeholders(int n) {
        StringBuilder sb = new StringBuilder(n * 2);
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('?');
        }
        return sb.toString();
    }

    static void bindStrings(PreparedStatement ps, java.util.List<String> values, int offset) throws SQLException {
        for (int i = 0; i < values.size(); i++) {
            ps.setString(offset + i + 1, values.get(i));
        }
    }

    /** Listeyi en fazla size elemanli parcalara boler. */
    static <T> java.util.List<java.util.List<T>> chunks(java.util.List<T> list, int size) {
        java.util.List<java.util.List<T>> out = new java.util.ArrayList<java.util.List<T>>();
        for (int i = 0; i < list.size(); i += size) {
            out.add(new java.util.ArrayList<T>(list.subList(i, Math.min(list.size(), i + size))));
        }
        return out;
    }
}
