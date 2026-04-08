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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class RPGHealthListener implements Listener {

    private final RPGHealthPlugin plugin;
    private final RPGHealthManager healthManager;
    private final DamageIndicator damageIndicator;

    public RPGHealthListener(RPGHealthPlugin plugin,
                             RPGHealthManager healthManager) {
        this.plugin = plugin;
        this.healthManager = healthManager;
        this.damageIndicator = new DamageIndicator(plugin);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        new BukkitRunnable() {
            @Override public void run() {
                if (!player.isOnline()) return;
                healthManager.onPlayerJoin(player);
            }
        }.runTaskLater(plugin, 5L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        healthManager.onPlayerQuit(event.getPlayer());
    }

    // Damage indicator on mob hit
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

    // Damage indicator on PvP
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPvpDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity() instanceof Player target)) return;
        double damage = event.getFinalDamage();
        if (damage <= 0) return;
        damageIndicator.spawnIndicator(target.getLocation(), damage);
    }

    // Award XP on mob kill
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
    }

    // Respawn with 50% HP
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        new BukkitRunnable() {
            int attempts = 0;
            @Override public void run() {
                attempts++;
                if (attempts > 10) { cancel(); return; }
                if (!player.isOnline()) { cancel(); return; }
                if (player.isDead()) return;
                cancel();
                RPGHealthManager.PlayerData data =
                        healthManager.getData(player.getUniqueId());
                healthManager.applyMaxHp(player, data);
                double halfHp = Math.max(1.0, data.vanillaMaxHp() * 0.5);
                player.setHealth(halfHp);
                player.sendMessage(
                        "\u00a7eYou respawned with \u00a7c50% \u00a7eHP!");
            }
        }.runTaskTimer(plugin, 5L, 5L);
    }
}
