package com.rpghealth.plugin;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class RPGHealthListener implements Listener {

    private final RPGHealthPlugin plugin;
    private final RPGHealthManager healthManager;
    private final DamageIndicator damageIndicator;

    public RPGHealthListener(RPGHealthPlugin plugin, RPGHealthManager healthManager) {
        this.plugin = plugin;
        this.healthManager = healthManager;
        this.damageIndicator = new DamageIndicator(plugin);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        new BukkitRunnable() {
            @Override public void run() {
                healthManager.onPlayerJoin(player);
            }
        }.runTaskLater(plugin, 5L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        healthManager.onPlayerQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        new BukkitRunnable() {
            @Override public void run() {
                if (!player.isOnline()) return;
                RPGHealthManager.PlayerData data = healthManager.getData(player.getUniqueId());
                healthManager.updateDisplay(player, data);
            }
        }.runTaskLater(plugin, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        Entity entity = event.getEntity();
        if (entity instanceof Player) return;
        if (!(entity instanceof LivingEntity)) return;
        double damage = event.getFinalDamage();
        if (damage <= 0) return;
        damageIndicator.spawnIndicator(entity.getLocation(), damage);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPvpDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity() instanceof Player target)) return;
        double damage = event.getFinalDamage();
        if (damage <= 0) return;
        damageIndicator.spawnIndicator(target.getLocation(), damage);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) return;
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        AttributeInstance maxHp = event.getEntity()
                .getAttribute(Attribute.GENERIC_MAX_HEALTH);
        int xpReward = 5;
        if (maxHp != null) {
            xpReward = (int) (maxHp.getValue() * 0.25);
        }
        healthManager.addXp(killer, xpReward);
        // XP shown on action bar — no chat message
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        RPGHealthManager.PlayerData data = healthManager.getData(player.getUniqueId());
        new BukkitRunnable() {
            @Override public void run() {
                player.setHealth(data.vanillaMaxHp() * 0.5);
                healthManager.updateDisplay(player, data);
            }
        }.runTaskLater(plugin, 5L);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        new BukkitRunnable() {
            @Override public void run() {
                RPGHealthManager.PlayerData data = healthManager.getData(player.getUniqueId());
                healthManager.applyMaxHp(player, data);
                healthManager.hideVanillaHealth(player);
                healthManager.updateDisplay(player, data);
            }
        }.runTaskLater(plugin, 5L);
    }

    @EventHandler
    public void onPlayerJoinStartActionBar(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        new BukkitRunnable() {
            @Override public void run() {
                if (!player.isOnline()) { cancel(); return; }
                RPGHealthManager.PlayerData data = healthManager.getData(player.getUniqueId());
                healthManager.updateDisplay(player, data);
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }
}
