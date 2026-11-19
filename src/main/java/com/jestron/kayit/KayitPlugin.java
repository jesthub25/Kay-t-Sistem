package com.jestron.kayit;

import com.jestron.kayit.alt.AltService;
import com.jestron.kayit.auth.AuthManager;
import com.jestron.kayit.ban.BanService;
import com.jestron.kayit.ban.CommandBanMonitor;
import com.jestron.kayit.command.CommandRegistry;
import com.jestron.kayit.core.Lang;
import com.jestron.kayit.core.Settings;
import com.jestron.kayit.core.Tasks;
import com.jestron.kayit.listener.AuthListener;
import com.jestron.kayit.listener.ProtectionListener;
import com.jestron.kayit.mail.EmailService;
import com.jestron.kayit.storage.Database;
import com.jestron.kayit.storage.MySQLDatabase;
import com.jestron.kayit.storage.SQLiteDatabase;
import com.jestron.kayit.util.Text;
import com.jestron.kayit.webhook.Webhook;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Level;

/**
 * KayitPlugin v2 - AvenorCraft kayit / giris sistemi.
 * Spigot, Paper, Purpur, Leaf ve fork'lari: Minecraft 1.16.x - 1.21.x, Java 8 - 21.
 */
public final class KayitPlugin extends JavaPlugin {

    private Settings settings;
    private Lang lang;
    private Tasks tasks;
    private Database database;
    private EmailService mail;
    private BanService bans;
    private AltService alts;
    private AuthManager auth;
    private CommandRegistry commands;
    private Webhook webhook;

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();
        saveDefaultConfig();
        reloadConfig();

        settings = new Settings();
        settings.load(this);
        lang = new Lang(this);
        lang.load(settings.language);
        tasks = new Tasks(this);
        webhook = new Webhook(this);

        try {
            database = createDatabase();
            database.init();
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Database could not be initialised - plugin will be disabled!", t);
            database = null;
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        commands = new CommandRegistry(this);
        mail = new EmailService(this);
        mail.start();
        bans = new BanService(this);
        alts = new AltService(this);
        auth = new AuthManager(this);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new AuthListener(this), this);
        pm.registerEvents(new ProtectionListener(this), this);
        pm.registerEvents(new CommandBanMonitor(this), this);

        commands.registerAll();
        commands.syncLater();
        auth.start();
        bans.start();
        auth.restoreOnlinePlayers();

        getLogger().info(Text.strip(Text.replace(lang.raw("console.enabled"),
                "version", getDescription().getVersion(),
                "server", Bukkit.getName() + " " + Bukkit.getBukkitVersion(),
                "language", lang.code(),
                "database", database.describe())) + " (" + (System.currentTimeMillis() - start) + " ms)");
    }

    @Override
    public void onDisable() {
        try {
            if (auth != null) {
                auth.saveReloadState();
                auth.shutdown();
            }
        } catch (Throwable t) {
            getLogger().log(Level.WARNING, "Error while saving sessions", t);
        }
        try {
            if (commands != null) {
                commands.unregisterAll();
            }
        } catch (Throwable t) {
            getLogger().log(Level.WARNING, "Error while unregistering commands", t);
        }
        if (bans != null) {
            bans.shutdown();
        }
        if (mail != null) {
            mail.shutdown();
        }
        HandlerList.unregisterAll(this);
        Bukkit.getScheduler().cancelTasks(this);
        if (tasks != null) {
            tasks.shutdown();
        }
        if (database != null) {
            database.close();
        }
    }

    private Database createDatabase() {
        String type = settings.dbType;
        if ("mysql".equals(type) || "mariadb".equals(type)) {
            return new MySQLDatabase("mariadb".equals(type), settings.mysqlHost, settings.mysqlPort,
                    settings.mysqlDatabase, settings.mysqlUser, settings.mysqlPassword, settings.mysqlSsl, getLogger());
        }
        if (!"sqlite".equals(type)) {
            getLogger().warning("Unknown database.type '" + type + "', using sqlite.");
        }
        String fileName = settings.sqliteFile == null || settings.sqliteFile.trim().isEmpty()
                ? "players.db" : settings.sqliteFile.trim();
        return new SQLiteDatabase(new File(getDataFolder(), fileName), getLogger());
    }

    /** /kayitsistemi yenile: config + dil + e-posta + ban kaynaklari + komut adlari. Ana thread. */
    public void reloadAll() {
        reloadConfig();
        settings.load(this);
        lang.load(settings.language);
        mail.shutdown();
        mail.start();
        bans.restart();
        commands.unregisterAll();
        commands.registerAll();
        CommandRegistry.syncNow();
    }

    // ---------------------------------------------------------------- erisimciler

    public Settings settings() {
        return settings;
    }

    public Lang lang() {
        return lang;
    }

    public Tasks tasks() {
        return tasks;
    }

    public Database database() {
        return database;
    }

    public EmailService mail() {
        return mail;
    }

    public BanService bans() {
        return bans;
    }

    public AltService alts() {
        return alts;
    }

    public AuthManager auth() {
        return auth;
    }

    public CommandRegistry commands() {
        return commands;
    }

    public Webhook webhook() {
        return webhook;
    }
}
