package com.jestron.kayit.command;

import com.jestron.kayit.KayitPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/** Tum eklenti komutlarinin temeli. Hatalar oyuncuya kibarca, konsola ayrintili yansir. */
public abstract class KCommand extends Command {

    protected final KayitPlugin plugin;
    protected final CommandRegistry registry;
    protected final String key;

    protected KCommand(KayitPlugin plugin, CommandRegistry registry, String key, String permission) {
        super(registry.label(key), "KayitPlugin", "/" + registry.label(key), registry.aliases(key));
        this.plugin = plugin;
        this.registry = registry;
        this.key = key;
        if (permission != null) {
            setPermission(permission);
        }
    }

    @Override
    public final boolean execute(CommandSender sender, String label, String[] args) {
        if (!plugin.isEnabled()) {
            return true;
        }
        if (getPermission() != null && !sender.hasPermission(getPermission())) {
            plugin.lang().send(sender, "general.no-permission");
            return true;
        }
        try {
            run(sender, label.toLowerCase(Locale.ROOT), args);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Error while executing /" + label, t);
            plugin.lang().send(sender, "general.internal-error");
        }
        return true;
    }

    protected abstract void run(CommandSender sender, String label, String[] args);

    @Override
    public final List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (getPermission() != null && !sender.hasPermission(getPermission())) {
            return Collections.emptyList();
        }
        try {
            List<String> out = complete(sender, args);
            return out == null ? Collections.<String>emptyList() : out;
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    /** Varsayilan: oneri yok (sifre komutlarinda oyuncu adi sizdirmamak icin). */
    protected List<String> complete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    protected Player player(CommandSender sender) {
        if (sender instanceof Player) {
            return (Player) sender;
        }
        plugin.lang().send(sender, "general.players-only");
        return null;
    }

    protected void usage(CommandSender sender, String label, Object... extra) {
        Object[] kv = new Object[extra.length + 4];
        kv[0] = "label";
        kv[1] = label;
        kv[2] = "version";
        kv[3] = plugin.getDescription().getVersion();
        System.arraycopy(extra, 0, kv, 4, extra.length);
        plugin.lang().send(sender, "usage." + key, kv);
    }

    protected static List<String> filter(List<String> options, String prefix) {
        List<String> out = new ArrayList<String>();
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(o);
            }
        }
        return out;
    }

    protected List<String> players(CommandSender sender, String prefix) {
        List<String> names = new ArrayList<String>();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (sender instanceof Player && !((Player) sender).canSee(p)) {
                continue;
            }
            names.add(p.getName());
        }
        return filter(names, prefix);
    }
}
