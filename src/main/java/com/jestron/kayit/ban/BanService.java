package com.jestron.kayit.ban;

import com.jestron.kayit.KayitPlugin;
import com.jestron.kayit.storage.AltAccount;
import com.jestron.kayit.util.Checks;
import com.jestron.kayit.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tum ban kaynaklarini birlestiren servis.
 *  - Aktif banlar her refresh-seconds (varsayilan 60 sn) okunup bellekte tutulur:
 *    ban atildiginda en gec 1 dakika icinde "banli", ban acildiginda en gec 1 dakika icinde "temiz" gorunur.
 *  - Ban/unban komutlarindan ~3 sn sonra ek bir yenileme yapilir.
 *  - /hesaplar icin gecmis banlar ihtiyac aninda (arka planda) okunur.
 */
public final class BanService {

    private final KayitPlugin plugin;
    private final List<BanProvider> providers = new ArrayList<BanProvider>();
    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    private final AtomicBoolean soonQueued = new AtomicBoolean(false);

    private volatile Map<String, BanEntry> byUuid = Collections.emptyMap();
    private volatile Map<String, BanEntry> byName = Collections.emptyMap();
    private volatile long lastRefresh;
    private volatile int activeCount;
    private BukkitTask timer;

    public BanService(KayitPlugin plugin) {
        this.plugin = plugin;
        providers.add(new BukkitBanListProvider(plugin));
        providers.add(new LiteBansProvider(plugin));
        providers.add(new AdvancedBanProvider(plugin));
        providers.add(new CommandLogProvider(plugin));
    }

    public void start() {
        stopTimer();
        long period = Math.max(15, plugin.settings().banRefreshSeconds) * 20L;
        timer = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                refreshNow();
            }
        }, 40L, period);
    }

    public void restart() {
        for (BanProvider p : providers) {
            p.resetAnnounced();
            p.setLastError(null);
        }
        start();
    }

    private void stopTimer() {
        if (timer != null) {
            try {
                timer.cancel();
            } catch (Exception ignored) {
                // yok say
            }
            timer = null;
        }
    }

    public void shutdown() {
        stopTimer();
        for (BanProvider p : providers) {
            try {
                p.shutdown();
            } catch (Throwable ignored) {
                // yok say
            }
        }
    }

    /** Ban/unban komutundan sonra kisa sure icinde yenile. */
    public void refreshSoon() {
        if (!plugin.isEnabled() || !soonQueued.compareAndSet(false, true)) {
            return;
        }
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                soonQueued.set(false);
                refreshNow();
            }
        }, 60L);
    }

    /** Arka plan thread'inde cagrilir. */
    public void refreshNow() {
        if (!refreshing.compareAndSet(false, true)) {
            return;
        }
        try {
            Map<String, BanEntry> u = new HashMap<String, BanEntry>();
            Map<String, BanEntry> n = new HashMap<String, BanEntry>();
            Set<BanEntry> distinct = new HashSet<BanEntry>();
            for (final BanProvider p : providers) {
                if (!plugin.isEnabled()) {
                    return;
                }
                if (!p.isAvailable()) {
                    p.resetAnnounced();
                    continue;
                }
                List<BanEntry> list;
                try {
                    if (p.mainThread()) {
                        list = plugin.tasks().callSync(new Callable<List<BanEntry>>() {
                            @Override
                            public List<BanEntry> call() throws Exception {
                                return p.fetchActive();
                            }
                        });
                    } else {
                        list = p.fetchActive();
                    }
                    p.setLastError(null);
                    if (p.markAnnounced()) {
                        plugin.getLogger().info(Text.strip(Text.replace(plugin.lang().raw("console.provider-ok"),
                                "provider", p.name())));
                    }
                } catch (Throwable t) {
                    String err = rootMessage(t);
                    if (!err.equals(p.getLastError())) {
                        plugin.getLogger().warning(Text.strip(Text.replace(plugin.lang().raw("console.provider-error"),
                                "provider", p.name(), "error", err)));
                    }
                    p.setLastError(err);
                    continue;
                }
                if (list == null) {
                    continue;
                }
                for (BanEntry e : list) {
                    if (e == null || !e.isActiveNow()) {
                        continue;
                    }
                    boolean used = false;
                    if (e.getUuidKey() != null) {
                        used |= put(u, e.getUuidKey(), e);
                    }
                    if (e.getName() != null) {
                        used |= put(n, Checks.lower(e.getName()), e);
                    }
                    if (used) {
                        distinct.add(e);
                    }
                }
            }
            byUuid = u;
            byName = n;
            activeCount = distinct.size();
            lastRefresh = System.currentTimeMillis();
        } finally {
            refreshing.set(false);
        }
    }

    private static boolean put(Map<String, BanEntry> map, String key, BanEntry e) {
        BanEntry old = map.get(key);
        if (BanEntry.better(e, old)) {
            map.put(key, e);
            return true;
        }
        return false;
    }

    /** Hesabin su an aktif bir bani var mi? (bellekteki son okuma) */
    public BanEntry findActive(UUID uuid, String name) {
        String key = Checks.uuidKey(uuid);
        BanEntry e = key == null ? null : byUuid.get(key);
        if (e == null && name != null) {
            BanEntry byN = byName.get(Checks.lower(name));
            // Isim eslesmesi: kayit baska bir UUID'ye aitse (isim degisikligi) yok say
            if (byN != null && (byN.getUuidKey() == null || byN.getUuidKey().equals(key))) {
                e = byN;
            }
        }
        return e != null && e.isActiveNow() ? e : null;
    }

    /**
     * Verilen hesaplarin en son ban kaydini (gecmis dahil) bulur. ANA THREAD'DEN CAGRILMAMALI.
     */
    public Map<UUID, BanEntry> lookupHistory(Collection<AltAccount> accounts) {
        Map<UUID, BanEntry> out = new HashMap<UUID, BanEntry>();
        if (accounts == null || accounts.isEmpty()) {
            return out;
        }
        Map<String, AltAccount> keys = new HashMap<String, AltAccount>();
        Map<String, AltAccount> names = new HashMap<String, AltAccount>();
        for (AltAccount a : accounts) {
            keys.put(Checks.uuidKey(a.getUuid()), a);
            if (a.getName() != null) {
                names.put(Checks.lower(a.getName()), a);
            }
        }
        for (BanProvider p : providers) {
            if (p.mainThread() || !p.isAvailable()) {
                continue;
            }
            List<BanEntry> list;
            try {
                list = p.fetchHistory(accounts);
            } catch (Throwable t) {
                p.setLastError(rootMessage(t));
                continue;
            }
            if (list == null) {
                continue;
            }
            for (BanEntry e : list) {
                AltAccount acc = null;
                if (e.getUuidKey() != null) {
                    acc = keys.get(e.getUuidKey());
                }
                if (acc == null && e.getUuidKey() == null && e.getName() != null) {
                    acc = names.get(Checks.lower(e.getName()));
                }
                if (acc == null) {
                    continue;
                }
                BanEntry old = out.get(acc.getUuid());
                if (old == null || e.getCreated() > old.getCreated()
                        || (e.getCreated() == old.getCreated() && e.getPriority() > old.getPriority())) {
                    out.put(acc.getUuid(), e);
                }
            }
        }
        return out;
    }

    /** Verilen eklentinin banlari dogrudan okunabiliyor mu? */
    public boolean covers(String pluginName) {
        for (BanProvider p : providers) {
            if (p.isAvailable() && p.covers(pluginName)) {
                return true;
            }
        }
        return false;
    }

    public List<BanProvider> providers() {
        return Collections.unmodifiableList(providers);
    }

    public long getLastRefresh() {
        return lastRefresh;
    }

    public int getActiveCount() {
        return activeCount;
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String m = cur.getMessage();
        if (m == null || m.trim().isEmpty()) {
            m = cur.getClass().getSimpleName();
        }
        m = m.replace('\n', ' ').trim();
        return m.length() > 180 ? m.substring(0, 180) + "..." : m;
    }
}
