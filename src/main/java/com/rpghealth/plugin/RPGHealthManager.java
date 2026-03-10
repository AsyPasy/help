package com.rpghealth.plugin;

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
    private File dataFolder;

    // Start at 100 HP
    private static final double BASE_HP = 100.0;
    // HP gained per level
    private static final double HP_PER_LEVEL = 2.0;
    // Base XP needed to level up
    private static final int BASE_XP = 100;
    // XP scaling per level
    private static final double XP_SCALE = 1.15;
    // Regen per tick
    private static final double REGEN_AMOUNT = 0.5;
    // Regen interval in ticks (3 seconds)
    private static final long REGEN_INTERVAL = 60L;

    public static class PlayerData {
        public int level = 1;
        public double maxHp = BASE_HP;
        public double currentHp = BASE_HP;
        public int xp = 0;
    }

    public RPGHealthManager(RPGHealthPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!dataFolder.exists()) dataFolder.mkdirs();
        startRegenTask();
    }

    private void startRegenTask() {
        new BukkitRunnable() {
            @Override public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    PlayerData data = getData(player.getUniqueId());
                    if (data.currentHp < data.maxHp) {
                        data.currentHp = Math.min(data.maxHp, data.currentHp + REGEN_AMOUNT);
                        applyHealthToPlayer(player, data);
                        updateActionBar(player, data);
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
        applyHealthToPlayer(player, data);
        updateActionBar(player, data);
    }

    public void onPlayerQuit(Player player) {
        saveData(player.getUniqueId());
    }

    public void applyHealthToPlayer(Player player, PlayerData data) {
        AttributeInstance maxHpAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHpAttr != null) {
            maxHpAttr.setBaseValue(data.maxHp);
        }
        double clamped = Math.min(data.currentHp, data.maxHp);
        data.currentHp = Math.max(0, clamped);
        player.setHealth(data.currentHp);
    }

    public void updateActionBar(Player player, PlayerData data) {
        String display = String.format("§c%.0f§7/§c%.0f §c❤  §eLv.%d",
                data.currentHp, data.maxHp, data.level);
        player.sendActionBar(display);
    }

    public void damagePlayer(Player player, double amount) {
        PlayerData data = getData(player.getUniqueId());
        data.currentHp = Math.max(0, data.currentHp - amount);
        applyHealthToPlayer(player, data);
        updateActionBar(player, data);
        if (data.currentHp <= 0) {
            player.setHealth(0);
        }
    }

    public void healPlayer(Player player, double amount) {
        PlayerData data = getData(player.getUniqueId());
        data.currentHp = Math.min(data.maxHp, data.currentHp + amount);
        applyHealthToPlayer(player, data);
        updateActionBar(player, data);
    }

    public void addXp(Player player, int amount) {
        PlayerData data = getData(player.getUniqueId());
        data.xp += amount;
        int xpNeeded = getXpForNextLevel(data.level);
        while (data.xp >= xpNeeded) {
            data.xp -= xpNeeded;
            data.level++;
            data.maxHp = BASE_HP + (data.level - 1) * HP_PER_LEVEL;
            data.currentHp = data.maxHp;
            applyHealthToPlayer(player, data);
            player.sendMessage("§a§l✦ LEVEL UP! §eYou are now level §f" + data.level + "§e!");
            player.sendMessage("§eMax HP is now §f" + String.format("%.0f", data.maxHp) + "§e!");
            player.getWorld().playSound(player.getLocation(),
                    org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            xpNeeded = getXpForNextLevel(data.level);
        }
        updateActionBar(player, data);
    }

    public void setLevel(Player player, int level) {
        PlayerData data = getData(player.getUniqueId());
        data.level = Math.max(1, level);
        data.maxHp = BASE_HP + (data.level - 1) * HP_PER_LEVEL;
        data.currentHp = data.maxHp;
        data.xp = 0;
        applyHealthToPlayer(player, data);
        updateActionBar(player, data);
        player.sendMessage("§aYour level has been set to §f" + data.level
                + " §awith §f" + String.format("%.0f", data.maxHp) + " §amax HP!");
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
            data.maxHp = BASE_HP + (data.level - 1) * HP_PER_LEVEL;
            data.currentHp = config.getDouble("currentHp", data.maxHp);
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
        config.set("currentHp", data.currentHp);
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
