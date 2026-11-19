package com.jestron.kayit.command;

import com.jestron.kayit.KayitPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /sifredegistir <eski> <yeni> */
public final class ChangePasswordCommand extends KCommand {

    public ChangePasswordCommand(KayitPlugin plugin, CommandRegistry registry) {
        super(plugin, registry, "changepassword", null);
    }

    @Override
    protected void run(CommandSender sender, String label, String[] args) {
        Player p = player(sender);
        if (p == null) {
            return;
        }
        if (args.length != 2) {
            usage(sender, label);
            return;
        }
        plugin.auth().changePassword(p, args[0], args[1]);
    }
}
