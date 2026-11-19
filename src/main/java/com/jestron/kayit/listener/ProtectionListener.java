package com.jestron.kayit.listener;

import com.jestron.kayit.KayitPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.projectiles.ProjectileSource;

/** Giris yapmamis oyunculari kisitlar. NPC'ler (oturumu olmayanlar) etkilenmez. */
public final class ProtectionListener implements Listener {

    private final KayitPlugin plugin;

    public ProtectionListener(KayitPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean restricted(Entity e) {
        return e instanceof Player && plugin.auth().isRestricted((Player) e);
    }

    // ---------------------------------------------------------------- hareket

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        // Sadece bakis acisi degisimi veya dusme -> serbest
        if (from.getX() == to.getX() && from.getZ() == to.getZ() && to.getY() <= from.getY()) {
            return;
        }
        if (!plugin.settings().freeze || !plugin.auth().isRestricted(event.getPlayer())) {
            return;
        }
        Location back = from.clone();
        back.setYaw(to.getYaw());
        back.setPitch(to.getPitch());
        if (to.getY() < from.getY()) {
            back.setY(to.getY()); // havada asili kalip "flying" kick'i yememesi icin dusmeye izin ver
        }
        event.setTo(back);
    }

    // ---------------------------------------------------------------- sohbet / komut

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (plugin.auth().isRestricted(event.getPlayer())) {
            event.setCancelled(true);
            plugin.lang().send(event.getPlayer(), "protection.chat");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        if (!plugin.auth().isRestricted(p)) {
            return;
        }
        String msg = event.getMessage().trim();
        while (msg.startsWith("/")) {
            msg = msg.substring(1);
        }
        int space = msg.indexOf(' ');
        String label = space < 0 ? msg : msg.substring(0, space);
        if (plugin.commands().isAllowedBeforeLogin(label)) {
            return;
        }
        event.setCancelled(true);
        plugin.lang().send(p, "protection.command");
    }

    // ---------------------------------------------------------------- etkilesim

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (plugin.auth().isRestricted(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (plugin.auth().isRestricted(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (plugin.auth().isRestricted(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.auth().isRestricted(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.auth().isRestricted(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (restricted(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (plugin.auth().isRestricted(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (plugin.auth().isRestricted(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        HumanEntity h = event.getPlayer();
        if (restricted(h)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (restricted(event.getWhoClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (restricted(event.getWhoClicked())) {
            event.setCancelled(true);
        }
    }

    // ---------------------------------------------------------------- hasar / mob / aclik

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (plugin.settings().invulnerable && restricted(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Projectile) {
            ProjectileSource src = ((Projectile) damager).getShooter();
            if (src instanceof Entity) {
                damager = (Entity) src;
            }
        }
        if (restricted(damager)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (plugin.settings().invulnerable && restricted(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (plugin.settings().invulnerable && restricted(event.getEntity())) {
            event.setCancelled(true);
        }
    }
}
