package com.antiabuse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class AbuseCommandExecutor implements CommandExecutor, TabCompleter {

   private final CommandLogger commandLogger;
   private final AntiAdminAbusePlugin plugin;

   private static final int COMMANDS_PER_PAGE = 10;

   private static final String PRIMARY;
   private static final String SECONDARY;
   private static final String ACCENT;
   private static final String TEXT;
   private static final String MUTED;
   private static final String SUCCESS;
   private static final String ERROR;
   private static final String WARNING;

   static {
      PRIMARY   = ChatColor.DARK_RED.toString() + ChatColor.BOLD;
      SECONDARY = ChatColor.RED.toString();
      ACCENT    = ChatColor.GOLD.toString();
      TEXT      = ChatColor.WHITE.toString();
      MUTED     = ChatColor.GRAY.toString();
      SUCCESS   = ChatColor.GREEN.toString();
      ERROR     = ChatColor.DARK_RED.toString();
      WARNING   = ChatColor.YELLOW.toString();
   }

   public AbuseCommandExecutor(CommandLogger commandLogger, AntiAdminAbusePlugin plugin) {
      this.commandLogger = commandLogger;
      this.plugin = plugin;
   }

   @Override
   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (!sender.hasPermission("antiabuse.view")) {
         sender.sendMessage(ERROR + "✖ You don't have permission to use this command.");
         return true;
      }
      if (args.length == 0) {
         this.sendMainHelp(sender);
         return true;
      }

      String subcommand = args[0].toLowerCase();
      switch (subcommand) {
         case "commands":
         case "cmd":
         case "history":
            return this.handleCommandsSubcommand(sender, Arrays.copyOfRange(args, 1, args.length));
         case "op":
         case "ops":
         case "operators":
            return this.handleOpSubcommand(sender);
         case "anticheat":
         case "ac":
         case "violations":
            return this.handleAnticheatSubcommand(sender, Arrays.copyOfRange(args, 1, args.length));
         case "spy":
            return this.handleSpySubcommand(sender, Arrays.copyOfRange(args, 1, args.length));
         case "help":
         case "?":
            return this.handleHelpSubcommand(sender, Arrays.copyOfRange(args, 1, args.length));
         case "stats":
         case "statistics":
            return this.handleStatsSubcommand(sender);
         case "reload":
            return this.handleReloadSubcommand(sender);
         default:
            this.sendMainHelp(sender);
            return true;
      }
   }

   private boolean handleCommandsSubcommand(CommandSender sender, String[] args) {
      if (args.length == 0) {
         this.sendCommandsUsage(sender);
         return true;
      }

      String target = args[0];
      String displayTarget = target.equalsIgnoreCase("console") ? "Console" : target;
      int page = 1;

      if (args.length > 1) {
         try {
            page = Integer.parseInt(args[1]);
            if (page < 1) page = 1;
         } catch (NumberFormatException e) {
            sender.sendMessage(ERROR + "✖ Invalid page number: " + args[1]);
            return true;
         }
      }

      List<CommandRecord> history = this.commandLogger.getCommandHistory(target);
      if (history.isEmpty()) {
         sender.sendMessage(WARNING + "⚠ No command history found for: " + TEXT + displayTarget);
         return true;
      }

      int totalPages = (int) Math.ceil((double)history.size() / COMMANDS_PER_PAGE);
      if (page > totalPages) page = totalPages;

      int startIndex = (page - 1) * COMMANDS_PER_PAGE;
      int endIndex = Math.min(startIndex + COMMANDS_PER_PAGE, history.size());

      List<CommandRecord> reversedHistory = new ArrayList<>(history);
      Collections.reverse(reversedHistory);

      sender.sendMessage("");
      sender.sendMessage(PRIMARY + " COMMAND HISTORY - " + displayTarget.toUpperCase());
      sender.sendMessage(MUTED + " Page " + page + " of " + totalPages + " • Total: " + history.size() + " commands");
      sender.sendMessage("");

      for (int i = startIndex; i < endIndex; i++) {
         CommandRecord record = reversedHistory.get(i);
         String formattedRecord = this.formatCommandRecord(record, i + 1);
         sender.sendMessage(formattedRecord);
      }

      sender.sendMessage("");
      if (page < totalPages) {
         sender.sendMessage(MUTED + "► Next page: " + TEXT + "/abuse commands " + target + " " + (page + 1));
      }
      if (page > 1) {
         sender.sendMessage(MUTED + "◄ Previous page: " + TEXT + "/abuse commands " + target + " " + (page - 1));
      }
      sender.sendMessage("");
      return true;
   }

   private boolean handleOpSubcommand(CommandSender sender) {
      OfflinePlayer[] ops = sender.getServer().getOperators().toArray(new OfflinePlayer[0]);

      sender.sendMessage("");
      sender.sendMessage(PRIMARY + " OPERATOR LIST");
      sender.sendMessage(MUTED + " Total: " + ops.length + " operator" + (ops.length != 1 ? "s" : ""));
      sender.sendMessage("");

      if (ops.length == 0) {
         sender.sendMessage(WARNING + "⚠ No operators found on this server.");
      } else {
         for (int i = 0; i < ops.length; i++) {
            OfflinePlayer op = ops[i];
            boolean isOnline = op.isOnline();
            String status = isOnline ? SUCCESS + "● ONLINE" : MUTED + "● OFFLINE";
            String name = isOnline ? TEXT + op.getName() : MUTED + op.getName();
            String spyStatus = this.plugin.isSpying(op.getName()) ? ACCENT + " [SPYING]" : "";
            sender.sendMessage(SECONDARY + " " + (i + 1) + ". " + name + " " + status + spyStatus);
         }
      }
      sender.sendMessage("");
      return true;
   }

   private boolean handleAnticheatSubcommand(CommandSender sender, String[] args) {
      if (args.length == 0) {
         sender.sendMessage("");
         sender.sendMessage(PRIMARY + " ANTICHEAT VIOLATIONS USAGE");
         sender.sendMessage("");
         sender.sendMessage(TEXT + "Usage: " + SECONDARY + "/abuse anticheat <player>");
         sender.sendMessage("");
         sender.sendMessage(ACCENT + "Examples:");
         sender.sendMessage(TEXT + "• " + SECONDARY + "/abuse anticheat notch " + MUTED + "- View Notch's violations");
         sender.sendMessage(TEXT + "• " + SECONDARY + "/abuse ac steve " + MUTED + "- Short form command");
         sender.sendMessage("");
         return true;
      }

      String target = args[0];
      List<String> violations = this.commandLogger.getAnticheatViolations(target);

      sender.sendMessage("");
      sender.sendMessage(PRIMARY + " ANTICHEAT VIOLATIONS - " + target.toUpperCase());
      sender.sendMessage(MUTED + " Total: " + violations.size() + " violation" + (violations.size() != 1 ? "s" : ""));
      sender.sendMessage("");

      if (violations.isEmpty()) {
         sender.sendMessage(SUCCESS + "✔ No anticheat violations found for " + target);
         sender.sendMessage(MUTED + " This player has a clean anticheat record.");
      } else {
         List<String> recentViolations = violations.size() > 10 ? violations.subList(violations.size() - 10, violations.size()) : violations;
         for (int i = recentViolations.size() - 1; i >= 0; i--) {
            String violation = recentViolations.get(i);
            sender.sendMessage(WARNING + "⚠ " + TEXT + violation);
         }
         if (violations.size() > 10) {
            sender.sendMessage("");
            sender.sendMessage(MUTED + "... and " + (violations.size() - 10) + " older violation(s)");
            sender.sendMessage("");
         }
      }
      return true;
   }

   private boolean handleSpySubcommand(CommandSender sender, String[] args) {
      if (args.length == 0) {
         this.sendSpyUsage(sender);
         return true;
      }

      String target = args[0].toLowerCase();
      String displayTarget = target.equals("console") ? "Console" : target;
      boolean currentlySpying = this.plugin.isSpying(target);

      if (currentlySpying) {
         this.plugin.removeSpyTarget(target);
         sender.sendMessage(SUCCESS + "✔ Stopped spying on: " + TEXT + displayTarget);
      } else {
         this.plugin.addSpyTarget(target);
         sender.sendMessage(SUCCESS + "✔ Now spying on: " + TEXT + displayTarget);
         sender.sendMessage(MUTED + " You will receive real-time alerts when " + displayTarget + " executes commands.");
      }
      return true;
   }

   private boolean handleHelpSubcommand(CommandSender sender, String[] args) {
      if (args.length > 0) {
         String topic = args[0].toLowerCase();
         return this.sendDetailedHelp(sender, topic);
      } else {
         this.sendMainHelp(sender);
         return true;
      }
   }

   private boolean handleStatsSubcommand(CommandSender sender) {
      Set<String> trackedPlayers = this.commandLogger.getTrackedPlayers();
      int totalRecords = this.commandLogger.getTotalRecords();
      int consoleCommands = this.commandLogger.getCommandHistory("console").size();
      OfflinePlayer[] ops = sender.getServer().getOperators().toArray(new OfflinePlayer[0]);
      Set<String> spyTargets = this.plugin.getSpyTargets();

      sender.sendMessage("");
      sender.sendMessage(PRIMARY + " PLUGIN STATISTICS");
      sender.sendMessage("");
      sender.sendMessage(ACCENT + "\u23F0 Plugin Status:");
      sender.sendMessage(TEXT + " • Installed: " + SECONDARY + this.plugin.getFormattedInstallTime());
      sender.sendMessage(TEXT + " • Uptime: " + SECONDARY + this.plugin.getUptimeDuration());
      sender.sendMessage("");
      sender.sendMessage(ACCENT + "\u1F4CA Command Tracking:");
      sender.sendMessage(TEXT + " • Total Records: " + SECONDARY + totalRecords);
      sender.sendMessage(TEXT + " • Console Commands: " + SECONDARY + consoleCommands);
      sender.sendMessage(TEXT + " • Tracked Players: " + SECONDARY + trackedPlayers.size());
      sender.sendMessage("");
      sender.sendMessage(ACCENT + "⚠ Anticheat Monitoring:");
      sender.sendMessage(TEXT + " • Total Violations: " + SECONDARY + this.commandLogger.getTotalViolations());
      sender.sendMessage(TEXT + " • Players with Violations: " + SECONDARY + this.commandLogger.getPlayersWithViolations().size());
      sender.sendMessage(TEXT + " • Real-time Alerts: " + SUCCESS + "ENABLED");
      sender.sendMessage("");
      sender.sendMessage(ACCENT + "\uD83D\uDC65 Server Security:");
      sender.sendMessage(TEXT + " • Total Operators: " + SECONDARY + ops.length);
      sender.sendMessage(TEXT + " • Currently Spying: " + SECONDARY + spyTargets.size() + " target" + (spyTargets.size() != 1 ? "s" : ""));
      sender.sendMessage("");
      sender.sendMessage(ACCENT + "⚔ Protection Features:");
      sender.sendMessage(TEXT + " • Coordinate Hiding: " + SUCCESS + "ENABLED");
      sender.sendMessage(TEXT + " • IP Address Hiding: " + SUCCESS + "ENABLED");
      sender.sendMessage(TEXT + " • Message Privacy: " + SUCCESS + "ENABLED");
      sender.sendMessage(TEXT + " • Command Logging: " + SUCCESS + "ENABLED");
      sender.sendMessage(TEXT + " • Anticheat Monitoring: " + SUCCESS + "ENABLED");
      sender.sendMessage("");

      if (!spyTargets.isEmpty()) {
         sender.sendMessage("");
         sender.sendMessage(ACCENT + "\uD83D\uDD0D Active Spy Targets: " + TEXT + String.join(MUTED + ", " + TEXT, spyTargets));
         sender.sendMessage("");
      }
      return true;
   }

   private boolean handleReloadSubcommand(CommandSender sender) {
      if (!sender.hasPermission("antiabuse.admin")) {
         sender.sendMessage(ERROR + "✖ You need 'antiabuse.admin' permission to reload the plugin.");
         return true;
      }
      try {
         this.commandLogger.saveData();
         this.commandLogger.loadData();
         sender.sendMessage(SUCCESS + "✔ Plugin configuration reloaded successfully!");
      } catch (Exception e) {
         sender.sendMessage(ERROR + "✖ Failed to reload plugin: " + e.getMessage());
      }
      return true;
   }

   private String formatCommandRecord(CommandRecord record, int index) {
      String number = MUTED + "[" + index + "]";
      String timestamp = MUTED + "[" + record.getFormattedTimestamp() + "]";
      String player = record.isConsole() ? MUTED + "CONSOLE" : TEXT + record.getExecutor();
      String command = TEXT + record.getCommand();
      return number + " " + timestamp + " " + player + SECONDARY + ": " + command;
   }

   private void sendMainHelp(CommandSender sender) {
      sender.sendMessage("");
      sender.sendMessage(PRIMARY + " ANTI-ADMIN ABUSE");
      sender.sendMessage(MUTED + " Plugin for SMP server security");
      sender.sendMessage("");
      sender.sendMessage(SECONDARY + "► " + TEXT + "/abuse commands [page]");
      sender.sendMessage(MUTED + " View command history for any player or console");
      sender.sendMessage("");
      sender.sendMessage(SECONDARY + "► " + TEXT + "/abuse op");
      sender.sendMessage(MUTED + " List all operators on the server");
      sender.sendMessage("");
      sender.sendMessage(SECONDARY + "► " + TEXT + "/abuse anticheat <player>");
      sender.sendMessage(MUTED + " View Paper anticheat violations for a player");
      sender.sendMessage("");
      sender.sendMessage(SECONDARY + "► " + TEXT + "/abuse spy <target>");
      sender.sendMessage(MUTED + " Toggle real-time command alerts for a target");
      sender.sendMessage("");
      sender.sendMessage(SECONDARY + "► " + TEXT + "/abuse stats");
      sender.sendMessage(MUTED + " View plugin statistics and security status");
      sender.sendMessage("");
      sender.sendMessage(SECONDARY + "► " + TEXT + "/abuse help [topic]");
      sender.sendMessage(MUTED + " Get detailed help on specific commands");
      sender.sendMessage("");
      sender.sendMessage(MUTED + "Type " + TEXT + "/abuse help " + MUTED + " for detailed information");
      sender.sendMessage("");
   }

   private void sendSpyUsage(CommandSender sender) {
      Set<String> currentSpies = this.plugin.getSpyTargets();
      sender.sendMessage("");
      sender.sendMessage(PRIMARY + " COMMAND SPY");
      sender.sendMessage("");
      sender.sendMessage(TEXT + "Usage: " + SECONDARY + "/abuse spy <target>");
      sender.sendMessage(MUTED + "Toggle real-time command monitoring for a specific target");
      sender.sendMessage("");
      if (!currentSpies.isEmpty()) {
         sender.sendMessage(ACCENT + "Currently spying on: " + TEXT + String.join(MUTED + ", " + TEXT, currentSpies));
      } else {
         sender.sendMessage(MUTED + "No active spy targets");
      }
      sender.sendMessage("");
   }

   private void sendCommandsUsage(CommandSender sender) {
      sender.sendMessage("");
      sender.sendMessage(PRIMARY + " COMMAND HISTORY USAGE");
      sender.sendMessage("");
      sender.sendMessage(TEXT + "Usage: " + SECONDARY + "/abuse commands [page]");
      sender.sendMessage(MUTED + "View command history for any player or console");
      sender.sendMessage("");
      Set<String> trackedPlayers = this.commandLogger.getTrackedPlayers();
      if (!trackedPlayers.isEmpty()) {
         sender.sendMessage(ACCENT + "Available players: " + TEXT + String.join(MUTED + ", " + TEXT, trackedPlayers));
      }
      if (!this.commandLogger.getCommandHistory("console").isEmpty()) {
         sender.sendMessage(ACCENT + "Console commands available: " + TEXT + "Yes");
      }
      sender.sendMessage("");
   }

   private boolean sendDetailedHelp(CommandSender sender, String topic) {
      sender.sendMessage("");
      switch (topic) {
         case "commands":
         case "cmd":
            sender.sendMessage(PRIMARY + " COMMAND HISTORY HELP");
            sender.sendMessage("");
            sender.sendMessage(ACCENT + "Purpose: " + TEXT + "View logged commands for any player or console");
            sender.sendMessage(ACCENT + "Usage: " + TEXT + "/abuse commands [page]");
            sender.sendMessage("");
            sender.sendMessage(ACCENT + "Examples:");
            sender.sendMessage(TEXT + "• /abuse commands Steve");
            sender.sendMessage(TEXT + "• /abuse commands console 2");
            sender.sendMessage(TEXT + "• /abuse cmd Notch");
            break;
         case "spy":
            sender.sendMessage(PRIMARY + " COMMAND SPY HELP");
            sender.sendMessage("");
            sender.sendMessage(ACCENT + "Purpose: " + TEXT + "Get real-time notifications when targets execute commands");
            sender.sendMessage(ACCENT + "Usage: " + TEXT + "/abuse spy <target>");
            sender.sendMessage("");
            sender.sendMessage(ACCENT + "Features:");
            sender.sendMessage(TEXT + "• Toggle on/off by running the same command again");
            sender.sendMessage(TEXT + "• Monitor multiple targets simultaneously");
            sender.sendMessage(TEXT + "• Instant alerts when commands are executed");
            sender.sendMessage("");
            sender.sendMessage(ACCENT + "Examples:");
            sender.sendMessage(TEXT + "• /abuse spy Steve");
            sender.sendMessage(TEXT + "• /abuse spy console");
            break;
         case "anticheat":
         case "ac":
         case "violations":
            sender.sendMessage(PRIMARY + " ANTICHEAT MONITOR HELP");
            sender.sendMessage("");
            sender.sendMessage(ACCENT + "Purpose: " + TEXT + "View Paper's built-in anticheat violations for players");
            sender.sendMessage(ACCENT + "Usage: " + TEXT + "/abuse anticheat <player>");
            sender.sendMessage("");
            sender.sendMessage(ACCENT + "Features:");
            sender.sendMessage(TEXT + "• Track flying, speed, and movement violations");
            sender.sendMessage(TEXT + "• Real-time alerts when players get kicked for cheating");
            sender.sendMessage(TEXT + "• View recent violation history (last 10 shown)");
            sender.sendMessage(TEXT + "• Automatic detection of suspicious behavior");
            sender.sendMessage("");
            sender.sendMessage(ACCENT + "Examples:");
            sender.sendMessage(TEXT + "• /abuse anticheat Steve");
            sender.sendMessage(TEXT + "• /abuse ac Notch");
            sender.sendMessage(TEXT + "• /abuse violations Player123");
            break;
         default:
            sender.sendMessage(PRIMARY + " AVAILABLE HELP TOPICS");
            sender.sendMessage("");
            sender.sendMessage(TEXT + "• " + SECONDARY + "/abuse help commands " + MUTED + "- Command history system");
            sender.sendMessage(TEXT + "• " + SECONDARY + "/abuse help anticheat " + MUTED + "- Paper anticheat monitoring");
            sender.sendMessage(TEXT + "• " + SECONDARY + "/abuse help spy " + MUTED + "- Real-time command monitoring");
            sender.sendMessage("");
            sender.sendMessage(MUTED + "Or just use " + TEXT + "/abuse help" + MUTED + " for the main help menu");
            break;
      }
      sender.sendMessage("");
      return true;
   }

   public void sendSpyAlert(CommandSender spyingPlayer, String target, String command) {
      if (spyingPlayer instanceof Player && ((Player) spyingPlayer).isOnline()) {
         String alertMessage = WARNING + "\uD83D\uDD0D SPY ALERT: " + TEXT + target + SECONDARY + " executed: " + TEXT + command;
         spyingPlayer.sendMessage(alertMessage);
      }
   }

   @Override
   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (!sender.hasPermission("antiabuse.view")) {
         return new ArrayList<>();
      }
      if (args.length == 1) {
         List<String> subcommands = Arrays.asList("commands", "op", "anticheat", "spy", "help", "stats", "reload");
         String input = args[0].toLowerCase();
         return subcommands.stream()
                 .filter(sub -> sub.toLowerCase().startsWith(input))
                 .collect(Collectors.toList());
      }
      if (args.length == 2) {
         String subcommand = args[0].toLowerCase();
         String input = args[1].toLowerCase();
         ArrayList<String> suggestions;

         if (subcommand.equals("commands") || subcommand.equals("spy")) {
            suggestions = new ArrayList<>();
            suggestions.add("console");
            suggestions.addAll(this.commandLogger.getTrackedPlayers());
            return suggestions.stream()
                    .filter(s -> s.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
         }
         if (subcommand.equals("anticheat")) {
            suggestions = new ArrayList<>(this.commandLogger.getPlayersWithViolations());
            suggestions.addAll(this.commandLogger.getTrackedPlayers());
            return suggestions.stream()
                    .distinct()
                    .filter(s -> s.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
         }
         if (subcommand.equals("help")) {
            List<String> helpTopics = Arrays.asList("commands", "anticheat", "spy", "op", "stats");
            return helpTopics.stream()
                    .filter(t -> t.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
         }
      }
      return new ArrayList<>();
   }
}
