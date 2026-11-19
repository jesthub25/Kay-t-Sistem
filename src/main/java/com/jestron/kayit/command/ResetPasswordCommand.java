package com.jestron.kayit.command;

import com.jestron.kayit.KayitPlugin;
import com.jestron.kayit.util.Checks;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

/** /sifresifirla <oyuncu>  (cevrimdisi oyuncular da olur) */
public final class ResetPasswordCommand extends KCommand {

    public ResetPasswordCommand(KayitPlugin plugin, CommandRegistry registry) {
        super(plugin, registry, "resetpassword", "kayit.resetpassword");
    }

    @Override
    protected void run(CommandSender sender, String label, String[] args) {
        if (args.length != 1 || !Checks.isPlayerName(args[0])) {
            usage(sender, label);
            return;
        }
        plugin.auth().resetPassword(sender, args[0]);
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        return args.length == 1 ? players(sender, args[0]) : Collections.<String>emptyList();
    }
}
