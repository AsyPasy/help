package com.rpghealth.plugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class RPGHealthPlugin extends JavaPlugin {

    private RPGHealthManager healthManager;
    private RPGHealthListener listener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        healthManager = new RPGHealthManager(this);
        listener = new RPGHealthListener(this, healthManager);
        getServer().getPluginManager().registerEvents(listener, this);
        getLogger().info("RPGHealth plugin enabled!");
    }

    @Override
    public void onDisable() {
        if (healthManager != null) healthManager.saveAll();
        getLogger().info("RPGHealth plugin disabled!");
    }

    public RPGHealthManager getHealthManager() {
        return healthManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase()) {
            case "rpghealth" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only players can use this!");
                    return true;
                }
                RPGHealthManager.PlayerData data = healthManager.getData(player.getUniqueId());
                player.sendMessage("§6§l--- Your RPG Health Stats ---");
                player.sendMessage("§eLevel: §f" + data.level);
                player.sendMessage("§eMax HP: §f" + data.maxHp);
                player.sendMessage("§eCurrent HP: §f" + String.format("%.1f", data.currentHp));
                player.sendMessage("§eXP: §f" + data.xp + " / " + healthManager.getXpForNextLevel(data.level));
                return true;
            }
            case "sethealth" -> {
                if (!sender.hasPermission("rpghealth.admin")) {
                    sender.sendMessage("§cNo permission!");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /sethealth <player> <level>");
                    return true;
                }
                Player target = getServer().getPlayer(args[0]);
                if (target == null) {
                    sender.sendMessage("§cPlayer not found!");
                    return true;
                }
                try {
                    int level = Integer.parseInt(args[1]);
                    healthManager.setLevel(target, level);
                    sender.sendMessage("§aSet " + target.getName() + "'s level to " + level);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cInvalid level number!");
                }
                return true;
            }
            case "addxp" -> {
                if (!sender.hasPermission("rpghealth.admin")) {
                    sender.sendMessage("§cNo permission!");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /addxp <player> <amount>");
                    return true;
                }
                Player target = getServer().getPlayer(args[0]);
                if (target == null) {
                    sender.sendMessage("§cPlayer not found!");
                    return true;
                }
                try {
                    int amount = Integer.parseInt(args[1]);
                    healthManager.addXp(target, amount);
                    sender.sendMessage("§aAdded " + amount + " XP to " + target.getName());
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cInvalid XP amount!");
                }
                return true;
            }
        }
        return false;
    }
}
