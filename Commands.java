package com.example.anticheat;

import org.bukkit.plugin.java.JavaPlugin;

public class AntiCheatPlugin extends JavaPlugin {
    private CheckManager checkManager;
    
    @Override
    public void onEnable() {
        this.checkManager = new CheckManager(this);
        getServer().getPluginManager().registerEvents(new PlayerListener(checkManager), this);
        getCommand("ac").setExecutor(new AntiCheatCommand(checkManager));
        
        getLogger().info("AntiCheatPlugin включен!");
        
        // Запуск периодических проверок
        new CheckTask(checkManager).runTaskTimer(this, 0L, 20L); // Каждую секунду
    }
    
    @Override
    public void onDisable() {
        getLogger().info("AntiCheatPlugin выключен!");
    }
}
package com.example.anticheat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import java.util.*;

public class CheckManager {
    private final AntiCheatPlugin plugin;
    private final Map<UUID, PlayerData> playerDataMap = new HashMap<>();
    private final Map<UUID, Integer> violationLevels = new HashMap<>();
    
    public CheckManager(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }
    
    public PlayerData getData(Player player) {
        return playerDataMap.computeIfAbsent(player.getUniqueId(), k -> new PlayerData(player));
    }
    
    public void runChecks(Player player) {
        PlayerData data = getData(player);
        
        // Проверка на Fly
        if (checkFly(player, data)) {
            flag(player, "Fly", 1);
        }
        
        // Проверка на Speed
        if (checkSpeed(player, data)) {
            flag(player, "Speed", 1);
        }
        
        // Проверка на KillAura (быстрое нанесение урона)
        if (checkKillAura(player, data)) {
            flag(player, "KillAura", 2);
        }
        
        // Проверка на Reach (дальность атаки)
        if (checkReach(player, data)) {
            flag(player, "Reach", 2);
        }
        
        data.updateLocation();
    }
    
    private boolean checkFly(Player player, PlayerData data) {
        if (player.isFlying() || player.isGliding() || player.isInsideVehicle()) 
            return false;
        
        if (!player.isOnGround() && player.getLocation().getBlock().getRelative(0, -1, 0).getType().isAir()) {
            long airTime = System.currentTimeMillis() - data.getLastGroundTime();
            return airTime > 2000; // Более 2 секунд в воздухе без полёта
        }
        return false;
    }
    
    private boolean checkSpeed(Player player, PlayerData data) {
        if (player.isFlying() || player.isInsideVehicle()) 
            return false;
        
        double speed = data.getCurrentSpeed();
        double maxSpeed = 0.36; // Максимальная нормальная скорость
        
        if (player.isSprinting()) {
            maxSpeed = 0.45;
        }
        
        return speed > maxSpeed;
    }
    
    private boolean checkKillAura(Player player, PlayerData data) {
        long currentTime = System.currentTimeMillis();
        long lastAttack = data.getLastAttackTime();
        
        if (lastAttack > 0) {
            long difference = currentTime - lastAttack;
            if (difference < 100) { // Атака чаще чем раз в 100мс
                return true;
            }
        }
        data.setLastAttackTime(currentTime);
        return false;
    }
    
    private boolean checkReach(Player player, PlayerData data) {
        Player target = data.getLastAttacked();
        if (target == null) return false;
        
        double distance = player.getLocation().distance(target.getLocation());
        double maxReach = 3.5; // Максимальная нормальная дальность атаки
        
        if (player.isSprinting()) {
            maxReach = 4.0;
        }
        
        return distance > maxReach;
    }
    
    public void flag(Player player, String check, int severity) {
        UUID uuid = player.getUniqueId();
        int violations = violationLevels.getOrDefault(uuid, 0) + severity;
        violationLevels.put(uuid, violations);
        
        // Уведомление администраторов
        String alert = ChatColor.RED + "[AC] " + ChatColor.WHITE + player.getName() + 
                      ChatColor.GRAY + " возможный чит " + ChatColor.YELLOW + check +
                      ChatColor.GRAY + " (Уровень: " + violations + ")";
        
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("anticheat.alerts")) {
                online.sendMessage(alert);
            }
        }
        
        // Автоматическое наказание при высокой вероятности
        if (violations >= 10) {
Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
                                  "kick " + player.getName() + " Обнаружены читы!");
            violationLevels.remove(uuid);
        }
    }
}package com.example.anticheat;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public class PlayerData {
    private final Player player;
    private Location lastLocation;
    private long lastGroundTime;
    private long lastAttackTime;
    private Player lastAttacked;
    
    public PlayerData(Player player) {
        this.player = player;
        this.lastLocation = player.getLocation();
        this.lastGroundTime = System.currentTimeMillis();
    }
    
    public void updateLocation() {
        Location current = player.getLocation();
        
        if (player.isOnGround()) {
            lastGroundTime = System.currentTimeMillis();
        }
        
        lastLocation = current;
    }
    
    public double getCurrentSpeed() {
        if (lastLocation == null) return 0;
        
        Location current = player.getLocation();
        double distance = lastLocation.distance(current);
        
        return distance; // Расстояние между проверками
    }
    
    // Геттеры и сеттеры
    public long getLastGroundTime() { return lastGroundTime; }
    public long getLastAttackTime() { return lastAttackTime; }
    public void setLastAttackTime(long time) { this.lastAttackTime = time; }
    public Player getLastAttacked() { return lastAttacked; }
    public void setLastAttacked(Player player) { this.lastAttacked = player; }
}
package com.example.anticheat;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class PlayerListener implements Listener {
    private final CheckManager checkManager;
    
    public PlayerListener(CheckManager checkManager) {
        this.checkManager = checkManager;
    }
    
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        checkManager.getData(e.getPlayer());
    }
    
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // Очистка данных при выходе
    }
    
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof org.bukkit.entity.Player) {
            Player player = (Player) e.getDamager();
            PlayerData data = checkManager.getData(player);
            
            if (e.getEntity() instanceof Player) {
                data.setLastAttacked((Player) e.getEntity());
            }
            data.setLastAttackTime(System.currentTimeMillis());
        }
    }
}
package com.example.anticheat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AntiCheatCommand implements CommandExecutor {
    private final CheckManager checkManager;
    
    public AntiCheatCommand(CheckManager checkManager) {
        this.checkManager = checkManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("anticheat.admin")) {
            sender.sendMessage(ChatColor.RED + "Нет прав!");
            return true;
        }
        
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "Использование:");
            sender.sendMessage(ChatColor.YELLOW + "/ac alerts - Вкл/выкл уведомления");
            sender.sendMessage(ChatColor.YELLOW + "/ac check <игрок> - Проверить игрока");
            return true;
        }
        
        if (args[0].equalsIgnoreCase("check") && args.length > 1) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target != null) {
                checkManager.runChecks(target);
                sender.sendMessage(ChatColor.GREEN + "Проверка выполнена для " + target.getName());
            }
        }
        
        return true;
    }
}
package com.example.anticheat;

import org.bukkit.scheduler.BukkitRunnable;

public class CheckTask extends BukkitRunnable {
    private final CheckManager checkManager;
    
    public CheckTask(CheckManager checkManager) {
        this.checkManager = checkManager;
    }
    
    @Override
    public void run() {
        checkManager.getPlugin().getServer().getOnlinePlayers().forEach(player -> {
            checkManager.runChecks(player);
        });
    }
}
name: AntiCheat
version: 1.0
author: speed_
main: com.example.anticheat.AntiCheatPlugin
api-version: 1.16

commands:
  ac:
    description: Управление античитом
    usage: /ac <subcommand>
    permission: anticheat.admin

permissions:
  anticheat.admin:
    description: Доступ к командам античита
    default: op
  anticheat.alerts:
    description: Получение уведомлений о читах
    default: op
