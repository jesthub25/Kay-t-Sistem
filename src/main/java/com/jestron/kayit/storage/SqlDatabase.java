package com.jestron.kayit.storage;

import com.jestron.kayit.util.Checks;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * SQLite ve MySQL/MariaDB icin ortak, tasinabilir SQL uygulamasi.
 * Tum public metodlar synchronized: tek baglanti, sifir yaris durumu.
 * UPSERT yerine UPDATE -> INSERT kullanilir (tum SQLite/MySQL surumlerinde calisir).
 */
public abstract class SqlDatabase implements Database {

    private static final int SCHEMA_VERSION = 2;
    private static final int MAX_IN = 100;

    protected final Logger logger;
    private Connection connection;
    private long lastValidation;

    protected SqlDatabase(Logger logger) {
        this.logger = logger;
    }

    /** Yeni JDBC baglantisi acar. */
    protected abstract Connection open() throws Exception;

    protected abstract boolean isMySql();

    // ---------------------------------------------------------------- baglanti

    protected final synchronized Connection conn() throws SQLException {
        long now = System.currentTimeMillis();
        try {
            if (connection == null || connection.isClosed()) {
                connection = open();
                lastValidation = now;
            } else if (isMySql() && now - lastValidation > 30000L) {
                lastValidation = now;
                if (!connection.isValid(3)) {
                    closeQuietly();
                    connection = open();
                }
            }
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw new SQLException(e.getMessage(), e);
        }
        if (connection == null) {
            throw new SQLException("Could not open database connection");
        }
        return connection;
    }

    private void closeQuietly() {
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception ignored) {
                // yok say
            }
            connection = null;
        }
    }

    @Override
    public synchronized void close() {
        closeQuietly();
    }

    // ---------------------------------------------------------------- tipler

    private String txt(int len) {
        return isMySql() ? "VARCHAR(" + len + ")" : "TEXT";
    }

    private String big() {
        return isMySql() ? "BIGINT" : "INTEGER";
    }

    private String engine() {
        return isMySql() ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4" : "";
    }

    // ---------------------------------------------------------------- sema

    @Override
    public synchronized void init() throws Exception {
        Connection c = conn();
        try (Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS kp_meta (meta_key " + txt(64) + " NOT NULL PRIMARY KEY, "
                    + "meta_value " + txt(255) + ")" + engine());
            st.executeUpdate("CREATE TABLE IF NOT EXISTS players ("
                    + "uuid " + txt(36) + " NOT NULL PRIMARY KEY, "
                    + "name " + txt(32) + " NOT NULL, "
                    + "password_hash " + txt(255) + " NOT NULL DEFAULT '', "
                    + "email " + txt(255) + ", "
                    + "email_verified INTEGER NOT NULL DEFAULT 0, "
                    + "registered_at " + big() + " NOT NULL DEFAULT 0, "
                    + "last_login " + big() + " NOT NULL DEFAULT 0, "
                    + "last_seen " + big() + " NOT NULL DEFAULT 0, "
                    + "last_ip " + txt(45) + ")" + engine());
            st.executeUpdate("CREATE TABLE IF NOT EXISTS kp_account_ips ("
                    + "uuid " + txt(36) + " NOT NULL, "
                    + "ip " + txt(45) + " NOT NULL, "
                    + "first_seen " + big() + " NOT NULL DEFAULT 0, "
                    + "last_seen " + big() + " NOT NULL DEFAULT 0, "
                    + "registered INTEGER NOT NULL DEFAULT 0, "
                    + "PRIMARY KEY (uuid, ip))" + engine());
            st.executeUpdate("CREATE TABLE IF NOT EXISTS kp_sessions ("
                    + "uuid " + txt(36) + " NOT NULL PRIMARY KEY, "
                    + "ip " + txt(45) + " NOT NULL, "
                    + "expires_at " + big() + " NOT NULL DEFAULT 0)" + engine());
            st.executeUpdate("CREATE TABLE IF NOT EXISTS kp_ban_log ("
                    + (isMySql() ? "id BIGINT AUTO_INCREMENT PRIMARY KEY, " : "id INTEGER PRIMARY KEY AUTOINCREMENT, ")
                    + "name_lc " + txt(32) + " NOT NULL, "
                    + "reason " + txt(255) + ", "
                    + "issuer " + txt(64) + ", "
                    + "created " + big() + " NOT NULL DEFAULT 0, "
                    + "expires " + big() + " NOT NULL DEFAULT -1, "
                    + "active INTEGER NOT NULL DEFAULT 1, "
                    + "plugin " + txt(64) + ", "
                    + "removed_by " + txt(64) + ", "
                    + "removed_at " + big() + " NOT NULL DEFAULT 0)" + engine());
        }

        // Eski surumlerden (v1.x) gelen players tablosuna eksik sutunlari ekle
        addColumnIfMissing("players", "last_login", big() + " NOT NULL DEFAULT 0");
        addColumnIfMissing("players", "last_seen", big() + " NOT NULL DEFAULT 0");
        addColumnIfMissing("players", "last_ip", txt(45));

        createIndex("idx_kp_players_name", "players", "name");
        createIndex("idx_kp_aip_ip", "kp_account_ips", "ip");
        createIndex("idx_kp_banlog_name", "kp_ban_log", "name_lc");

        migrate();
    }

    private boolean columnExists(String table, String column) {
        try (Statement st = conn().createStatement()) {
            st.setMaxRows(1);
            try (ResultSet rs = st.executeQuery("SELECT " + column + " FROM " + table + " WHERE 1=0")) {
                return true;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private boolean tableExists(String table) {
        try (Statement st = conn().createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT 1 FROM " + table + " WHERE 1=0")) {
                return true;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private void addColumnIfMissing(String table, String column, String definition) throws SQLException {
        if (columnExists(table, column)) {
            return;
        }
        try (Statement st = conn().createStatement()) {
            st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private void createIndex(String name, String table, String column) {
        String sql = isMySql()
                ? "CREATE INDEX " + name + " ON " + table + " (" + column + ")"
                : "CREATE INDEX IF NOT EXISTS " + name + " ON " + table + " (" + column + ")";
        try (Statement st = conn().createStatement()) {
            st.executeUpdate(sql);
        } catch (SQLException ignored) {
            // MySQL: index zaten var -> sorun degil
        }
    }

    private void migrate() throws SQLException {
        int version = 0;
        String v = getMeta("schema");
        if (v != null) {
            try {
                version = Integer.parseInt(v.trim());
            } catch (NumberFormatException ignored) {
                version = 0;
            }
        }
        if (version >= SCHEMA_VERSION) {
            return;
        }

        if (tableExists("player_ips") && count("SELECT COUNT(*) FROM kp_account_ips") == 0) {
            try (Statement st = conn().createStatement()) {
                int n = st.executeUpdate("INSERT INTO kp_account_ips (uuid, ip, first_seen, last_seen, registered) "
                        + "SELECT uuid, ip, MIN(timestamp), MAX(timestamp), "
                        + "MAX(CASE WHEN type = 'REGISTER' THEN 1 ELSE 0 END) "
                        + "FROM player_ips GROUP BY uuid, ip");
                logger.info("[Migration] " + n + " IP records moved to kp_account_ips.");
            } catch (SQLException e) {
                logger.warning("[Migration] player_ips could not be migrated: " + e.getMessage());
            }
        }
        if (tableExists("last_login")) {
            try (Statement st = conn().createStatement()) {
                st.executeUpdate("UPDATE players SET "
                        + "last_login = COALESCE((SELECT l.timestamp FROM last_login l WHERE l.uuid = players.uuid), last_login), "
                        + "last_ip = COALESCE((SELECT l.ip FROM last_login l WHERE l.uuid = players.uuid), last_ip)");
            } catch (SQLException e) {
                logger.warning("[Migration] last_login could not be migrated: " + e.getMessage());
            }
        }
        setMeta("schema", String.valueOf(SCHEMA_VERSION));
    }

    private int count(String sql) throws SQLException {
        try (Statement st = conn().createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private String getMeta(String key) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("SELECT meta_value FROM kp_meta WHERE meta_key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private void setMeta(String key, String value) throws SQLException {
        if (update("UPDATE kp_meta SET meta_value = ? WHERE meta_key = ?", value, key) == 0) {
            update("INSERT INTO kp_meta (meta_key, meta_value) VALUES (?, ?)", key, value);
        }
    }

    // ---------------------------------------------------------------- yardimcilar

    private int update(String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        }
    }

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object p = params[i];
            if (p == null) {
                ps.setNull(i + 1, java.sql.Types.VARCHAR);
            } else if (p instanceof Long) {
                ps.setLong(i + 1, (Long) p);
            } else if (p instanceof Integer) {
                ps.setInt(i + 1, (Integer) p);
            } else if (p instanceof Boolean) {
                ps.setInt(i + 1, ((Boolean) p) ? 1 : 0);
            } else {
                ps.setString(i + 1, String.valueOf(p));
            }
        }
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String placeholders(int n) {
        StringBuilder sb = new StringBuilder(n * 2);
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('?');
        }
        return sb.toString();
    }

    private static PlayerAccount mapAccount(ResultSet rs) throws SQLException {
        UUID uuid = Checks.parseUuid(rs.getString("uuid"));
        if (uuid == null) {
            return null;
        }
        return new PlayerAccount(uuid, rs.getString("name"), rs.getString("password_hash"),
                rs.getString("email"), rs.getInt("email_verified") == 1, rs.getLong("registered_at"),
                rs.getLong("last_login"), rs.getLong("last_seen"), rs.getString("last_ip"));
    }

    private static final String ACCOUNT_COLUMNS =
            "uuid, name, password_hash, email, email_verified, registered_at, last_login, last_seen, last_ip";

    // ---------------------------------------------------------------- hesaplar

    @Override
    public synchronized PlayerAccount getAccount(UUID uuid) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT " + ACCOUNT_COLUMNS + " FROM players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapAccount(rs) : null;
            }
        }
    }

    @Override
    public synchronized PlayerAccount getAccountByName(String name) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT " + ACCOUNT_COLUMNS + " FROM players WHERE LOWER(name) = LOWER(?) ORDER BY last_login DESC")) {
            ps.setMaxRows(1);
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapAccount(rs) : null;
            }
        }
    }

    @Override
    public synchronized void saveRegistration(UUID uuid, String name, String passwordHash, String email,
                                              boolean emailVerified, String ip, long now) throws SQLException {
        PlayerAccount existing = getAccount(uuid);
        String finalEmail = email;
        boolean verified = emailVerified;
        if (finalEmail == null && existing != null) {
            finalEmail = existing.getEmail();
            verified = existing.isEmailVerified();
        }
        if (existing != null) {
            update("UPDATE players SET name = ?, password_hash = ?, email = ?, email_verified = ?, registered_at = ? "
                    + "WHERE uuid = ?", cut(name, 32), passwordHash, cut(finalEmail, 255), verified, now, uuid.toString());
        } else {
            update("INSERT INTO players (uuid, name, password_hash, email, email_verified, registered_at, "
                            + "last_login, last_seen, last_ip) VALUES (?, ?, ?, ?, ?, ?, 0, 0, ?)",
                    uuid.toString(), cut(name, 32), passwordHash, cut(finalEmail, 255), verified, now, cut(ip, 45));
        }
        upsertIp(uuid, ip, now, true);
    }

    @Override
    public synchronized void setPassword(UUID uuid, String passwordHash) throws SQLException {
        update("UPDATE players SET password_hash = ? WHERE uuid = ?", passwordHash == null ? "" : passwordHash,
                uuid.toString());
    }

    @Override
    public synchronized void setEmail(UUID uuid, String email, boolean verified) throws SQLException {
        update("UPDATE players SET email = ?, email_verified = ? WHERE uuid = ?", cut(email, 255),
                email != null && verified, uuid.toString());
    }

    @Override
    public synchronized int countAccountsByEmail(String email, UUID exclude) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT COUNT(*) FROM players WHERE LOWER(email) = LOWER(?) AND uuid <> ? AND password_hash <> ''")) {
            ps.setString(1, email);
            ps.setString(2, exclude == null ? "" : exclude.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // ---------------------------------------------------------------- IP / aktiflik

    private void upsertIp(UUID uuid, String ip, long now, boolean registered) throws SQLException {
        if (ip == null || ip.isEmpty()) {
            return;
        }
        String id = uuid.toString();
        String cutIp = cut(ip, 45);
        String sql = registered
                ? "UPDATE kp_account_ips SET last_seen = ?, registered = 1 WHERE uuid = ? AND ip = ?"
                : "UPDATE kp_account_ips SET last_seen = ? WHERE uuid = ? AND ip = ?";
        if (update(sql, now, id, cutIp) > 0) {
            return;
        }
        try {
            update("INSERT INTO kp_account_ips (uuid, ip, first_seen, last_seen, registered) VALUES (?, ?, ?, ?, ?)",
                    id, cutIp, now, now, registered ? 1 : 0);
        } catch (SQLException e) {
            // Baska bir sunucu ayni anda ekledi (paylasilan MySQL) -> guncelle
            update(sql, now, id, cutIp);
        }
    }

    @Override
    public synchronized void recordLogin(UUID uuid, String name, String ip, long now) throws SQLException {
        int rows = update("UPDATE players SET name = ?, last_login = ?, last_ip = ? WHERE uuid = ?",
                cut(name, 32), now, cut(ip, 45), uuid.toString());
        if (rows > 0) {
            upsertIp(uuid, ip, now, false);
        }
    }

    @Override
    public synchronized void recordQuit(UUID uuid, long now) throws SQLException {
        update("UPDATE players SET last_seen = ? WHERE uuid = ?", now, uuid.toString());
    }

    @Override
    public synchronized int countRegistrationsByIp(String ip, UUID exclude) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT COUNT(DISTINCT a.uuid) FROM kp_account_ips a INNER JOIN players p ON p.uuid = a.uuid "
                        + "WHERE a.ip = ? AND a.registered = 1 AND p.password_hash <> '' AND a.uuid <> ?")) {
            ps.setString(1, ip);
            ps.setString(2, exclude == null ? "" : exclude.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    @Override
    public synchronized List<String> getIps(UUID uuid, int limit) throws SQLException {
        List<String> out = new ArrayList<String>();
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT ip FROM kp_account_ips WHERE uuid = ? ORDER BY last_seen DESC")) {
            ps.setMaxRows(Math.max(1, limit));
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        }
        return out;
    }

    @Override
    public synchronized List<AltAccount> getAccountsByIps(Collection<String> ips, int limit) throws SQLException {
        if (ips == null || ips.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> list = new ArrayList<String>(new LinkedHashSet<String>(ips));
        if (list.size() > MAX_IN) {
            list = list.subList(0, MAX_IN);
        }
        String sql = "SELECT p.uuid, p.name, p.password_hash, p.registered_at, p.last_login, p.last_seen, "
                + "COUNT(DISTINCT a.ip) AS shared FROM kp_account_ips a INNER JOIN players p ON p.uuid = a.uuid "
                + "WHERE a.ip IN (" + placeholders(list.size()) + ") "
                + "GROUP BY p.uuid, p.name, p.password_hash, p.registered_at, p.last_login, p.last_seen "
                + "ORDER BY p.last_login DESC";
        List<AltAccount> out = new ArrayList<AltAccount>();
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setMaxRows(Math.max(1, limit));
            for (int i = 0; i < list.size(); i++) {
                ps.setString(i + 1, list.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID id = Checks.parseUuid(rs.getString("uuid"));
                    if (id == null) {
                        continue;
                    }
                    String hash = rs.getString("password_hash");
                    out.add(new AltAccount(id, rs.getString("name"), hash != null && !hash.isEmpty(),
                            rs.getLong("registered_at"), rs.getLong("last_login"), rs.getLong("last_seen"),
                            rs.getInt("shared")));
                }
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- oturumlar

    @Override
    public synchronized void saveSession(UUID uuid, String ip, long expiresAt) throws SQLException {
        if (update("UPDATE kp_sessions SET ip = ?, expires_at = ? WHERE uuid = ?", cut(ip, 45), expiresAt,
                uuid.toString()) == 0) {
            update("INSERT INTO kp_sessions (uuid, ip, expires_at) VALUES (?, ?, ?)", uuid.toString(), cut(ip, 45),
                    expiresAt);
        }
    }

    @Override
    public synchronized boolean hasValidSession(UUID uuid, String ip, long now) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("SELECT ip, expires_at FROM kp_sessions WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                String sip = rs.getString(1);
                long exp = rs.getLong(2);
                return sip != null && sip.equals(ip) && exp > now;
            }
        }
    }

    @Override
    public synchronized void deleteSession(UUID uuid) throws SQLException {
        update("DELETE FROM kp_sessions WHERE uuid = ?", uuid.toString());
    }

    // ---------------------------------------------------------------- komut ban kaydi

    @Override
    public synchronized void logCommandBan(CommandBanRecord r) throws SQLException {
        update("INSERT INTO kp_ban_log (name_lc, reason, issuer, created, expires, active, plugin, removed_by, removed_at) "
                        + "VALUES (?, ?, ?, ?, ?, 1, ?, NULL, 0)",
                cut(Checks.lower(r.getName()), 32), cut(r.getReason(), 255), cut(r.getIssuer(), 64), r.getCreated(),
                r.getExpires(), cut(r.getPlugin(), 64));
    }

    @Override
    public synchronized void logCommandUnban(String name, String by, long now) throws SQLException {
        update("UPDATE kp_ban_log SET active = 0, removed_by = ?, removed_at = ? WHERE name_lc = ? AND active = 1",
                cut(by, 64), now, cut(Checks.lower(name), 32));
    }

    @Override
    public synchronized List<CommandBanRecord> getActiveCommandBans(long now) throws SQLException {
        List<CommandBanRecord> out = new ArrayList<CommandBanRecord>();
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT name_lc, reason, issuer, created, expires, active, plugin FROM kp_ban_log "
                        + "WHERE active = 1 AND (expires < 0 OR expires > ?) ORDER BY created DESC")) {
            ps.setLong(1, now);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapBan(rs));
                }
            }
        }
        return out;
    }

    @Override
    public synchronized List<CommandBanRecord> getCommandBanHistory(Collection<String> names) throws SQLException {
        if (names == null || names.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> lower = new LinkedHashSet<String>();
        for (String n : names) {
            if (n != null) {
                lower.add(Checks.lower(n));
            }
            if (lower.size() >= MAX_IN) {
                break;
            }
        }
        List<String> list = new ArrayList<String>(lower);
        List<CommandBanRecord> out = new ArrayList<CommandBanRecord>();
        if (list.isEmpty()) {
            return out;
        }
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT name_lc, reason, issuer, created, expires, active, plugin FROM kp_ban_log WHERE name_lc IN ("
                        + placeholders(list.size()) + ") ORDER BY created DESC")) {
            for (int i = 0; i < list.size(); i++) {
                ps.setString(i + 1, list.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapBan(rs));
                }
            }
        }
        return out;
    }

    private static CommandBanRecord mapBan(ResultSet rs) throws SQLException {
        return new CommandBanRecord(rs.getString("name_lc"), rs.getString("reason"), rs.getString("issuer"),
                rs.getLong("created"), rs.getLong("expires"), rs.getInt("active") == 1, rs.getString("plugin"));
    }
}
