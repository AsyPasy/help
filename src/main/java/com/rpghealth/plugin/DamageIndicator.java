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

/**
 * Spawns a floating damage number that:
 *  - drifts upward over exactly 1 second (20 ticks)
 *  - fades out in the final 6 ticks
 *  - is removed from the world at tick 22 (fully invisible by then)
 *
 * All motion is client-side interpolation — zero per-tick teleporting.
 */
public class DamageIndicator {

    // Total upward drift duration — matches the 1-second feel the user wants
    private static final int   RISE_DURATION = 20;
    // Fade begins at tick 14, giving 6 ticks of fade before removal
    private static final int   FADE_START    = 14;
    private static final int   FADE_DURATION = 6;
    // Remove at tick 22 — client is fully transparent before this
    private static final int   LIFETIME      = 22;
    // How far up it floats over those 20 ticks (blocks)
    private static final float RISE_HEIGHT   = 1.6f;
    private static final float SCALE         = 0.65f;

    private final RPGHealthPlugin plugin;

    public DamageIndicator(RPGHealthPlugin plugin) {
        this.plugin = plugin;
    }

    public void spawnIndicator(Location location, double damage) {
        // Small random horizontal offset so stacked hits don't overlap perfectly
        double ox = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.8;
        double oz = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.8;
        Location spawnLoc = location.clone().add(ox, 1.8, oz);

        TextDisplay display = (TextDisplay) spawnLoc.getWorld()
                .spawnEntity(spawnLoc, EntityType.TEXT_DISPLAY);

        display.setText(colorFor(damage) + "\u2694 " + String.format("%.1f", damage));
        display.setBillboard(Display.Billboard.CENTER);
        display.setShadowed(true);
        display.setDefaultBackground(false);
        display.setSeeThrough(false);

        // Initial transform — sets the scale before any interpolation starts
        display.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new AxisAngle4f(0f, 0f, 0f, 1f),
                new Vector3f(SCALE, SCALE, SCALE),
                new AxisAngle4f(0f, 0f, 0f, 1f)
        ));

        // Tick +1: trigger smooth upward drift on the client over RISE_DURATION ticks.
        // Delayed 1 tick so the spawn packet reaches the client first.
        new BukkitRunnable() {
            @Override public void run() {
                if (!display.isValid()) return;
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(RISE_DURATION);
                display.setTransformation(new Transformation(
                        new Vector3f(0f, RISE_HEIGHT, 0f),
                        new AxisAngle4f(0f, 0f, 0f, 1f),
                        new Vector3f(SCALE, SCALE, SCALE),
                        new AxisAngle4f(0f, 0f, 0f, 1f)
                ));
            }
        }.runTaskLater(plugin, 1L);

        // Tick FADE_START: smooth opacity fade to 0 over FADE_DURATION ticks.
        // textOpacity 0 = fully transparent (the client interpolates smoothly).
        new BukkitRunnable() {
            @Override public void run() {
                if (!display.isValid()) return;
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(FADE_DURATION);
                display.setTextOpacity((byte) 0);
            }
        }.runTaskLater(plugin, FADE_START);

        // Tick LIFETIME: entity is invisible by now — safe to remove
        new BukkitRunnable() {
            @Override public void run() {
                if (display.isValid()) display.remove();
            }
        }.runTaskLater(plugin, LIFETIME);
    }

    private String colorFor(double damage) {
        if (damage >= 15) return ChatColor.DARK_RED + "" + ChatColor.BOLD;
        if (damage >= 8)  return ChatColor.RED      + "" + ChatColor.BOLD;
        if (damage >= 4)  return "" + ChatColor.RED;
        return "" + ChatColor.GRAY;
    }
}
