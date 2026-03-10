package com.rpghealth.plugin;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RPGHealthManager {

    private final RPGHealthPlugin plugin;
    private final Map<UUID, PlayerData> playerData = new HashMap<>();
    private final File dataFolder;

    // Vanilla max HP stays at 20 always — we just display it as 100
    private static final double VANILLA_BASE_HP = 20.0;
    private static final double DISPLAY_BASE_HP = 100.0;
    private static final double DISPLAY_HP_PER_LEVEL = 2.0;
    private static final int BASE_XP = 100;
    private static final double XP_SCALE = 1.15;
    private static final double REGEN_AMOUNT = 0.1; // in vanilla HP (0.1 = 0.5 display HP)
    private static final long REGEN_INTERVAL = 60L;

    public static class PlayerData {
        public int level = 1;
        public int xp = 0;

        // Display values only — actual HP stays vanilla scaled
        public double displayMaxHp() {
            return DISPLAY_BASE_HP + (level - 1) * DISPLAY_HP_PER_LEVEL;
        }

        // Convert vanilla HP to display HP
        public double toDisplay(double vanillaHp) {
            return (vanillaHp / VANILLA_BASE_HP) * displayMaxHp();
        }

        // Convert display HP to vanilla HP
        public double toVanilla(double displayHp) {
            return (displayHp / displayMaxHp()) * VANILLA_BASE_HP;
        }

        // Max vanilla HP scales slightly with level — +0.4 vanilla per level
        public double vanillaMaxHp() {
            return VANILLA_BASE_HP + (level - 1) * 0.4;
        }
    }

    public RPGHealthManager(RPGHealthPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!dataFolder.exists()) dataFolder.mkdirs();
        startRegenTask();
    }

    private void startRegenTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    PlayerData data = getData(player.getUniqueId());
                    double maxVanilla = data.vanillaMaxHp();
                    if (player.getHealth() < maxVanilla) {
                        double newHp = Math.min(maxVanilla, player.getHealth() + REGEN_AMOUNT);
                        player.setHealth(newHp);
                        updateDisplay(player, data);
                    }
                }
            }
        }.runTaskTimer(plugin, REGEN_INTERVAL, REGEN_INTERVAL);
    }

    public PlayerData getData(UUID uuid) {
        return playerData.computeIfAbsent(uuid, k -> loadData(k));
    }

    public void onPlayerJoin(Player player) {
        PlayerData data = getData(player.getUniqueId());
        applyMaxHp(player, data);
        hideVanillaHealth(player);
        updateDisplay(player, data);
    }

    public void onPlayerQuit(Player player) {
        saveData(player.getUniqueId());
    }

    public void applyMaxHp(Player player, PlayerData data) {
        AttributeInstance maxHpAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHpAttr != null) {
            maxHpAttr.setBaseValue(data.vanillaMaxHp());
        }
        // Clamp current HP
        double maxVanilla = data.vanillaMaxHp();
        if (player.getHealth() > maxVanilla) {
            player.setHealth(maxVanilla);
        }
    }

    public void hideVanillaHealth(Player player) {
        // Hide vanilla hearts by setting health scale to 0 display
        player.setHealthScaled(true);
        player.setHealthScale(0.0); // 0 = no hearts shown
    }

    public void updateDisplay(Player player, PlayerData data) {
        double displayCurrent = data.toDisplay(player.getHealth());
        double displayMax = data.displayMaxHp();
        String display = String.format("§c%.0f§7/§c%.0f §c❤  §eLv.%d",
                displayCurrent, displayMax, data.level);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent(display));
    }

    public void addXp(Player player, int amount) {
        PlayerData data = getData(player.getUniqueId());
        data.xp += amount;
        int xpNeeded = getXpForNextLevel(data.level);
        while (data.xp >= xpNeeded) {
            data.xp -= xpNeeded;
            data.level++;
            applyMaxHp(player, data);
            // Full heal on level up
            player.setHealth(data.vanillaMaxHp());
            player.sendMessage("§a§l✦ LEVEL UP! §eYou are now level §f" + data.level + "§e!");
            player.sendMessage("§eMax HP is now §f" + String.format("%.0f", data.displayMaxHp()) + "§e!");
            player.getWorld().playSound(player.getLocation(),
                    org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            xpNeeded = getXpForNextLevel(data.level);
        }
        updateDisplay(player, data);
    }

    public void setLevel(Player player, int level) {
        PlayerData data = getData(player.getUniqueId());
        data.level = Math.max(1, level);
        data.xp = 0;
        applyMaxHp(player, data);
        player.setHealth(data.vanillaMaxHp());
        updateDisplay(player, data);
        player.sendMessage("§aYour level has been set to §f" + data.level
                + " §awith §f" + String.format("%.0f", data.displayMaxHp()) + " §amax HP!");
    }

    public int getXpForNextLevel(int level) {
        return (int) (BASE_XP * Math.pow(XP_SCALE, level - 1));
    }

    private PlayerData loadData(UUID uuid) {
        File file = new File(dataFolder, uuid.toString() + ".yml");
        PlayerData data = new PlayerData();
        if (file.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            data.level = config.getInt("level", 1);
            data.xp = config.getInt("xp", 0);
        }
        return data;
    }

    private void saveData(UUID uuid) {
        PlayerData data = playerData.get(uuid);
        if (data == null) return;
        File file = new File(dataFolder, uuid.toString() + ".yml");
        FileConfiguration config = new YamlConfiguration();
        config.set("level", data.level);
        config.set("xp", data.xp);
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save data for " + uuid);
        }
    }

    public void saveAll() {
        playerData.keySet().forEach(this::saveData);
    }
}
