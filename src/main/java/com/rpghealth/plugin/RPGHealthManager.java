package com.rpghealth.plugin;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
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
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private final File dataFolder;

    private final double VANILLA_BASE_HP;
    private final double DISPLAY_BASE_HP;
    private final double DISPLAY_HP_PER_LEVEL;
    private final double VANILLA_HP_PER_LEVEL;
    private final double REGEN_AMOUNT;
    private final long REGEN_INTERVAL;
    private final int BASE_XP;
    private final double XP_SCALE;

    public class PlayerData {
        public int level = 1;
        public int xp = 0;

        public double displayMaxHp() {
            return DISPLAY_BASE_HP + (level - 1) * DISPLAY_HP_PER_LEVEL;
        }

        public double toDisplay(double vanillaHp) {
            return (vanillaHp / vanillaMaxHp()) * displayMaxHp();
        }

        public double toVanilla(double displayHp) {
            return (displayHp / displayMaxHp()) * vanillaMaxHp();
        }

        public double vanillaMaxHp() {
            return VANILLA_BASE_HP + (level - 1) * VANILLA_HP_PER_LEVEL;
        }
    }

    public RPGHealthManager(RPGHealthPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!dataFolder.exists()) dataFolder.mkdirs();

        FileConfiguration cfg = plugin.getConfig();
        VANILLA_BASE_HP      = cfg.getDouble("settings.base-vanilla-hp", 20.0);
        DISPLAY_BASE_HP      = cfg.getDouble("settings.base-display-hp", 100.0);
        DISPLAY_HP_PER_LEVEL = cfg.getDouble("settings.display-hp-per-level", 5.0);
        VANILLA_HP_PER_LEVEL = cfg.getDouble("settings.vanilla-hp-per-level", 0.5);
        REGEN_AMOUNT         = cfg.getDouble("settings.regen-amount", 0.1);
        REGEN_INTERVAL       = cfg.getLong("settings.regen-interval", 60L);
        BASE_XP              = cfg.getInt("settings.base-xp", 200);
        XP_SCALE             = cfg.getDouble("settings.xp-scale", 1.3);

        startRegenTask();
    }

    // --- Boss Bar helpers ---

    private BossBar getOrCreateBar(Player player) {
        return bossBars.computeIfAbsent(player.getUniqueId(), k -> {
            BossBar bar = Bukkit.createBossBar("", BarColor.RED, BarStyle.SOLID);
            bar.addPlayer(player);
            return bar;
        });
    }

    private void removeBar(Player player) {
        BossBar bar = bossBars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removePlayer(player);
            bar.setVisible(false);
        }
    }

    // Converts current HP into a 0.0–1.0 progress value for the boss bar fill.
    private double barProgress(double current, double max) {
        if (max <= 0) return 0;
        return Math.max(0.0, Math.min(1.0, current / max));
    }

    // Picks a bar color based on how much HP the player has left.
    private BarColor barColor(double progressFraction) {
        if (progressFraction > 0.5) return BarColor.RED;
        if (progressFraction > 0.25) return BarColor.YELLOW;
        return BarColor.WHITE;
    }

    // --- Display update ---

    public void updateDisplay(Player player, PlayerData data) {
        if (player.isDead()) return;
        double displayCurrent = data.toDisplay(player.getHealth());
        double displayMax     = data.displayMaxHp();
        double progress       = barProgress(displayCurrent, displayMax);

        String title = String.format(
                "\u00a7c%.0f\u00a77/\u00a7c%.0f \u00a7c\u2764  \u00a7eLv.%d",
                displayCurrent, displayMax, data.level);

        BossBar bar = getOrCreateBar(player);
        bar.setTitle(title);
        bar.setProgress(progress);
        bar.setColor(barColor(progress));
        bar.setVisible(true);
    }

    public void showXpGain(Player player, int amount) {
        if (player.isDead()) return;
        PlayerData data        = getData(player.getUniqueId());
        double displayCurrent  = data.toDisplay(player.getHealth());
        double displayMax      = data.displayMaxHp();
        double progress        = barProgress(displayCurrent, displayMax);

        String title = String.format(
                "\u00a7c%.0f\u00a77/\u00a7c%.0f \u00a7c\u2764  \u00a7eLv.%d  \u00a7a+%d XP",
                displayCurrent, displayMax, data.level, amount);

        BossBar bar = getOrCreateBar(player);
        bar.setTitle(title);
        bar.setProgress(progress);
        bar.setColor(barColor(progress));
        bar.setVisible(true);
    }

    // --- Regen task ---

    private void startRegenTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    if (player.isDead()) continue;
                    PlayerData data = getData(player.getUniqueId());
                    double maxVanilla = data.vanillaMaxHp();
                    if (player.getHealth() < maxVanilla) {
                        double newHp = Math.min(maxVanilla,
                                player.getHealth() + REGEN_AMOUNT);
                        player.setHealth(newHp);
                        updateDisplay(player, data);
                    }
                }
            }
        }.runTaskTimer(plugin, REGEN_INTERVAL, REGEN_INTERVAL);
    }

    // --- Player data lifecycle ---

    public PlayerData getData(UUID uuid) {
        return playerData.computeIfAbsent(uuid, k -> loadData(k));
    }

    public void onPlayerJoin(Player player) {
        PlayerData data = getData(player.getUniqueId());
        applyMaxHp(player, data);
        updateDisplay(player, data);
    }

    public void onPlayerQuit(Player player) {
        removeBar(player);
        saveData(player.getUniqueId());
    }

    public void applyMaxHp(Player player, PlayerData data) {
        AttributeInstance maxHpAttr =
                player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHpAttr != null) {
            maxHpAttr.setBaseValue(data.vanillaMaxHp());
        }
        if (player.getHealth() > data.vanillaMaxHp()) {
            player.setHealth(data.vanillaMaxHp());
        }
    }

    public void addXp(Player player, int amount) {
        PlayerData data = getData(player.getUniqueId());
        showXpGain(player, amount);
        data.xp += amount;
        int xpNeeded = getXpForNextLevel(data.level);
        while (data.xp >= xpNeeded) {
            data.xp -= xpNeeded;
            data.level++;
            applyMaxHp(player, data);
            player.setHealth(data.vanillaMaxHp());
            player.sendMessage(
                    "\u00a7a\u00a7l\u2756 LEVEL UP! \u00a7eYou are now level \u00a7f"
                    + data.level + "\u00a7e!");
            player.sendMessage("\u00a7eMax HP is now \u00a7f"
                    + String.format("%.0f", data.displayMaxHp()) + "\u00a7e!");
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
        player.sendMessage("\u00a7aYour level has been set to \u00a7f"
                + data.level + " \u00a7awith \u00a7f"
                + String.format("%.0f", data.displayMaxHp())
                + " \u00a7amax HP!");
    }

    public void resetPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerData data = new PlayerData();
        playerData.put(uuid, data);
        applyMaxHp(player, data);
        player.setHealth(data.vanillaMaxHp());
        updateDisplay(player, data);
        File file = new File(dataFolder, uuid.toString() + ".yml");
        if (file.exists()) file.delete();
    }

    public int getXpForNextLevel(int level) {
        return (int) (BASE_XP * Math.pow(XP_SCALE, level - 1));
    }

    private PlayerData loadData(UUID uuid) {
        File file = new File(dataFolder, uuid.toString() + ".yml");
        PlayerData data = new PlayerData();
        if (file.exists()) {
            FileConfiguration config =
                    YamlConfiguration.loadConfiguration(file);
            data.level = config.getInt("level", 1);
            data.xp    = config.getInt("xp", 0);
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
