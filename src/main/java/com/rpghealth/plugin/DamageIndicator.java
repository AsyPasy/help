package com.rpghealth.plugin;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.scheduler.BukkitRunnable;

public class DamageIndicator {

    private final RPGHealthPlugin plugin;

    public DamageIndicator(RPGHealthPlugin plugin) {
        this.plugin = plugin;
    }

    public void spawnIndicator(Location location, double damage) {
        Location loc = location.clone().add(
                (Math.random() - 0.5) * 0.8,
                1.8,
                (Math.random() - 0.5) * 0.8);

        // Spawn below ground first so it's never seen before being configured
        Location spawnLoc = loc.clone().subtract(0, 10, 0);

        ArmorStand stand = (ArmorStand) loc.getWorld()
                .spawnEntity(spawnLoc, EntityType.ARMOR_STAND);

        // Configure everything before it's ever visible to players
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setCanPickupItems(false);
        stand.setCustomNameVisible(true);
        stand.setInvulnerable(true);
        stand.setSmall(true);
        stand.setBasePlate(false);
        stand.setArms(false);
        stand.setCustomName(getColor(damage)
                + "\u2694 " + String.format("%.1f", damage));

        // Teleport to real position immediately next tick
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!stand.isValid()) { cancel(); return; }
                if (ticks >= 20) {
                    stand.remove();
                    cancel();
                    return;
                }
                if (ticks == 0) {
                    // First tick — teleport to real position
                    stand.teleport(loc);
                }
                stand.teleport(stand.getLocation().add(0, 0.04, 0));
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private String getColor(double damage) {
        if (damage >= 15) return "\u00a74\u00a7l";
        if (damage >= 8)  return "\u00a7c\u00a7l";
        if (damage >= 4)  return "\u00a7c";
        return "\u00a77";
    }
}
