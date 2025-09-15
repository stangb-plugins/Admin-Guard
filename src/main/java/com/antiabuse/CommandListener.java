package com.antiabuse;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class CommandListener implements Listener {

   private final CommandLogger commandLogger;
   private final AntiAdminAbusePlugin plugin;

   private static final String WARNING;
   private static final String TEXT;
   private static final String SECONDARY;
   private static final Pattern MESSAGE_COMMAND_PATTERN;

   private final Map<String, PendingCommand> pendingCommands = new ConcurrentHashMap<>();

   static {
      WARNING = ChatColor.YELLOW.toString();
      TEXT = ChatColor.WHITE.toString();
      SECONDARY = ChatColor.RED.toString();
      MESSAGE_COMMAND_PATTERN = Pattern.compile(
              "^/(minecraft:)?(msg|tell|whisper|w|message|m|reply|r|pm|dm|mail|mail send)(?:$|\\s.*)", Pattern.CASE_INSENSITIVE
      );
   }

   public CommandListener(CommandLogger commandLogger, AntiAdminAbusePlugin plugin) {
      this.commandLogger = commandLogger;
      this.plugin = plugin;

      new BukkitRunnable() {
         public void run() {
            long now = System.currentTimeMillis();
            pendingCommands.entrySet().removeIf(entry -> now - entry.getValue().timestamp > 10000L);
         }
      }.runTaskTimer(plugin, 200L, 200L);
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
      Player player = event.getPlayer();
      String command = event.getMessage();
      if (!player.hasPermission("antiabuse.exempt")) {
         String lowerCommand = command.toLowerCase();
         if (!lowerCommand.startsWith("/commands") && !lowerCommand.startsWith("/abuse")) {
            if (!MESSAGE_COMMAND_PATTERN.matcher(command).matches()) {
               if (this.isValidCommandFormat(command)) {
                  String playerId = player.getUniqueId().toString();
                  this.pendingCommands.put(playerId, new PendingCommand(player.getName(), command, System.currentTimeMillis()));
               }
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onPlayerCommandMonitor(PlayerCommandPreprocessEvent event) {
      Player player = event.getPlayer();
      String playerId = player.getUniqueId().toString();
      final PendingCommand pending = this.pendingCommands.remove(playerId);
      if (pending != null && !event.isCancelled()) {
         new BukkitRunnable() {
            public void run() {
               commandLogger.logCommand(pending.executor, pending.command, false);
               sendSpyAlerts(pending.executor, pending.command);
            }
         }.runTaskLater(this.plugin, 2L);
      }
   }

   private boolean isValidCommandFormat(String command) {
      if (!command.startsWith("/")) return false;
      String baseCmd = command.split(" ")[0].toLowerCase();
      if (baseCmd.length() <= 1) return false;
      return !baseCmd.equals("/help") && !baseCmd.equals("/?");
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onServerCommand(ServerCommandEvent event) {
      String command = event.getCommand();
      String lowerCommand = command.toLowerCase();
      if (!lowerCommand.startsWith("stop") &&
              !lowerCommand.startsWith("restart") &&
              !lowerCommand.startsWith("reload") &&
              !lowerCommand.startsWith("save-all") &&
              !lowerCommand.startsWith("abuse")) {

         String testCommand = "/" + command;
         if (!MESSAGE_COMMAND_PATTERN.matcher(testCommand).matches()) {
            if (!command.startsWith("/")) command = "/" + command;
            this.commandLogger.logCommand("CONSOLE", command, true);
            this.sendSpyAlerts("console", command);
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onPlayerKick(PlayerKickEvent event) {
      Player player = event.getPlayer();
      String reason = event.getReason();
      if (this.isAnticheatKick(reason)) {
         String kickInfo = "ANTICHEAT KICK: " + player.getName() + " - " + reason;
         this.commandLogger.logAnticheatViolation(player.getName(), reason);
         String alertMessage = WARNING + "⚠ ANTICHEAT: " + TEXT + player.getName() + SECONDARY + " kicked for: " + TEXT + reason;
         for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.hasPermission("antiabuse.view")) {
               onlinePlayer.sendMessage(alertMessage);
            }
         }
         this.plugin.getLogger().warning(kickInfo);
      }
   }

   private boolean isAnticheatKick(String reason) {
      if (reason == null) return false;
      String lowerReason = reason.toLowerCase();
      return lowerReason.contains("flying") ||
              lowerReason.contains("speed") ||
              lowerReason.contains("movement") ||
              lowerReason.contains("anticheat") ||
              lowerReason.contains("illegal") ||
              lowerReason.contains("cheat") ||
              lowerReason.contains("hack") ||
              lowerReason.contains("exploit") ||
              lowerReason.contains("suspicious");
   }

   private void sendSpyAlerts(String target, String command) {
      if (this.plugin.isSpying(target)) {
         String displayTarget = target.equals("console") ? "Console" : target;
         String alertMessage = WARNING + "\uD83D\uDD0D SPY ALERT: " + TEXT + displayTarget + SECONDARY + " executed: " + TEXT + command;
         for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.hasPermission("antiabuse.view")) {
               onlinePlayer.sendMessage(alertMessage);
            }
         }
         this.plugin.getLogger().info("SPY ALERT: " + displayTarget + " executed: " + command);
      }
   }

   private static class PendingCommand {
      final String executor;
      final String command;
      final long timestamp;

      PendingCommand(String executor, String command, long timestamp) {
         this.executor = executor;
         this.command = command;
         this.timestamp = timestamp;
      }
   }
}
