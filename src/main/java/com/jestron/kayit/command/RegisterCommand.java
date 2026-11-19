package com.jestron.kayit.command;

import com.jestron.kayit.KayitPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /kayitol <sifre> [e-posta] */
public final class RegisterCommand extends KCommand {

    public RegisterCommand(KayitPlugin plugin, CommandRegistry registry) {
        super(plugin, registry, "register", null);
    }

    @Override
    protected void run(CommandSender sender, String label, String[] args) {
        Player p = player(sender);
        if (p == null) {
            return;
        }
        if (args.length < 1 || args.length > 2) {
            usage(sender, label);
            return;
        }
        plugin.auth().register(p, args[0], args.length == 2 ? args[1] : null);
    }
}
