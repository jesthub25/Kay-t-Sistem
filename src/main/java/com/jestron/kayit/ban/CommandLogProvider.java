package com.jestron.kayit.ban;

import com.jestron.kayit.KayitPlugin;
import com.jestron.kayit.storage.AltAccount;
import com.jestron.kayit.storage.CommandBanRecord;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Komut izleyicisinin kaydettigi banlar. Dogrudan okunamayan eklentiler (CMI, BanManager vb.) icin
 * yedek kaynaktir; dusuk onceliklidir, gercek kaynak verisi her zaman onu ezer.
 */
public final class CommandLogProvider extends BanProvider {

    private final KayitPlugin plugin;

    public CommandLogProvider(KayitPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "CommandMonitor";
    }

    @Override
    public boolean isAvailable() {
        return plugin.settings().commandMonitorEnabled;
    }

    @Override
    public List<BanEntry> fetchActive() throws Exception {
        List<BanEntry> out = new ArrayList<BanEntry>();
        for (CommandBanRecord r : plugin.database().getActiveCommandBans(System.currentTimeMillis())) {
            out.add(toEntry(r));
        }
        return out;
    }

    @Override
    public List<BanEntry> fetchHistory(Collection<AltAccount> accounts) throws Exception {
        List<String> names = new ArrayList<String>();
        for (AltAccount a : accounts) {
            names.add(a.getName());
        }
        List<BanEntry> out = new ArrayList<BanEntry>();
        for (CommandBanRecord r : plugin.database().getCommandBanHistory(names)) {
            out.add(toEntry(r));
        }
        return out;
    }

    private static BanEntry toEntry(CommandBanRecord r) {
        String source = r.getPlugin() == null || r.getPlugin().isEmpty() ? "Command" : r.getPlugin();
        return new BanEntry(r.getName(), null, r.getReason(), r.getIssuer(), r.getCreated(), r.getExpires(),
                r.isActive(), source, 0);
    }
}
