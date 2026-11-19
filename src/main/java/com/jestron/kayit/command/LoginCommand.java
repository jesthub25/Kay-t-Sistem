package com.jestron.kayit.command;

import com.jestron.kayit.KayitPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /giris <sifre> */
public final class LoginCommand extends KCommand {

    public LoginCommand(KayitPlugin plugin, CommandRegistry registry) {
        super(plugin, registry, "login", null);
    }

    @Override
    protected void run(CommandSender sender, String label, String[] args) {
        Player p = player(sender);
        if (p == null) {
            return;
        }
        if (args.length != 1) {
            usage(sender, label);
            return;
        }
        plugin.auth().login(p, args[0]);
    }
}
