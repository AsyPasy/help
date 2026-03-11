package com.rpghealth.plugin;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.concurrent.ThreadLocalRandom;

public class DamageIndicator {

    private final RPGHealthPlugin plugin;

    public DamageIndicator(RPGHealthPlugin plugin) {
        this.plugin = plugin;
    }

    public void spawnIndicator(Location location, double damage) {
        Location loc = location.clone().add(
                (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.8,
                1.8,
                (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.8);

        // TextDisplay is a 1.19.4+ entity — no flicker, no armor stand weirdness
        TextDisplay display = (TextDisplay) loc.getWorld()
                .spawnEntity(loc, EntityType.TEXT_DISPLAY);

        display.setText(getColor(damage) + "\u2694 " + String.format("%.1f", damage));
        display.setBillboard(Display.Billboard.CENTER);
        display.setShadowed(true);
        display.setDefaultBackground(false);
        display.setSeeThrough(false);
        display.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(0, 0, 0, 1),
                new Vector3f(0.6f, 0.6f, 0.6f),
                new AxisAngle4f(0, 0, 0, 1)
        ));

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!display.isValid() || ticks >= 20) {
                    display.remove();
                    cancel();
                    return;
                }
                display.teleport(display.getLocation().add(0, 0.04, 0));
                ticks++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private String getColor(double damage) {
        if (damage >= 15) return ChatColor.DARK_RED + "" + ChatColor.BOLD;
        if (damage >= 8)  return ChatColor.RED + "" + ChatColor.BOLD;
        if (damage >= 4)  return "" + ChatColor.RED;
        return "" + ChatColor.GRAY;
    }
}
