package com.jestron.kayit.ban;

import com.jestron.kayit.KayitPlugin;
import com.jestron.kayit.core.Settings;
import com.jestron.kayit.storage.AltAccount;
import com.jestron.kayit.storage.MySQLDatabase;
import com.jestron.kayit.util.Checks;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * AdvancedBan. Eklenti bu sunucudaysa API ile (varsayilan HSQLDB dahil) okunur,
 * proxy kurulumunda MySQL tablolari (Punishments / PunishmentHistory) okunur.
 */
public final class AdvancedBanProvider extends BanProvider {

    private static final String SOURCE = "AdvancedBan";

    private final KayitPlugin plugin;
    private ClassLoader apiLoader;
    private Object manager;
    private Method getPunishments;
    private Object selectAll;
    private Object selectHistory;
    private final Map<Class<?>, Map<String, Method>> getters = new HashMap<Class<?>, Map<String, Method>>();

    public AdvancedBanProvider(KayitPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return SOURCE;
    }

    private Plugin advancedBan() {
        Plugin p = Bukkit.getPluginManager().getPlugin("AdvancedBan");
        return p != null && p.isEnabled() ? p : null;
    }

    @Override
    public boolean isAvailable() {
        Settings s = plugin.settings();
        return s.advancedBanEnabled && (advancedBan() != null || s.advancedBanSql.enabled);
    }

    @Override
    public boolean covers(String pluginName) {
        return SOURCE.equalsIgnoreCase(pluginName) && isAvailable();
    }

    // ---------------------------------------------------------------- API

    private synchronized void initApi(Plugin ab) throws Exception {
        ClassLoader cl = ab.getClass().getClassLoader();
        if (apiLoader == cl && manager != null) {
            return;
        }
        Class<?> pm = Class.forName("me.leoko.advancedban.manager.PunishmentManager", true, cl);
        Class<?> query = Class.forName("me.leoko.advancedban.utils.SQLQuery", true, cl);
        Object mgr = pm.getMethod("get").invoke(null);
        Method m = pm.getMethod("getPunishments", query, Object[].class);
        Object all = null;
        Object hist = null;
        Object[] constants = query.getEnumConstants();
        if (constants != null) {
            for (Object c : constants) {
                String n = ((Enum<?>) c).name();
                if ("SELECT_ALL_PUNISHMENTS".equals(n)) {
                    all = c;
                } else if ("SELECT_ALL_PUNISHMENTS_HISTORY".equals(n)) {
                    hist = c;
                }
            }
        }
        if (all == null) {
            throw new IllegalStateException("Unsupported AdvancedBan version (SQLQuery.SELECT_ALL_PUNISHMENTS missing)");
        }
        manager = mgr;
        getPunishments = m;
        selectAll = all;
        selectHistory = hist;
        apiLoader = cl;
        getters.clear();
    }

    private synchronized Object call(Object target, String method) throws Exception {
        Class<?> cls = target.getClass();
        Map<String, Method> map = getters.get(cls);
        if (map == null) {
            map = new HashMap<String, Method>();
            getters.put(cls, map);
        }
        Method m = map.get(method);
        if (m == null) {
            m = cls.getMethod(method);
            m.setAccessible(true);
            map.put(method, m);
        }
        return m.invoke(target);
    }

    private List<BanEntry> fromApi(Object query, boolean active) throws Exception {
        List<BanEntry> out = new ArrayList<BanEntry>();
        Object result = getPunishments.invoke(manager, query, new Object[0]);
        if (!(result instanceof Collection)) {
            return out;
        }
        for (Object p : (Collection<?>) result) {
            if (p == null) {
                continue;
            }
            Object type = call(p, "getType");
            String typeName = type == null ? "" : String.valueOf(type).toUpperCase(Locale.ROOT);
            if (!typeName.contains("BAN")) {
                continue;
            }
            out.add(new BanEntry(str(call(p, "getName")), str(call(p, "getUuid")), str(call(p, "getReason")),
                    str(call(p, "getOperator")), num(call(p, "getStart")), num(call(p, "getEnd")), active,
                    SOURCE, 10));
        }
        return out;
    }

    // ---------------------------------------------------------------- SQL

    private List<BanEntry> fromSql(String table, boolean active, List<String> keys, List<String> names)
            throws Exception {
        Settings s = plugin.settings();
        StringBuilder sql = new StringBuilder("SELECT `name`, `uuid`, `reason`, `operator`, `punishmentType`, `start`, `end` FROM ")
                .append(table).append(" WHERE punishmentType LIKE '%BAN%'");
        List<String> params = new ArrayList<String>();
        if (keys != null || names != null) {
            sql.append(" AND (1=0");
            if (keys != null && !keys.isEmpty()) {
                sql.append(" OR `uuid` IN (").append(SqlRows.placeholders(keys.size())).append(')');
                params.addAll(keys);
            }
            if (names != null && !names.isEmpty()) {
                sql.append(" OR LOWER(`name`) IN (").append(SqlRows.placeholders(names.size())).append(')');
                params.addAll(names);
            }
            sql.append(')');
        }
        List<BanEntry> out = new ArrayList<BanEntry>();
        try (Connection c = MySQLDatabase.connect(false, s.advancedBanSql.host, s.advancedBanSql.port,
                s.advancedBanSql.database, s.advancedBanSql.username, s.advancedBanSql.password, false);
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            SqlRows.bindStrings(ps, params, 0);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new BanEntry(rs.getString("name"), rs.getString("uuid"), rs.getString("reason"),
                            rs.getString("operator"), rs.getLong("start"), rs.getLong("end"), active, SOURCE, 10));
                }
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- BanProvider

    @Override
    public List<BanEntry> fetchActive() throws Exception {
        Plugin ab = advancedBan();
        if (ab != null) {
            initApi(ab);
            return fromApi(selectAll, true);
        }
        if (plugin.settings().advancedBanSql.enabled) {
            return fromSql("Punishments", true, null, null);
        }
        return new ArrayList<BanEntry>();
    }

    @Override
    public List<BanEntry> fetchHistory(Collection<AltAccount> accounts) throws Exception {
        Set<String> keys = new HashSet<String>();
        Set<String> names = new HashSet<String>();
        for (AltAccount a : accounts) {
            String k = Checks.uuidKey(a.getUuid());
            if (k != null) {
                keys.add(k);
            }
            if (a.getName() != null) {
                names.add(a.getName().toLowerCase(Locale.ROOT));
            }
        }
        List<BanEntry> all;
        Plugin ab = advancedBan();
        if (ab != null) {
            initApi(ab);
            if (selectHistory == null) {
                return new ArrayList<BanEntry>();
            }
            all = fromApi(selectHistory, false);
        } else if (plugin.settings().advancedBanSql.enabled) {
            List<String> keyList = new ArrayList<String>();
            for (String k : keys) {
                keyList.add(k);
                keyList.add(Checks.parseUuid(k).toString());
            }
            all = fromSql("PunishmentHistory", false, keyList, new ArrayList<String>(names));
        } else {
            return new ArrayList<BanEntry>();
        }
        List<BanEntry> out = new ArrayList<BanEntry>();
        for (BanEntry e : all) {
            boolean match = (e.getUuidKey() != null && keys.contains(e.getUuidKey()))
                    || (e.getName() != null && names.contains(e.getName().toLowerCase(Locale.ROOT)));
            if (match) {
                out.add(e);
            }
        }
        return out;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static long num(Object o) {
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        try {
            return o == null ? -1L : Long.parseLong(String.valueOf(o));
        } catch (NumberFormatException e) {
            return -1L;
        }
    }
}
