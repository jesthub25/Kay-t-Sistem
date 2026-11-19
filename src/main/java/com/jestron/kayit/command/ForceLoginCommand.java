package com.jestron.kayit.command;

import com.jestron.kayit.KayitPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/** /engelsizgiris <oyuncu> */
public final class ForceLoginCommand extends KCommand {

    public ForceLoginCommand(KayitPlugin plugin, CommandRegistry registry) {
        super(plugin, registry, "forcelogin", "kayit.forcelogin");
    }

    @Override
    protected void run(CommandSender sender, String label, String[] args) {
        if (args.length != 1) {
            usage(sender, label);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            plugin.lang().send(sender, "general.player-not-found", "player", args[0]);
            return;
        }
        plugin.auth().forceLogin(sender, target);
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        return args.length == 1 ? players(sender, args[0]) : Collections.<String>emptyList();
    }
}
