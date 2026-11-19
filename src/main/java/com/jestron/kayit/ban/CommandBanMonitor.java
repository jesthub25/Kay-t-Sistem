package com.jestron.kayit.ban;

import com.jestron.kayit.KayitPlugin;
import com.jestron.kayit.core.Settings;
import com.jestron.kayit.storage.CommandBanRecord;
import com.jestron.kayit.util.Checks;
import com.jestron.kayit.util.Durations;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.RemoteServerCommandEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ban / unban komutlarini izler (oyuncu, konsol, RCON).
 *  - Her ban/unban'dan ~3 sn sonra ban listesini yeniler (1 dakikayi beklemeden).
 *  - Banlari dogrudan okunamayan eklentiler (CMI, BanManager vb.) icin kendi kaydini tutar.
 */
public final class CommandBanMonitor implements Listener {

    private final KayitPlugin plugin;

    public CommandBanMonitor(KayitPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        handle(event.getPlayer(), event.getMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        handle(event.getSender(), event.getCommand());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRemoteCommand(RemoteServerCommandEvent event) {
        handle(event.getSender(), event.getCommand());
    }

    private void handle(CommandSender sender, String raw) {
        try {
            process(sender, raw);
        } catch (Throwable t) {
            plugin.getLogger().warning("Command monitor error: " + t.getMessage());
        }
    }

    private void process(CommandSender sender, String raw) {
        Settings s = plugin.settings();
        if (!s.commandMonitorEnabled || raw == null || plugin.commands() == null) {
            return;
        }
        String line = raw.trim();
        while (line.startsWith("/")) {
            line = line.substring(1);
        }
        if (line.isEmpty()) {
            return;
        }
        String[] parts = line.split("\\s+");
        String label = parts[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        String base = colon >= 0 ? label.substring(colon + 1) : label;
        boolean ban = s.banCommands.contains(base);
        boolean unban = s.unbanCommands.contains(base);
        if (!ban && !unban) {
            return;
        }
        // Hangi eklenti olursa olsun listeyi birkac saniye icinde yenile
        plugin.bans().refreshSoon();

        Command cmd = plugin.commands().findCommand(label);
        if (cmd == null || !cmd.testPermissionSilent(sender)) {
            return;
        }
        String owner = plugin.commands().ownerOf(cmd, label);

        List<String> args = new ArrayList<String>();
        for (int i = 1; i < parts.length; i++) {
            String a = parts[i];
            // LiteBans/AdvancedBan bayraklari (-s, -p ...) atlanir
            if (a.length() > 1 && a.charAt(0) == '-' && Character.isLetter(a.charAt(1))) {
                continue;
            }
            if (!a.isEmpty()) {
                args.add(a);
            }
        }
        if (args.isEmpty()) {
            return;
        }
        final String target = args.get(0);
        if (!Checks.isPlayerName(target) || target.indexOf('*') >= 0) {
            return;
        }
        final String issuer = sender instanceof Player ? sender.getName() : "CONSOLE";
        final long now = System.currentTimeMillis();

        if (unban) {
            plugin.tasks().async(new Runnable() {
                @Override
                public void run() {
                    try {
                        plugin.database().logCommandUnban(target, issuer, now);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Could not record unban: " + e.getMessage());
                    }
                }
            });
            return;
        }

        if (plugin.bans().covers(owner)) {
            // Bu eklentinin banlari zaten dogrudan okunuyor
            return;
        }
        long expires = -1L;
        int reasonStart = 1;
        if (args.size() > 1) {
            long d = Durations.parse(args.get(1));
            if (d > 0) {
                expires = now + d;
                reasonStart = 2;
            }
        }
        StringBuilder reason = new StringBuilder();
        for (int i = reasonStart; i < args.size(); i++) {
            if (reason.length() > 0) {
                reason.append(' ');
            }
            reason.append(args.get(i));
        }
        final CommandBanRecord record = new CommandBanRecord(target, reason.length() == 0 ? null : reason.toString(),
                issuer, now, expires, true, owner);
        plugin.tasks().async(new Runnable() {
            @Override
            public void run() {
                try {
                    plugin.database().logCommandBan(record);
                } catch (Exception e) {
                    plugin.getLogger().warning("Could not record ban: " + e.getMessage());
                }
            }
        });
    }
}
