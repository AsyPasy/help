package com.rpghealth.plugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;
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
    private final ProtocolManager pm;
    private final Map<UUID, PlayerData> playerData = new HashMap<>();
    private final File dataFolder;

    private final double VANILLA_BASE_HP;
    private final double DISPLAY_BASE_HP;
    private final double DISPLAY_HP_PER_LEVEL;
    private final double VANILLA_HP_PER_LEVEL;
    private final double REGEN_AMOUNT;
    private final long   REGEN_INTERVAL;
    private final int    BASE_XP;
    private final double XP_SCALE;

    // -------------------------------------------------------------------------

    public class PlayerData {
        public int level = 1;
        public int xp    = 0;

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

    // -------------------------------------------------------------------------

    public RPGHealthManager(RPGHealthPlugin plugin) {
        this.plugin     = plugin;
        this.pm         = ProtocolLibrary.getProtocolManager();
        this.dataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!dataFolder.exists()) dataFolder.mkdirs();

        FileConfiguration cfg = plugin.getConfig();
        VANILLA_BASE_HP      = cfg.getDouble("settings.base-vanilla-hp",      20.0);
        DISPLAY_BASE_HP      = cfg.getDouble("settings.base-display-hp",      100.0);
        DISPLAY_HP_PER_LEVEL = cfg.getDouble("settings.display-hp-per-level", 5.0);
        VANILLA_HP_PER_LEVEL = cfg.getDouble("settings.vanilla-hp-per-level", 0.5);
        REGEN_AMOUNT         = cfg.getDouble("settings.regen-amount",         0.1);
        REGEN_INTERVAL       = cfg.getLong  ("settings.regen-interval",       60L);
        BASE_XP              = cfg.getInt   ("settings.base-xp",              200);
        XP_SCALE             = cfg.getDouble("settings.xp-scale",             1.3);

        startRegenTask();
    }

    // -------------------------------------------------------------------------
    // Action-bar display — sent via ProtocolLib SYSTEM_CHAT (overlay = true).
    //
    // In 1.19+ the action bar is a SYSTEM_CHAT packet with the overlay flag
    // set to true. This is the slot that sits directly ABOVE the vanilla hearts
    // and hunger bars, at the bottom-centre of the screen.
    //
    // Using ProtocolLib here gives us raw packet control (no Bungee/Spigot
    // compatibility quirks) and a clean fallback path.
    // -------------------------------------------------------------------------

    /**
     * Converts a legacy-colour-code string to JSON and sends it as an
     * action-bar packet via ProtocolLib. Falls back to the Spigot API if
     * anything goes wrong.
     */
    private void sendActionBar(Player player, String legacyText) {
        try {
            // Convert §-codes → JSON component so the packet carries colour correctly
            String json = ComponentSerializer.toString(
                    TextComponent.fromLegacyText(legacyText));

            PacketContainer pkt = pm.createPacket(PacketType.Play.Server.SYSTEM_CHAT);
            pkt.getChatComponents().write(0, WrappedChatComponent.fromJson(json));
            pkt.getBooleans().write(0, true); // true = overlay / action bar
            pm.sendServerPacket(player, pkt);

        } catch (Exception e) {
            // Fallback — original Spigot action-bar call
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(legacyText));
        }
    }

    public void updateDisplay(Player player, PlayerData data) {
        if (player.isDead()) return;
        double cur = data.toDisplay(player.getHealth());
        double max = data.displayMaxHp();
        sendActionBar(player, String.format(
                "\u00a7c%.0f\u00a77/\u00a7c%.0f \u00a7c\u2764  \u00a7eLv.%d",
                cur, max, data.level));
    }

    public void showXpGain(Player player, int amount) {
        if (player.isDead()) return;
        PlayerData data = getData(player.getUniqueId());
        double cur = data.toDisplay(player.getHealth());
        double max = data.displayMaxHp();
        sendActionBar(player, String.format(
                "\u00a7c%.0f\u00a77/\u00a7c%.0f \u00a7c\u2764  \u00a7eLv.%d  \u00a7a+%d XP",
                cur, max, data.level, amount));
    }

    // -------------------------------------------------------------------------
    // Regen
    // -------------------------------------------------------------------------

    private void startRegenTask() {
        new BukkitRunnable() {
            @Override public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    if (player.isDead()) continue;
                    PlayerData data     = getData(player.getUniqueId());
                    double maxVanilla   = data.vanillaMaxHp();
                    if (player.getHealth() < maxVanilla) {
                        player.setHealth(Math.min(maxVanilla, player.getHealth() + REGEN_AMOUNT));
                        updateDisplay(player, data);
                    }
                }
            }
        }.runTaskTimer(plugin, REGEN_INTERVAL, REGEN_INTERVAL);
    }

    // -------------------------------------------------------------------------
    // Player lifecycle
    // -------------------------------------------------------------------------

    public PlayerData getData(UUID uuid) {
        return playerData.computeIfAbsent(uuid, this::loadData);
    }

    public void onPlayerJoin(Player player) {
        PlayerData data = getData(player.getUniqueId());
        applyMaxHp(player, data);
        updateDisplay(player, data);
    }

    public void onPlayerQuit(Player player) {
        saveData(player.getUniqueId());
    }

    public void applyMaxHp(Player player, PlayerData data) {
        AttributeInstance attr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attr != null) attr.setBaseValue(data.vanillaMaxHp());
        if (player.getHealth() > data.vanillaMaxHp()) player.setHealth(data.vanillaMaxHp());
    }

    // -------------------------------------------------------------------------
    // XP / levelling
    // -------------------------------------------------------------------------

    public void addXp(Player player, int amount) {
        PlayerData data = getData(player.getUniqueId());
        showXpGain(player, amount);
        data.xp += amount;
        int needed = getXpForNextLevel(data.level);
        while (data.xp >= needed) {
            data.xp  -= needed;
            data.level++;
            applyMaxHp(player, data);
            player.setHealth(data.vanillaMaxHp());
            player.sendMessage("\u00a7a\u00a7l\u2756 LEVEL UP! \u00a7eYou are now level \u00a7f"
                    + data.level + "\u00a7e!");
            player.sendMessage("\u00a7eMax HP is now \u00a7f"
                    + String.format("%.0f", data.displayMaxHp()) + "\u00a7e!");
            player.getWorld().playSound(player.getLocation(),
                    org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            needed = getXpForNextLevel(data.level);
        }
        updateDisplay(player, data);
    }

    public void setLevel(Player player, int level) {
        PlayerData data = getData(player.getUniqueId());
        data.level = Math.max(1, level);
        data.xp    = 0;
        applyMaxHp(player, data);
        player.setHealth(data.vanillaMaxHp());
        updateDisplay(player, data);
        player.sendMessage("\u00a7aYour level has been set to \u00a7f"
                + data.level + " \u00a7awith \u00a7f"
                + String.format("%.0f", data.displayMaxHp()) + " \u00a7amax HP!");
    }

    public void resetPlayer(Player player) {
        UUID uuid       = player.getUniqueId();
        PlayerData data = new PlayerData();
        playerData.put(uuid, data);
        applyMaxHp(player, data);
        player.setHealth(data.vanillaMaxHp());
        updateDisplay(player, data);
        File file = new File(dataFolder, uuid + ".yml");
        if (file.exists()) file.delete();
    }

    public int getXpForNextLevel(int level) {
        return (int) (BASE_XP * Math.pow(XP_SCALE, level - 1));
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    private PlayerData loadData(UUID uuid) {
        File file   = new File(dataFolder, uuid + ".yml");
        PlayerData data = new PlayerData();
        if (file.exists()) {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            data.level = cfg.getInt("level", 1);
            data.xp    = cfg.getInt("xp",    0);
        }
        return data;
    }

    private void saveData(UUID uuid) {
        PlayerData data = playerData.get(uuid);
        if (data == null) return;
        File file               = new File(dataFolder, uuid + ".yml");
        FileConfiguration cfg   = new YamlConfiguration();
        cfg.set("level", data.level);
        cfg.set("xp",    data.xp);
        try { cfg.save(file); }
        catch (IOException e) {
            plugin.getLogger().warning("Failed to save data for " + uuid);
        }
    }

    public void saveAll() {
        playerData.keySet().forEach(this::saveData);
    }
}
