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

        ArmorStand stand = (ArmorStand) loc.getWorld()
                .spawnEntity(loc, EntityType.ARMOR_STAND);
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

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!stand.isValid() || ticks >= 20) {
                    stand.remove();
                    cancel();
                    return;
                }
                stand.teleport(stand.getLocation().add(0, 0.04, 0));
                ticks++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private String getColor(double damage) {
        if (damage >= 15) return "\u00a74\u00a7l";
        if (damage >= 8)  return "\u00a7c\u00a7l";
        if (damage >= 4)  return "\u00a7c";
        return "\u00a77";
    }
}
