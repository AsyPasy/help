package com.rpghealth.plugin;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class DamageIndicator {

    private final RPGHealthPlugin plugin;

    public DamageIndicator(RPGHealthPlugin plugin) {
        this.plugin = plugin;
    }

    public void spawnIndicator(Location location, double damage) {
        Location spawnLoc = location.clone().add(
                (Math.random() - 0.5) * 0.8,
                1.8,
                (Math.random() - 0.5) * 0.8);

        // Format the damage text
        String text = formatDamage(damage);

        // Spawn invisible armor stand with custom name as the indicator
        ArmorStand stand = (ArmorStand) location.getWorld().spawnEntity(spawnLoc, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setCanPickupItems(false);
        stand.setCustomNameVisible(true);
        stand.setCustomName(text);
        stand.setSmall(true);
        stand.setMarker(true);
        stand.setInvulnerable(true);

        // Float upward and fade out after 1.5 seconds
        new BukkitRunnable() {
            int ticks = 0;
            final int MAX_TICKS = 20;

            @Override
            public void run() {
                if (!stand.isValid() || ticks >= MAX_TICKS) {
                    stand.remove();
                    cancel();
                    return;
                }
                // Float upward slightly each tick
                Location loc = stand.getLocation();
                loc.add(0, 0.04, 0);
                stand.teleport(loc);
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private String formatDamage(double damage) {
        // Color based on damage amount
        String color;
        if (damage >= 15) {
            color = "§4§l"; // dark red bold for big hits
        } else if (damage >= 8) {
            color = "§c§l"; // red bold for medium hits
        } else if (damage >= 4) {
            color = "§c";   // red for normal hits
        } else {
            color = "§7";   // gray for small hits
        }

        // Format number — show 1 decimal only if not a whole number
        String dmgText;
        if (damage == Math.floor(damage)) {
            dmgText = String.valueOf((int) damage);
        } else {
            dmgText = String.format("%.1f", damage);
        }

        return color + "⚔ " + dmgText;
    }
}
