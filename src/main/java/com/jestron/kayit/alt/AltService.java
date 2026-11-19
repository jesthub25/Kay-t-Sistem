package com.jestron.kayit.alt;

import com.jestron.kayit.KayitPlugin;
import com.jestron.kayit.auth.AuthSession;
import com.jestron.kayit.ban.BanEntry;
import com.jestron.kayit.core.Lang;
import com.jestron.kayit.core.Settings;
import com.jestron.kayit.storage.AltAccount;
import com.jestron.kayit.storage.PlayerAccount;
import com.jestron.kayit.util.Text;
import com.jestron.kayit.webhook.Webhook;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Alt hesap tespiti:
 *  - /hesaplar <oyuncu>: ayni IP(ler)i kullanan tum hesaplar, aktiflik ve ban bilgisi (sadece komutu yazana)
 *  - Giris / kayit aninda banli alt hesap varsa kayit.alert yetkililerine uyari
 */
public final class AltService {

    public static final String ALERT_PERMISSION = "kayit.alert";

    private final KayitPlugin plugin;

    public AltService(KayitPlugin plugin) {
        this.plugin = plugin;
    }

    private Lang lang() {
        return plugin.lang();
    }

    // ================================================================ uyarilar

    /** Oyuncunun banli alt hesabi var mi kontrol eder; oturum basina bir kez uyarir. Ana thread. */
    public void checkPlayer(final Player p) {
        final Settings s = plugin.settings();
        if (!s.alertBannedAlt) {
            return;
        }
        final AuthSession session = plugin.auth().getSession(p.getUniqueId());
        if (session == null || session.isAlerted()) {
            return;
        }
        final UUID self = p.getUniqueId();
        final String name = p.getName();
        final String ip = session.getIp();
        plugin.tasks().async(new Runnable() {
            @Override
            public void run() {
                try {
                    Set<String> ips = new LinkedHashSet<String>();
                    if (ip != null && !"unknown".equals(ip)) {
                        ips.add(ip);
                    }
                    if (s.altsScanAllIps) {
                        ips.addAll(plugin.database().getIps(self, 50));
                    }
                    if (ips.isEmpty()) {
                        return;
                    }
                    List<AltAccount> accounts = plugin.database().getAccountsByIps(ips, 200);
                    final List<String> banned = new ArrayList<String>();
                    for (AltAccount a : accounts) {
                        if (a.getUuid().equals(self)) {
                            continue;
                        }
                        if (plugin.bans().findActive(a.getUuid(), a.getName()) != null) {
                            banned.add(a.getName());
                        }
                    }
                    if (banned.isEmpty()) {
                        return;
                    }
                    plugin.tasks().sync(new Runnable() {
                        @Override
                        public void run() {
                            if (!p.isOnline() || session.isAlerted()) {
                                return;
                            }
                            session.setAlerted(true);
                            broadcastAlert(name, banned);
                        }
                    });
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Alt check failed for " + name, e);
                }
            }
        });
    }

    private void broadcastAlert(String player, List<String> banned) {
        String alts = Text.join(banned, ", ");
        String altsLabel = plugin.commands().label("alts");
        String msg = lang().get("alerts.banned-alt", "player", player, "alts", alts);
        String hover = lang().get("alerts.hover", "player", player, "alts", altsLabel);
        String command = "/" + altsLabel + " " + player;
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (!staff.hasPermission(ALERT_PERMISSION)) {
                continue;
            }
            sendClickable(staff, msg, hover, command, false);
            playSound(staff);
        }
        plugin.getLogger().warning(Text.strip(Text.replace(lang().raw("alerts.console"), "player", player,
                "alts", alts)));
        if (plugin.settings().webhookBannedAlt) {
            plugin.webhook().send("Banli alt hesap / Banned alt", Text.strip(msg), Webhook.COLOR_ALERT);
        }
    }

    private void playSound(Player p) {
        String sound = plugin.settings().alertSound;
        if (sound == null || sound.isEmpty()) {
            return;
        }
        try {
            p.playSound(p.getLocation(), sound, 1.0f, 1.0f);
        } catch (Throwable ignored) {
            // gecersiz ses adi: yok say
        }
    }

    // ================================================================ /hesaplar

    public void showAlts(final CommandSender sender, final String targetName) {
        final Settings s = plugin.settings();
        Player online = Bukkit.getPlayerExact(targetName);
        final UUID onlineUuid = online == null ? null : online.getUniqueId();
        AuthSession onlineSession = onlineUuid == null ? null : plugin.auth().getSession(onlineUuid);
        final String onlineIp = onlineSession == null ? null : onlineSession.getIp();

        lang().send(sender, "alts.searching", "player", targetName);
        plugin.tasks().async(new Runnable() {
            @Override
            public void run() {
                try {
                    PlayerAccount target = onlineUuid != null ? plugin.database().getAccount(onlineUuid) : null;
                    if (target == null) {
                        target = plugin.database().getAccountByName(targetName);
                    }
                    if (target == null) {
                        message(sender, "alts.not-found", "player", targetName);
                        return;
                    }
                    Set<String> ips = new LinkedHashSet<String>();
                    if (onlineIp != null && !"unknown".equals(onlineIp)) {
                        ips.add(onlineIp);
                    }
                    if (s.altsScanAllIps) {
                        ips.addAll(plugin.database().getIps(target.getUuid(), 50));
                    } else if (ips.isEmpty() && target.getLastIp() != null) {
                        ips.add(target.getLastIp());
                    }
                    if (ips.isEmpty()) {
                        message(sender, "alts.no-ip");
                        return;
                    }
                    List<AltAccount> accounts = plugin.database().getAccountsByIps(ips, s.altsMaxResults + 1);
                    boolean truncated = accounts.size() > s.altsMaxResults;
                    if (truncated) {
                        accounts = new ArrayList<AltAccount>(accounts.subList(0, s.altsMaxResults));
                    }
                    Map<UUID, BanEntry> history = plugin.bans().lookupHistory(accounts);
                    final Result result = new Result(target, new ArrayList<String>(ips), accounts, history, truncated);
                    plugin.tasks().sync(new Runnable() {
                        @Override
                        public void run() {
                            render(sender, result);
                        }
                    });
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Alt lookup failed for " + targetName, e);
                    message(sender, "general.internal-error");
                }
            }
        });
    }

    private static final class Result {
        final PlayerAccount target;
        final List<String> ips;
        final List<AltAccount> accounts;
        final Map<UUID, BanEntry> history;
        final boolean truncated;

        Result(PlayerAccount target, List<String> ips, List<AltAccount> accounts, Map<UUID, BanEntry> history,
               boolean truncated) {
            this.target = target;
            this.ips = ips;
            this.accounts = accounts;
            this.history = history;
            this.truncated = truncated;
        }
    }

    /** Ana thread: sonucu YALNIZCA komutu yazana gonderir. */
    private void render(CommandSender sender, Result r) {
        if (sender instanceof Player && !((Player) sender).isOnline()) {
            return;
        }
        Settings s = plugin.settings();
        String altsLabel = plugin.commands().label("alts");

        // Sorgulanan hesap en ustte
        List<AltAccount> ordered = new ArrayList<AltAccount>(r.accounts);
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).getUuid().equals(r.target.getUuid())) {
                AltAccount self = ordered.remove(i);
                ordered.add(0, self);
                break;
            }
        }

        int banned = 0;
        for (AltAccount a : ordered) {
            if (plugin.bans().findActive(a.getUuid(), a.getName()) != null) {
                banned++;
            }
        }

        sender.sendMessage(lang().get("alts.header", "player", r.target.getName()));
        sender.sendMessage(lang().get("alts.summary", "total", ordered.size(), "banned", banned, "ips", r.ips.size()));
        if (s.altsShowIps) {
            sender.sendMessage(lang().get("alts.ips-line", "ips", Text.join(r.ips, ", ")));
        }
        if (!lang().isEmpty("alts.columns")) {
            sender.sendMessage(lang().get("alts.columns"));
        }

        for (AltAccount a : ordered) {
            BanEntry active = plugin.bans().findActive(a.getUuid(), a.getName());
            BanEntry shown = active;
            if (shown == null) {
                BanEntry past = r.history.get(a.getUuid());
                if (past != null && !past.isActiveNow()) {
                    shown = past;
                }
            }

            String self = a.getUuid().equals(r.target.getUuid()) ? lang().raw("alts.self-tag") : "";
            if (!a.isRegistered()) {
                self = self + lang().raw("alts.unregistered-tag");
            }

            String ban;
            String staff;
            if (shown == null) {
                ban = lang().raw("alts.ban-none");
                staff = lang().raw("alts.staff-none");
            } else {
                String reason = shown.getReason() == null ? lang().raw("alts.no-reason") : Text.strip(shown.getReason());
                String date = lang().date(s, shown.getCreated());
                if (active != null) {
                    String expiry = shown.isPermanent()
                            ? lang().raw("alts.ban-expiry-permanent")
                            : Text.replace(lang().raw("alts.ban-expiry-until"), "until", lang().date(s, shown.getExpires()));
                    ban = Text.replace(lang().raw("alts.ban-active"), "reason", reason, "date", date, "expiry", expiry);
                } else {
                    ban = Text.replace(lang().raw("alts.ban-past"), "reason", reason, "date", date);
                }
                staff = shown.getIssuer() == null ? lang().raw("alts.staff-none") : Text.strip(shown.getIssuer());
            }

            String line = lang().get("alts.entry", "name", a.getName(), "self", self, "activity", activity(a),
                    "ban", ban, "staff", staff, "shared", a.getSharedIps());
            String hover = lang().format(Text.join(lang().rawList("alts.hover"), "\n"),
                    "name", a.getName(),
                    "uuid", a.getUuid(),
                    "registered", lang().date(s, a.getRegisteredAt()),
                    "last_login", lang().date(s, a.getLastLogin()),
                    "shared", a.getSharedIps(),
                    "source", shown == null ? "-" : shown.getSource(),
                    "alts", altsLabel);
            sendClickable(sender, line, hover, "/" + altsLabel + " " + a.getName(), true);
        }
        if (r.truncated) {
            sender.sendMessage(lang().get("alts.truncated", "max", s.altsMaxResults));
        }
        if (!lang().isEmpty("alts.footer")) {
            sender.sendMessage(lang().get("alts.footer"));
        }
    }

    private String activity(AltAccount a) {
        Player p = Bukkit.getPlayer(a.getUuid());
        if (p != null && p.isOnline()) {
            return plugin.auth().isLoggedIn(a.getUuid())
                    ? lang().raw("alts.activity-online")
                    : lang().raw("alts.activity-unauthed");
        }
        long last = a.getLastActive();
        if (last <= 0) {
            return lang().raw("alts.activity-never");
        }
        return Text.replace(lang().raw("alts.activity-ago"), "time",
                lang().duration(System.currentTimeMillis() - last));
    }

    // ================================================================ yardimcilar

    /** Oyunculara tiklanabilir/ustune gelince bilgi veren mesaj; konsola duz metin. */
    @SuppressWarnings("deprecation")
    private void sendClickable(CommandSender to, String text, String hover, String command, boolean suggest) {
        if (!(to instanceof Player)) {
            to.sendMessage(text);
            return;
        }
        try {
            BaseComponent[] parts = TextComponent.fromLegacyText(text);
            BaseComponent[] hoverParts = TextComponent.fromLegacyText(hover);
            HoverEvent hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverParts);
            ClickEvent click = new ClickEvent(suggest ? ClickEvent.Action.SUGGEST_COMMAND : ClickEvent.Action.RUN_COMMAND,
                    command);
            for (BaseComponent c : parts) {
                c.setHoverEvent(hoverEvent);
                c.setClickEvent(click);
            }
            ((Player) to).spigot().sendMessage(parts);
        } catch (Throwable t) {
            to.sendMessage(text);
        }
    }

    private void message(final CommandSender to, final String key, final Object... kv) {
        plugin.tasks().sync(new Runnable() {
            @Override
            public void run() {
                lang().send(to, key, kv);
            }
        });
    }
}
