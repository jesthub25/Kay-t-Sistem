package com.jestron.kayit.ban;

import com.jestron.kayit.KayitPlugin;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Bukkit ban listesi: vanilla /ban, Essentials ve Bukkit API kullanan tum eklentiler.
 * Liste ana thread'de okunur (thread-safe degildir).
 */
public final class BukkitBanListProvider extends BanProvider {

    private final KayitPlugin plugin;

    public BukkitBanListProvider(KayitPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return isEssentials() ? "Essentials/Vanilla" : "Vanilla";
    }

    private boolean isEssentials() {
        Plugin p = Bukkit.getPluginManager().getPlugin("Essentials");
        return p != null && p.isEnabled();
    }

    @Override
    public boolean isAvailable() {
        return plugin.settings().banBukkitList;
    }

    @Override
    public boolean mainThread() {
        return true;
    }

    @Override
    public boolean covers(String pluginName) {
        if (pluginName == null) {
            return false;
        }
        String p = pluginName.toLowerCase(Locale.ROOT);
        return p.equals("minecraft") || p.equals("bukkit") || p.equals("essentials");
    }

    @Override
    @SuppressWarnings("deprecation")
    public List<BanEntry> fetchActive() {
        List<BanEntry> out = new ArrayList<BanEntry>();
        BanList list = Bukkit.getBanList(BanList.Type.NAME);
        String source = name();
        for (Object o : list.getBanEntries()) {
            if (!(o instanceof org.bukkit.BanEntry)) {
                continue;
            }
            org.bukkit.BanEntry b = (org.bukkit.BanEntry) o;
            String target = b.getTarget();
            if (target == null || target.isEmpty()) {
                continue;
            }
            Date created = b.getCreated();
            Date expiration = b.getExpiration();
            long exp = expiration == null ? -1L : expiration.getTime();
            String reason = b.getReason();
            if (reason != null && reason.equalsIgnoreCase("Banned by an operator.")) {
                reason = null;
            }
            out.add(new BanEntry(target, null, reason, b.getSource(),
                    created == null ? -1L : created.getTime(), exp, true, source, 10));
        }
        return out;
    }
}
