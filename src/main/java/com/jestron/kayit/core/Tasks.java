package com.jestron.kayit.core;

import org.bukkit.Bukkit;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Thread yonetimi. Veritabani, BCrypt ve ag islemleri ana thread'i asla dondurmez.
 */
public final class Tasks {

    private final Plugin plugin;
    private final ExecutorService workers;

    public Tasks(Plugin plugin) {
        this.plugin = plugin;
        final AtomicInteger n = new AtomicInteger();
        this.workers = Executors.newFixedThreadPool(3, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "KayitPlugin-Worker-" + n.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });
    }

    /** Arka plan thread'inde calistirir. */
    public void async(final Runnable task) {
        try {
            workers.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        task.run();
                    } catch (Throwable t) {
                        plugin.getLogger().log(Level.SEVERE, "Async task failed", t);
                    }
                }
            });
        } catch (RejectedExecutionException ignored) {
            // Eklenti kapanirken gelen gorevler yok sayilir
        }
    }

    /** Ana thread'de calistirir (zaten ana thread'deysek hemen). */
    public void sync(final Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            safe(task);
            return;
        }
        if (!plugin.isEnabled()) {
            return;
        }
        try {
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    safe(task);
                }
            });
        } catch (IllegalPluginAccessException ignored) {
            // kapaniyor
        }
    }

    /** Bir sonraki tick'te ana thread'de calistirir. */
    public void later(final Runnable task, long ticks) {
        if (!plugin.isEnabled()) {
            return;
        }
        try {
            Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                @Override
                public void run() {
                    safe(task);
                }
            }, Math.max(1L, ticks));
        } catch (IllegalPluginAccessException ignored) {
            // kapaniyor
        }
    }

    /** Async thread'den ana thread'e gidip sonucu bekler (en fazla 10 sn). */
    public <T> T callSync(Callable<T> callable) throws Exception {
        if (Bukkit.isPrimaryThread()) {
            return callable.call();
        }
        if (!plugin.isEnabled()) {
            throw new IllegalStateException("plugin disabled");
        }
        Future<T> f = Bukkit.getScheduler().callSyncMethod(plugin, callable);
        return f.get(10, TimeUnit.SECONDS);
    }

    private void safe(Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Task failed", t);
        }
    }

    public void shutdown() {
        workers.shutdown();
        try {
            if (!workers.awaitTermination(5, TimeUnit.SECONDS)) {
                workers.shutdownNow();
            }
        } catch (InterruptedException e) {
            workers.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
