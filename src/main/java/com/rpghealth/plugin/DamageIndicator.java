package com.rpghealth.plugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spawns floating damage numbers above hit targets.
 *
 * Animation is fully client-side using TextDisplay entity transformation
 * interpolation. The server sends ONE metadata packet after spawn — the
 * client handles all the smooth upward drift and fade-out with zero
 * per-tick server work.
 *
 * Requires ProtocolLib 5.x and Minecraft 1.20+.
 */
public class DamageIndicator {

    // How many ticks the upward drift takes  (1.5 seconds)
    private static final int RISE_DURATION  = 30;
    // Tick at which the fade-out begins
    private static final int FADE_START     = 22;
    // How many ticks the fade takes
    private static final int FADE_DURATION  = 10;
    // Total entity lifetime in ticks
    private static final int LIFETIME       = 34;
    // How far up the indicator travels (blocks, visual offset only)
    private static final float RISE_HEIGHT  = 2.2f;
    // Scale of the text
    private static final float SCALE        = 0.65f;
    // Broadcast radius squared (32 blocks)
    private static final double RADIUS_SQ   = 1024.0;

    private final RPGHealthPlugin plugin;
    private final ProtocolManager pm;

    // Serializers fetched once and cached
    private WrappedDataWatcher.Serializer intSerializer;
    private WrappedDataWatcher.Serializer byteSerializer;
    private WrappedDataWatcher.Serializer vec3Serializer;
    private WrappedDataWatcher.Serializer quatSerializer;
    private boolean serializersReady = false;

    public DamageIndicator(RPGHealthPlugin plugin) {
        this.plugin = plugin;
        this.pm     = ProtocolLibrary.getProtocolManager();
        initSerializers();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void spawnIndicator(Location location, double damage) {
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

        if (serializersReady) {
            smoothAnimate(display, spawnLoc);
        } else {
            legacyAnimate(display);
        }
    }

    // -------------------------------------------------------------------------
    // Smooth animation path (ProtocolLib)
    // -------------------------------------------------------------------------

    /**
     * Sends two metadata bursts:
     *   1. On tick+1 — sets transformation interpolation → client drifts up smoothly.
     *   2. On FADE_START — sets text_opacity to 0 with short interpolation → client fades.
     * The entity is removed server-side after LIFETIME ticks; the client already
     * considers it invisible by then.
     */
    private void smoothAnimate(TextDisplay display, Location spawnLoc) {

        // Burst 1: drift upward — sent one tick after spawn so the client
        // has had time to spawn the entity before we update its metadata.
        new BukkitRunnable() {
            @Override public void run() {
                if (!display.isValid()) return;
                sendTransformMeta(display.getEntityId(),
                        /* delay   */ 0,
                        /* duration */ RISE_DURATION,
                        new Vector3f(0f, RISE_HEIGHT, 0f),     // target translation
                        new Vector3f(SCALE, SCALE, SCALE),     // scale
                        spawnLoc);
            }
        }.runTaskLater(plugin, 1L);

        // Burst 2: fade to transparent
        new BukkitRunnable() {
            @Override public void run() {
                if (!display.isValid()) return;
                sendOpacityMeta(display.getEntityId(),
                        /* opacity  */ (byte) 0,               // fully transparent
                        /* delay    */ 0,
                        /* duration */ FADE_DURATION,
                        spawnLoc);
            }
        }.runTaskLater(plugin, FADE_START);

        // Remove entity — client is fully faded before this runs
        new BukkitRunnable() {
            @Override public void run() { display.remove(); }
        }.runTaskLater(plugin, LIFETIME);
    }

    // -------------------------------------------------------------------------
    // Metadata packet helpers
    // -------------------------------------------------------------------------

    /**
     * Metadata indices for Display entities in 1.20.1:
     *   8  interpolation_delay    (int)
     *   9  interpolation_duration (int)
     *   10 translation            (Vector3f)
     *   11 scale                  (Vector3f)   — part of Transformation
     *   12 left_rotation          (Quaternionf)
     *   13 right_rotation         (Quaternionf)
     */
    private void sendTransformMeta(int entityId, int delay, int duration,
                                    Vector3f translation, Vector3f scale,
                                    Location near) {
        try {
            List<WrappedDataValue> values = new ArrayList<>();
            values.add(new WrappedDataValue(8,  intSerializer,  delay));
            values.add(new WrappedDataValue(9,  intSerializer,  duration));
            values.add(new WrappedDataValue(10, vec3Serializer, translation));
            values.add(new WrappedDataValue(11, vec3Serializer, scale));
            // Identity quaternions — no rotation
            values.add(new WrappedDataValue(12, quatSerializer, new Quaternionf(0f, 0f, 0f, 1f)));
            values.add(new WrappedDataValue(13, quatSerializer, new Quaternionf(0f, 0f, 0f, 1f)));
            broadcast(buildMetaPacket(entityId, values), near);
        } catch (Exception e) {
            plugin.getLogger().warning("sendTransformMeta error: " + e.getMessage());
        }
    }

    /**
     * Metadata index for TextDisplay in 1.20.1:
     *   25 text_opacity (byte) — -1 = fully opaque, 0 = fully transparent.
     *
     * We also set interpolation so the fade is smooth, not a hard cut.
     */
    private void sendOpacityMeta(int entityId, byte opacity,
                                  int delay, int duration, Location near) {
        try {
            List<WrappedDataValue> values = new ArrayList<>();
            values.add(new WrappedDataValue(8,  intSerializer,  delay));
            values.add(new WrappedDataValue(9,  intSerializer,  duration));
            values.add(new WrappedDataValue(25, byteSerializer, opacity));
            broadcast(buildMetaPacket(entityId, values), near);
        } catch (Exception e) {
            plugin.getLogger().warning("sendOpacityMeta error: " + e.getMessage());
        }
    }

    private PacketContainer buildMetaPacket(int entityId, List<WrappedDataValue> values) {
        PacketContainer pkt = pm.createPacket(PacketType.Play.Server.ENTITY_METADATA);
        pkt.getIntegers().write(0, entityId);
        pkt.getDataValueCollectionModifier().write(0, values);
        return pkt;
    }

    /** Sends a packet to all players within RADIUS_SQ of the indicator. */
    private void broadcast(PacketContainer pkt, Location near) {
        near.getWorld().getPlayers().stream()
                .filter(p -> p.getLocation().distanceSquared(near) <= RADIUS_SQ)
                .forEach(p -> {
                    try { pm.sendServerPacket(p, pkt); }
                    catch (Exception ignored) {}
                });
    }

    // -------------------------------------------------------------------------
    // Fallback: old tick-by-tick teleport (used if serializer init fails)
    // -------------------------------------------------------------------------

    private void legacyAnimate(TextDisplay display) {
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!display.isValid() || ticks >= 20) {
                    display.remove(); cancel(); return;
                }
                display.teleport(display.getLocation().add(0, 0.04, 0));
                ticks++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // -------------------------------------------------------------------------
    // Initialisation
    // -------------------------------------------------------------------------

    private void initSerializers() {
        try {
            intSerializer  = WrappedDataWatcher.Registry.get(Integer.class);
            byteSerializer = WrappedDataWatcher.Registry.get(Byte.class);
            // Vector3f and Quaternionf are joml types used directly by Minecraft 1.20
            vec3Serializer = WrappedDataWatcher.Registry.get(Vector3f.class,  false);
            quatSerializer = WrappedDataWatcher.Registry.get(Quaternionf.class, false);
            serializersReady = true;
            plugin.getLogger().info("DamageIndicator: ProtocolLib serializers ready.");
        } catch (Exception e) {
            plugin.getLogger().warning(
                "DamageIndicator: ProtocolLib serializer init failed, using fallback. "
                + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Colour helpers
    // -------------------------------------------------------------------------

    private String colorFor(double damage) {
        if (damage >= 15) return ChatColor.DARK_RED + "" + ChatColor.BOLD;
        if (damage >= 8)  return ChatColor.RED      + "" + ChatColor.BOLD;
        if (damage >= 4)  return "" + ChatColor.RED;
        return "" + ChatColor.GRAY;
    }
}
