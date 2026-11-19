package com.jestron.kayit.ban;

import com.jestron.kayit.KayitPlugin;
import com.jestron.kayit.core.Settings;
import com.jestron.kayit.storage.AltAccount;
import com.jestron.kayit.storage.MySQLDatabase;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * LiteBans. Eklenti bu sunucudaysa resmi API (litebans.api.Database) kullanilir; bu sayede
 * varsayilan H2 dahil tum depolama turleri okunur. Eklenti proxy'deyse MySQL ile okunur.
 */
public final class LiteBansProvider extends BanProvider {

    private static final String SOURCE = "LiteBans";

    private final KayitPlugin plugin;
    private ClassLoader apiLoader;
    private Method getMethod;
    private Method prepareMethod;

    public LiteBansProvider(KayitPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return SOURCE;
    }

    private Plugin liteBans() {
        Plugin p = Bukkit.getPluginManager().getPlugin("LiteBans");
        return p != null && p.isEnabled() ? p : null;
    }

    @Override
    public boolean isAvailable() {
        Settings s = plugin.settings();
        return s.liteBansEnabled && (liteBans() != null || s.liteBansSql.enabled);
    }

    @Override
    public boolean covers(String pluginName) {
        return SOURCE.equalsIgnoreCase(pluginName) && isAvailable();
    }

    /** API veya SQL uzerinden sorgu calistirir. */
    private interface RowHandler {
        void handle(ResultSet rs) throws SQLException;
    }

    private void query(String sqlWithPlaceholder, List<String> params, boolean activeParam, RowHandler handler)
            throws Exception {
        Plugin lb = liteBans();
        Settings s = plugin.settings();
        if (lb != null) {
            Object db = api(lb);
            PreparedStatement ps = (PreparedStatement) prepareMethod.invoke(db, sqlWithPlaceholder);
            try {
                bind(ps, params, activeParam);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        handler.handle(rs);
                    }
                }
            } finally {
                try {
                    ps.close();
                } catch (SQLException ignored) {
                    // yok say
                }
            }
            return;
        }
        if (s.liteBansSql.enabled) {
            String sql = sqlWithPlaceholder.replace("{bans}", s.liteBansPrefix + "bans");
            try (Connection c = MySQLDatabase.connect(false, s.liteBansSql.host, s.liteBansSql.port,
                    s.liteBansSql.database, s.liteBansSql.username, s.liteBansSql.password, false);
                 PreparedStatement ps = c.prepareStatement(sql)) {
                bind(ps, params, activeParam);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        handler.handle(rs);
                    }
                }
            }
        }
    }

    private static void bind(PreparedStatement ps, List<String> params, boolean activeParam) throws SQLException {
        int idx = 0;
        if (activeParam) {
            ps.setBoolean(++idx, true);
        }
        if (params != null) {
            for (String p : params) {
                ps.setString(++idx, p);
            }
        }
    }

    private Object api(Plugin lb) throws Exception {
        ClassLoader cl = lb.getClass().getClassLoader();
        if (apiLoader != cl || getMethod == null) {
            Class<?> dbClass = Class.forName("litebans.api.Database", true, cl);
            getMethod = dbClass.getMethod("get");
            prepareMethod = dbClass.getMethod("prepareStatement", String.class);
            apiLoader = cl;
        }
        Object db = getMethod.invoke(null);
        if (db == null) {
            throw new IllegalStateException("LiteBans API not ready");
        }
        return db;
    }

    @Override
    public List<BanEntry> fetchActive() throws Exception {
        final List<BanEntry> out = new ArrayList<BanEntry>();
        query("SELECT uuid, reason, banned_by_name, time, until FROM {bans} WHERE active = ?", null, true,
                new RowHandler() {
                    @Override
                    public void handle(ResultSet rs) throws SQLException {
                        out.add(new BanEntry(null, rs.getString("uuid"), rs.getString("reason"),
                                rs.getString("banned_by_name"), rs.getLong("time"), rs.getLong("until"),
                                true, SOURCE, 10));
                    }
                });
        return out;
    }

    @Override
    public List<BanEntry> fetchHistory(Collection<AltAccount> accounts) throws Exception {
        final List<BanEntry> out = new ArrayList<BanEntry>();
        List<String> ids = new ArrayList<String>();
        for (AltAccount a : accounts) {
            ids.add(a.getUuid().toString());
        }
        for (List<String> chunk : SqlRows.chunks(ids, 100)) {
            query("SELECT uuid, reason, banned_by_name, time, until, active FROM {bans} WHERE uuid IN ("
                            + SqlRows.placeholders(chunk.size()) + ") ORDER BY time DESC", chunk, false,
                    new RowHandler() {
                        @Override
                        public void handle(ResultSet rs) throws SQLException {
                            out.add(new BanEntry(null, rs.getString("uuid"), rs.getString("reason"),
                                    rs.getString("banned_by_name"), rs.getLong("time"), rs.getLong("until"),
                                    rs.getBoolean("active"), SOURCE, 10));
                        }
                    });
        }
        return out;
    }
}
