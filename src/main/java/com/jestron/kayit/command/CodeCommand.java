package com.jestron.kayit.command;

import com.jestron.kayit.KayitPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/** /kod <kod>  |  /kod gonder */
public final class CodeCommand extends KCommand {

    public CodeCommand(KayitPlugin plugin, CommandRegistry registry) {
        super(plugin, registry, "code", null);
    }

    @Override
    protected void run(CommandSender sender, String label, String[] args) {
        Player p = player(sender);
        if (p == null) {
            return;
        }
        String resend = registry.sub("code", "resend");
        if (args.length != 1) {
            usage(sender, label, "resend", resend);
            return;
        }
        if (args[0].equalsIgnoreCase(resend)) {
            plugin.auth().resend(p);
        } else {
            plugin.auth().code(p, args[0]);
        }
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return filter(Collections.singletonList(registry.sub("code", "resend")), args[0]);
        }
        return Collections.emptyList();
    }
}
