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

    // Base HP at level 1
    private static final double BASE_HP = 20.0;
    // HP gained per level
    private static final double HP_PER_LEVEL = 2.0;
    // Base XP needed to level up from level 1
    private static final int BASE_XP = 100;
    // XP scaling per level (each level needs more XP)
    private static final double XP_SCALE = 1.15;
    // Regen amount per regen tick
    private static final double REGEN_AMOUNT = 0.5;
    // Regen interval in ticks (60 ticks = 3 seconds)
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
        // Clamp current HP to max
        double clamped = Math.min(data.currentHp, data.maxHp);
        data.currentHp = clamped;
        player.setHealth(clamped);
    }

    public void updateActionBar(Player player, PlayerData data) {
        // Build heart display
        int totalHearts = (int) (data.maxHp / 2);
        int fullHearts = (int) (data.currentHp / 2);
        int halfHeart = (data.currentHp % 2 >= 1) ? 1 : 0;
        int emptyHearts = totalHearts - fullHearts - halfHeart;

        StringBuilder bar = new StringBuilder("§c");
        for (int i = 0; i < fullHearts; i++) bar.append("❤");
        if (halfHeart == 1) bar.append("§4♥");
        bar.append("§8");
        for (int i = 0; i < emptyHearts; i++) bar.append("❤");

        bar.append(" §f").append(String.format("%.1f", data.currentHp))
           .append("§7/§f").append(String.format("%.0f", data.maxHp));
        bar.append("  §eLv.").append(data.level);

        player.sendActionBar(bar.toString());
    }

    public void damagePlayer(Player player, double amount) {
        PlayerData data = getData(player.getUniqueId());
        data.currentHp = Math.max(0, data.currentHp - amount);
        applyHealthToPlayer(player, data);
        updateActionBar(player, data);
        if (data.currentHp <= 0) {
            player.setHealth(0); // triggers death
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
            data.currentHp = data.maxHp; // full heal on level up
            applyHealthToPlayer(player, data);
            player.sendMessage("§a§l✦ LEVEL UP! §eYou are now level §f" + data.level + "§e!");
            player.sendMessage("§eMax HP increased to §f" + String.format("%.0f", data.maxHp) + "§e!");
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
