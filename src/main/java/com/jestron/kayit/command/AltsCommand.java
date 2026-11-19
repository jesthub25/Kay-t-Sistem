package com.jestron.kayit.command;

import com.jestron.kayit.KayitPlugin;
import com.jestron.kayit.util.Checks;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

/**
 * /hesaplar <oyuncu>
 * Yetkisi (kayit.alts) olmayan oyuncular komutu goremez ve tab'da da cikmaz.
 * Sonuc YALNIZCA komutu yazana gonderilir.
 */
public final class AltsCommand extends KCommand {

    public AltsCommand(KayitPlugin plugin, CommandRegistry registry) {
        super(plugin, registry, "alts", "kayit.alts");
    }

    @Override
    protected void run(CommandSender sender, String label, String[] args) {
        if (args.length != 1 || !Checks.isPlayerName(args[0])) {
            usage(sender, label);
            return;
        }
        plugin.alts().showAlts(sender, args[0]);
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        return args.length == 1 ? players(sender, args[0]) : Collections.<String>emptyList();
    }
}
