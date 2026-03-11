package com.rpghealth.plugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class DamageIndicator {

    private final RPGHealthPlugin plugin;
    private final ProtocolManager protocolManager;
    private final AtomicInteger entityIdCounter = new AtomicInteger(100000);

    public DamageIndicator(RPGHealthPlugin plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
    }

    public void spawnIndicator(Location location, double damage) {
        Location loc = location.clone().add(
                (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.8,
                1.8,
                (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.8);

        int entityId = entityIdCounter.incrementAndGet();
        UUID entityUUID = UUID.randomUUID();
        String displayName = getColor(damage) + "\u2694 " + String.format("%.1f", damage);

        // Send spawn packet to all nearby players
        List<Player> nearbyPlayers = getNearbyPlayers(loc, 32);
        spawnFakeArmorStand(entityId, entityUUID, loc, displayName, nearbyPlayers);

        // Float upward then remove
        new BukkitRunnable() {
            int ticks = 0;
            double currentY = loc.getY();

            @Override
            public void run() {
                ticks++;
                if (ticks >= 20) {
                    // Send destroy packet
                    destroyFakeEntity(entityId, nearbyPlayers);
                    cancel();
                    return;
                }
                currentY += 0.04;
                Location newLoc = loc.clone();
                newLoc.setY(currentY);
                teleportFakeEntity(entityId, newLoc, nearbyPlayers);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void spawnFakeArmorStand(int entityId, UUID uuid, Location loc,
                                      String name, List<Player> players) {
        // Spawn living entity packet
        PacketContainer spawnPacket = new PacketContainer(PacketType.Play.Server.SPAWN_ENTITY);
        spawnPacket.getIntegers().write(0, entityId);
        spawnPacket.getUUIDs().write(0, uuid);
        spawnPacket.getEntityTypeModifier().write(0,
                org.bukkit.entity.EntityType.ARMOR_STAND);
        spawnPacket.getDoubles().write(0, loc.getX());
        spawnPacket.getDoubles().write(1, loc.getY());
        spawnPacket.getDoubles().write(2, loc.getZ());

        // Metadata packet — sets invisible, custom name, small, no base plate
        PacketContainer metaPacket = new PacketContainer(
                PacketType.Play.Server.ENTITY_METADATA);
        metaPacket.getIntegers().write(0, entityId);

        List<WrappedDataValue> dataValues = new ArrayList<>();

        // Index 0 — entity flags: invisible (0x20)
        dataValues.add(new WrappedDataValue(0,
                WrappedDataWatcher.Registry.get(Byte.class),
                (byte) 0x20));

        // Index 2 — custom name
        dataValues.add(new WrappedDataValue(2,
                WrappedDataWatcher.Registry.getChatComponentSerializer(true),
                Optional.of(WrappedDataWatcher.Registry
                        .getChatComponentSerializer(true)
                        .getType().cast(
                                com.comphenix.protocol.wrappers.WrappedChatComponent
                                        .fromText(name).getHandle()))));

        // Index 3 — custom name visible: true
        dataValues.add(new WrappedDataValue(3,
                WrappedDataWatcher.Registry.get(Boolean.class), true));

        // Index 15 — armor stand flags: small (0x01) + no base plate (0x08)
        dataValues.add(new WrappedDataValue(15,
                WrappedDataWatcher.Registry.get(Byte.class),
                (byte) (0x01 | 0x08)));

        metaPacket.getDataValueCollectionModifier().write(0, dataValues);

        for (Player player : players) {
            try {
                protocolManager.sendServerPacket(player, spawnPacket);
                protocolManager.sendServerPacket(player, metaPacket);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send indicator packet: "
                        + e.getMessage());
            }
        }
    }

    private void teleportFakeEntity(int entityId, Location loc,
                                     List<Player> players) {
        PacketContainer teleportPacket = new PacketContainer(
                PacketType.Play.Server.ENTITY_TELEPORT);
        teleportPacket.getIntegers().write(0, entityId);
        teleportPacket.getDoubles().write(0, loc.getX());
        teleportPacket.getDoubles().write(1, loc.getY());
        teleportPacket.getDoubles().write(2, loc.getZ());
        teleportPacket.getBooleans().write(0, false);

        for (Player player : players) {
            try {
                protocolManager.sendServerPacket(player, teleportPacket);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send teleport packet: "
                        + e.getMessage());
            }
        }
    }

    private void destroyFakeEntity(int entityId, List<Player> players) {
        PacketContainer destroyPacket = new PacketContainer(
                PacketType.Play.Server.ENTITY_DESTROY);
        destroyPacket.getIntLists().write(0, List.of(entityId));

        for (Player player : players) {
            try {
                protocolManager.sendServerPacket(player, destroyPacket);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send destroy packet: "
                        + e.getMessage());
            }
        }
    }

    private List<Player> getNearbyPlayers(Location loc, double radius) {
        List<Player> result = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getWorld().equals(loc.getWorld())
                    && player.getLocation().distance(loc) <= radius) {
                result.add(player);
            }
        }
        return result;
    }

    private String getColor(double damage) {
        if (damage >= 15) return "\u00a74\u00a7l";
        if (damage >= 8)  return "\u00a7c\u00a7l";
        if (damage >= 4)  return "\u00a7c";
        return "\u00a77";
    }
}
