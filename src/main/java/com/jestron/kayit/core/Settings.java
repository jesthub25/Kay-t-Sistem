package com.jestron.kayit.core;

import com.jestron.kayit.util.Checks;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/**
 * config.yml degerlerinin tipli, dogrulanmis kopyasi.
 * Eksik anahtarlar JAR icindeki varsayilan config.yml'den gelir (JavaPlugin varsayilanlari).
 */
public final class Settings {

    public enum MailSecurity { STARTTLS, SSL, NONE }

    // Genel
    public String language;
    public String serverName;
    public DateTimeFormatter dateFormat;
    public ZoneId zone;

    // Veritabani
    public String dbType;
    public String sqliteFile;
    public String mysqlHost;
    public int mysqlPort;
    public String mysqlDatabase;
    public String mysqlUser;
    public String mysqlPassword;
    public boolean mysqlSsl;

    // Sifre
    public int passwordMin;
    public int passwordMax;
    public int bcryptCost;
    public boolean disallowUsername;
    public Set<String> blockedPasswords;

    // E-posta
    public boolean emailEnabled;
    public String smtpHost;
    public int smtpPort;
    public MailSecurity mailSecurity;
    public String smtpUser;
    public String smtpPassword;
    public String fromAddress;
    public String fromName;
    public String heloName;
    public int mailTimeoutSeconds;
    public boolean mailHtml;
    public int codeLength;
    public int codeExpirySeconds;
    public int resendCooldownSeconds;
    public int maxCodeAttempts;
    public boolean requireCodeOnLogin;
    public int maxAccountsPerEmail;
    public boolean mailTestOnStartup;

    // IP limitleri
    public int maxRegistrationsPerIp;
    public int maxSessionsPerIp;

    // Guvenlik
    public int loginTimeoutSeconds;
    public int maxLoginAttempts;
    public boolean preventOnlineKick;
    public int reminderIntervalSeconds;
    public boolean titles;

    // Oturum
    public boolean sessionEnabled;
    public int sessionTimeoutMinutes;

    // Koruma
    public boolean freeze;
    public boolean invulnerable;
    public boolean blindness;
    public Set<String> extraAllowedCommands;

    // Alt hesaplar
    public boolean altsScanAllIps;
    public boolean altsShowIps;
    public int altsMaxResults;

    // Ban kaynaklari
    public int banRefreshSeconds;
    public boolean banBukkitList;
    public boolean liteBansEnabled;
    public SqlSource liteBansSql;
    public String liteBansPrefix;
    public boolean advancedBanEnabled;
    public SqlSource advancedBanSql;
    public boolean commandMonitorEnabled;
    public Set<String> banCommands;
    public Set<String> unbanCommands;

    // Uyarilar
    public boolean alertBannedAlt;
    public String alertSound;

    // Webhook
    public boolean webhookEnabled;
    public String webhookUrl;
    public String webhookUsername;
    public boolean webhookRegister;
    public boolean webhookLogin;
    public boolean webhookBannedAlt;
    public boolean webhookAdmin;

    /** Harici SQL baglanti bilgisi (LiteBans / AdvancedBan proxy kurulumlari icin). */
    public static final class SqlSource {
        public final boolean enabled;
        public final String host;
        public final int port;
        public final String database;
        public final String username;
        public final String password;

        SqlSource(boolean enabled, String host, int port, String database, String username, String password) {
            this.enabled = enabled;
            this.host = host;
            this.port = port;
            this.database = database;
            this.username = username;
            this.password = password;
        }
    }

    public void load(JavaPlugin plugin) {
        FileConfiguration c = plugin.getConfig();
        Logger log = plugin.getLogger();

        language = str(c, "language", "tr").toLowerCase(Locale.ROOT);
        serverName = str(c, "server-name", "AvenorCraft");

        String pattern = str(c, "date-format", "dd/MM/yyyy HH.mm");
        try {
            dateFormat = DateTimeFormatter.ofPattern(pattern);
        } catch (IllegalArgumentException e) {
            log.warning("Invalid date-format '" + pattern + "', using dd/MM/yyyy HH:mm");
            dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        }
        String tz = str(c, "timezone", "");
        zone = ZoneId.systemDefault();
        if (!tz.isEmpty()) {
            try {
                zone = ZoneId.of(tz);
            } catch (Exception e) {
                log.warning("Invalid timezone '" + tz + "', using server timezone.");
            }
        }

        dbType = str(c, "database.type", "sqlite").toLowerCase(Locale.ROOT);
        sqliteFile = str(c, "database.sqlite-file", "players.db");
        mysqlHost = str(c, "database.mysql.host", "localhost");
        mysqlPort = c.getInt("database.mysql.port", 3306);
        mysqlDatabase = str(c, "database.mysql.database", "kayitplugin");
        mysqlUser = str(c, "database.mysql.username", "root");
        mysqlPassword = str(c, "database.mysql.password", "");
        mysqlSsl = c.getBoolean("database.mysql.use-ssl", false);

        passwordMin = Math.max(1, c.getInt("password.min-length", 5));
        passwordMax = Math.max(passwordMin, c.getInt("password.max-length", 16));
        bcryptCost = clamp(c.getInt("password.bcrypt-cost", 10), 4, 14);
        disallowUsername = c.getBoolean("password.disallow-username", true);
        blockedPasswords = lowerSet(c.getStringList("password.blocked"));

        emailEnabled = c.getBoolean("email.enabled", true);
        smtpHost = str(c, "email.smtp-host", "smtp.gmail.com");
        smtpPort = c.getInt("email.smtp-port", 587);
        String sec = str(c, "email.security", "").toUpperCase(Locale.ROOT);
        if (sec.isEmpty()) {
            // Eski config uyumlulugu (use-ssl / use-tls)
            if (c.getBoolean("email.use-ssl", false)) {
                sec = "SSL";
            } else if (c.getBoolean("email.use-tls", true)) {
                sec = "STARTTLS";
            } else {
                sec = "NONE";
            }
        }
        try {
            mailSecurity = MailSecurity.valueOf(sec);
        } catch (IllegalArgumentException e) {
            log.warning("Invalid email.security '" + sec + "', using " + (smtpPort == 465 ? "SSL" : "STARTTLS"));
            mailSecurity = smtpPort == 465 ? MailSecurity.SSL : MailSecurity.STARTTLS;
        }
        smtpUser = str(c, "email.username", "");
        smtpPassword = c.getString("email.password", "");
        if (smtpPassword == null) {
            smtpPassword = "";
        }
        if (smtpHost.toLowerCase(Locale.ROOT).contains("gmail")) {
            // Gmail uygulama sifreleri bosluklu gosterilir; bosluklar sifrenin parcasi degildir.
            smtpPassword = smtpPassword.replace(" ", "");
        }
        fromAddress = str(c, "email.from-address", "");
        if (fromAddress.isEmpty()) {
            fromAddress = smtpUser;
        }
        fromName = str(c, "email.from-name", serverName);
        heloName = str(c, "email.helo-name", "localhost");
        mailTimeoutSeconds = clamp(c.getInt("email.timeout-seconds", c.getInt("email.connection-timeout-ms", 20000) / 1000), 5, 120);
        mailHtml = !"text".equalsIgnoreCase(str(c, "email.format", "html"));
        codeLength = clamp(c.getInt("email.code-length", 6), 4, 10);
        codeExpirySeconds = clamp(c.getInt("email.code-expiry-seconds", 120), 30, 3600);
        resendCooldownSeconds = clamp(c.getInt("email.resend-cooldown-seconds", 30), 0, 600);
        maxCodeAttempts = clamp(c.getInt("email.max-code-attempts", 3), 1, 20);
        requireCodeOnLogin = c.getBoolean("email.require-code-on-login", true);
        maxAccountsPerEmail = Math.max(0, c.getInt("email.max-accounts-per-email", 3));
        mailTestOnStartup = c.getBoolean("email.test-on-startup", true);

        maxRegistrationsPerIp = Math.max(0, c.getInt("ip-limits.max-registrations-per-ip", 3));
        maxSessionsPerIp = Math.max(0, c.getInt("ip-limits.max-sessions-per-ip", 2));

        loginTimeoutSeconds = Math.max(0, c.getInt("security.login-timeout-seconds", 90));
        maxLoginAttempts = Math.max(0, c.getInt("security.max-login-attempts", 5));
        preventOnlineKick = c.getBoolean("security.prevent-online-kick", true);
        reminderIntervalSeconds = clamp(c.getInt("security.reminder-interval-seconds",
                c.getInt("game.login-reminder-seconds", 10)), 3, 600);
        titles = c.getBoolean("security.titles", true);

        sessionEnabled = c.getBoolean("session.enabled", false);
        sessionTimeoutMinutes = clamp(c.getInt("session.timeout-minutes", 10), 1, 1440);

        freeze = c.getBoolean("protection.freeze", c.getBoolean("game.freeze-player", true));
        invulnerable = c.getBoolean("protection.invulnerable", true);
        blindness = c.getBoolean("protection.blindness", false);
        extraAllowedCommands = new HashSet<String>();
        for (String s : c.getStringList("protection.allowed-commands")) {
            String cmd = s.trim().toLowerCase(Locale.ROOT);
            while (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            if (!cmd.isEmpty()) {
                extraAllowedCommands.add(cmd);
            }
        }

        altsScanAllIps = c.getBoolean("alts.scan-all-ips", true);
        altsShowIps = c.getBoolean("alts.show-ips", false);
        altsMaxResults = clamp(c.getInt("alts.max-results", 30), 1, 200);

        banRefreshSeconds = clamp(c.getInt("ban-sources.refresh-seconds",
                c.getInt("ban-cache.refresh-seconds", 60)), 15, 3600);
        banBukkitList = c.getBoolean("ban-sources.bukkit-banlist", true);
        liteBansEnabled = c.getBoolean("ban-sources.litebans.enabled", true);
        liteBansSql = sql(c, "ban-sources.litebans.sql", "litebans");
        liteBansPrefix = str(c, "ban-sources.litebans.sql.table-prefix", "litebans_");
        advancedBanEnabled = c.getBoolean("ban-sources.advancedban.enabled", true);
        advancedBanSql = sql(c, "ban-sources.advancedban.sql", "advancedban");
        commandMonitorEnabled = c.getBoolean("ban-sources.command-monitor.enabled", true);
        banCommands = lowerSet(listOr(c, "ban-sources.command-monitor.ban-commands",
                "ban", "tempban", "ipban", "banip", "tempipban", "tban"));
        unbanCommands = lowerSet(listOr(c, "ban-sources.command-monitor.unban-commands",
                "unban", "pardon", "unbanip", "pardonip", "pardon-ip"));

        alertBannedAlt = c.getBoolean("alerts.banned-alt", true);
        alertSound = str(c, "alerts.sound", "block.note_block.pling");

        webhookEnabled = c.getBoolean("webhook.enabled", false);
        webhookUrl = str(c, "webhook.url", "");
        webhookUsername = str(c, "webhook.username", "KayitSistemi");
        webhookRegister = c.getBoolean("webhook.events.register", true);
        webhookLogin = c.getBoolean("webhook.events.login", false);
        webhookBannedAlt = c.getBoolean("webhook.events.banned-alt", true);
        webhookAdmin = c.getBoolean("webhook.events.admin-actions", true);
    }

    public boolean isMailConfigured() {
        return !smtpUser.isEmpty() && !smtpPassword.isEmpty() && !smtpHost.isEmpty()
                && !"sunucu@gmail.com".equalsIgnoreCase(smtpUser)
                && !"app-password-here".equalsIgnoreCase(smtpPassword)
                && Checks.isEmail(fromAddress);
    }

    private static SqlSource sql(FileConfiguration c, String path, String defDb) {
        ConfigurationSection s = c.getConfigurationSection(path);
        if (s == null) {
            return new SqlSource(false, "localhost", 3306, defDb, "root", "");
        }
        return new SqlSource(s.getBoolean("enabled", false), s.getString("host", "localhost"),
                s.getInt("port", 3306), s.getString("database", defDb), s.getString("username", "root"),
                s.getString("password", ""));
    }

    private static List<String> listOr(FileConfiguration c, String path, String... def) {
        List<String> l = c.getStringList(path);
        if (l == null || l.isEmpty()) {
            List<String> d = new ArrayList<String>();
            Collections.addAll(d, def);
            return d;
        }
        return l;
    }

    private static Set<String> lowerSet(List<String> list) {
        Set<String> set = new HashSet<String>();
        if (list != null) {
            for (String s : list) {
                if (s != null && !s.trim().isEmpty()) {
                    set.add(s.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return set;
    }

    private static String str(FileConfiguration c, String path, String def) {
        String v = c.getString(path, def);
        return v == null ? def : v.trim();
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
