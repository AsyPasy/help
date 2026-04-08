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
 * Spawns a floating damage number above a hit target.
 *
 * Smooth upward drift and fade-out are handled entirely by the client
 * using the TextDisplay entity's built-in interpolation. The server sets
 * the target transformation once (1 tick after spawn) and the client
 * animates the movement itself — no per-tick teleporting.
 *
 * Requires Minecraft 1.20+ (TextDisplay + interpolation API).
 * Does NOT need any ProtocolLib calls — the Spigot Display API sends
 * the right metadata packets automatically.
 */
public class DamageIndicator {

    // Ticks for the upward drift  (1.5 s)
    private static final int   RISE_DURATION = 30;
    // Tick at which opacity fade begins
    private static final int   FADE_START    = 22;
    // Ticks the fade-out takes
    private static final int   FADE_DURATION = 10;
    // Total entity lifetime in ticks — client is fully faded before this
    private static final int   LIFETIME      = 36;
    // How far up the indicator floats (blocks)
    private static final float RISE_HEIGHT   = 2.2f;
    // Text scale
    private static final float SCALE         = 0.65f;

    private final RPGHealthPlugin plugin;

    public DamageIndicator(RPGHealthPlugin plugin) {
        this.plugin = plugin;
    }

    public void spawnIndicator(Location location, double damage) {
        double ox = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.8;
        double oz = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.8;
        Location spawnLoc = location.clone().add(ox, 1.8, oz);

        TextDisplay display = (TextDisplay) spawnLoc.getWorld()
                .spawnEntity(spawnLoc, EntityType.TEXT_DISPLAY);

        // Base appearance
        display.setText(colorFor(damage) + "\u2694 " + String.format("%.1f", damage));
        display.setBillboard(Display.Billboard.CENTER);
        display.setShadowed(true);
        display.setDefaultBackground(false);
        display.setSeeThrough(false);

        // Set initial scale so it doesn't start huge/tiny
        display.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new AxisAngle4f(0f, 0f, 0f, 1f),
                new Vector3f(SCALE, SCALE, SCALE),
                new AxisAngle4f(0f, 0f, 0f, 1f)
        ));

        // --- Burst 1 (tick +1): tell the client to drift upward smoothly ---
        // We delay 1 tick so the spawn packet has reached the client first.
        // Setting interpolationDelay=0 and interpolationDuration=RISE_DURATION
        // makes the client smoothly move the entity to the target translation
        // over RISE_DURATION ticks — entirely client-side, zero server work.
        new BukkitRunnable() {
            @Override public void run() {
                if (!display.isValid()) return;
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(RISE_DURATION);
                display.setTransformation(new Transformation(
                        new Vector3f(0f, RISE_HEIGHT, 0f),   // drift upward
                        new AxisAngle4f(0f, 0f, 0f, 1f),
                        new Vector3f(SCALE, SCALE, SCALE),
                        new AxisAngle4f(0f, 0f, 0f, 1f)
                ));
            }
        }.runTaskLater(plugin, 1L);

        // --- Burst 2 (tick FADE_START): smooth opacity fade to invisible ---
        // textOpacity: -1 = fully opaque, 0 = fully transparent.
        // The interpolation makes this a smooth fade rather than a hard cut.
        new BukkitRunnable() {
            @Override public void run() {
                if (!display.isValid()) return;
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(FADE_DURATION);
                display.setTextOpacity((byte) 0);
            }
        }.runTaskLater(plugin, FADE_START);

        // --- Cleanup: remove the entity after it is fully invisible ---
        new BukkitRunnable() {
            @Override public void run() {
                if (display.isValid()) display.remove();
            }
        }.runTaskLater(plugin, LIFETIME);
    }

    // -------------------------------------------------------------------------

    private String colorFor(double damage) {
        if (damage >= 15) return ChatColor.DARK_RED + "" + ChatColor.BOLD;
        if (damage >= 8)  return ChatColor.RED      + "" + ChatColor.BOLD;
        if (damage >= 4)  return "" + ChatColor.RED;
        return "" + ChatColor.GRAY;
    }
}
