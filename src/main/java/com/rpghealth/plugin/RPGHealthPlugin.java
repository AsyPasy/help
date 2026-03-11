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
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        switch (command.getName().toLowerCase()) {
            case "rpghealth" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only players can use this!");
                    return true;
                }
                RPGHealthManager.PlayerData data =
                        healthManager.getData(player.getUniqueId());
                double displayCurrent = data.toDisplay(player.getHealth());
                double displayMax = data.displayMaxHp();
                int xpNeeded = healthManager.getXpForNextLevel(data.level);
                player.sendMessage("\u00a76\u00a7l--- Your RPG Health Stats ---");
                player.sendMessage("\u00a7eLevel: \u00a7f" + data.level);
                player.sendMessage("\u00a7eMax HP: \u00a7f"
                        + String.format("%.0f", displayMax));
                player.sendMessage("\u00a7eCurrent HP: \u00a7f"
                        + String.format("%.1f", displayCurrent));
                player.sendMessage("\u00a7eXP: \u00a7f" + data.xp
                        + " \u00a7e/ \u00a7f" + xpNeeded);
                return true;
            }
            case "sethealth" -> {
                if (!sender.hasPermission("rpghealth.admin")) {
                    sender.sendMessage("\u00a7cNo permission!");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("\u00a7cUsage: /sethealth <player> <level>");
                    return true;
                }
                Player target = getServer().getPlayer(args[0]);
                if (target == null) {
                    sender.sendMessage("\u00a7cPlayer not found!");
                    return true;
                }
                try {
                    int level = Integer.parseInt(args[1]);
                    healthManager.setLevel(target, level);
                    sender.sendMessage("\u00a7aSet " + target.getName()
                            + "'s level to " + level);
                } catch (NumberFormatException e) {
                    sender.sendMessage("\u00a7cInvalid level number!");
                }
                return true;
            }
            case "addxp" -> {
                if (!sender.hasPermission("rpghealth.admin")) {
                    sender.sendMessage("\u00a7cNo permission!");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("\u00a7cUsage: /addxp <player> <amount>");
                    return true;
                }
                Player target = getServer().getPlayer(args[0]);
                if (target == null) {
                    sender.sendMessage("\u00a7cPlayer not found!");
                    return true;
                }
                try {
                    int amount = Integer.parseInt(args[1]);
                    healthManager.addXp(target, amount);
                    sender.sendMessage("\u00a7aAdded " + amount
                            + " XP to " + target.getName());
                } catch (NumberFormatException e) {
                    sender.sendMessage("\u00a7cInvalid XP amount!");
                }
                return true;
            }
            case "rpgreset" -> {
                if (!sender.hasPermission("rpghealth.admin")) {
                    sender.sendMessage("\u00a7cNo permission!");
                    return true;
                }
                if (args.length < 1) {
                    sender.sendMessage("\u00a7cUsage: /rpgreset <player>");
                    return true;
                }
                Player target = getServer().getPlayer(args[0]);
                if (target == null) {
                    sender.sendMessage("\u00a7cPlayer not found! They must be online.");
                    return true;
                }
                healthManager.resetPlayer(target);
                sender.sendMessage("\u00a7aReset \u00a7f" + target.getName()
                        + "\u00a7a's RPG data to level 1!");
                target.sendMessage(
                        "\u00a7cYour RPG data has been reset to level 1!");
                return true;
            }
        }
        return false;
    }
}
