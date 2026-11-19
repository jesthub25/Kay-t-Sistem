package com.jestron.kayit.listener;

import com.jestron.kayit.KayitPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.logging.Level;

/** Oyuncu giris / cikis yasam dongusu. */
public final class AuthListener implements Listener {

    private final KayitPlugin plugin;

    public AuthListener(KayitPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        try {
            plugin.auth().handlePreLogin(event);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Pre-login handling failed for " + event.getName(), t);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        try {
            plugin.auth().handleJoin(event.getPlayer(), false);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Join handling failed for " + event.getPlayer().getName(), t);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        try {
            plugin.auth().handleQuit(event.getPlayer());
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Quit handling failed for " + event.getPlayer().getName(), t);
        }
    }
}
