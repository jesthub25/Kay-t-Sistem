package com.jestron.kayit.command;

import com.jestron.kayit.KayitPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** /eposta bagla <e-posta> | kaldir | degistir <mevcut> <yeni> */
public final class EmailCommand extends KCommand {

    public EmailCommand(KayitPlugin plugin, CommandRegistry registry) {
        super(plugin, registry, "email", null);
    }

    @Override
    protected void run(CommandSender sender, String label, String[] args) {
        Player p = player(sender);
        if (p == null) {
            return;
        }
        String add = registry.sub("email", "add");
        String remove = registry.sub("email", "remove");
        String change = registry.sub("email", "change");
        if (args.length == 0) {
            usage(sender, label, "add", add, "remove", remove, "change", change);
            return;
        }
        String sub = args[0];
        if (sub.equalsIgnoreCase(add) && args.length == 2) {
            plugin.auth().emailAdd(p, label, args[1]);
        } else if (sub.equalsIgnoreCase(remove) && args.length == 1) {
            plugin.auth().emailRemove(p, label);
        } else if (sub.equalsIgnoreCase(change) && args.length == 3) {
            plugin.auth().emailChange(p, label, args[1], args[2]);
        } else {
            usage(sender, label, "add", add, "remove", remove, "change", change);
        }
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList(registry.sub("email", "add"), registry.sub("email", "remove"),
                    registry.sub("email", "change")), args[0]);
        }
        return Collections.emptyList();
    }
}
