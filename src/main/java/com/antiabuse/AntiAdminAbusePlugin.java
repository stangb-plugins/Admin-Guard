package com.antiabuse;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.Filter.Result;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class AntiAdminAbusePlugin extends JavaPlugin {

   private static final Pattern COORDS = Pattern.compile("\\(\\[[^]]+]\\s*-?\\d+(\\.\\d+)?,\\s*-?\\d+(\\.\\d+)?,\\s*-?\\d+(\\.\\d+)?\\)");
   private static final Pattern IP_ADDRESSES = Pattern.compile("(?:^|[^\\d])(?:(?:(?:25[0-5]|2[0-4][0-9]|[4]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[4]?[0-9][0-9]?)(?::[0-9]{1,5})?|(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|(?:[0-9a-fA-F]{0,4}::?){1,7}[0-9a-fA-F]{0,4})(?=[^\\d]|$)");
   private static final Pattern MESSAGE_COMMANDS = Pattern.compile("issued server command: /(?:minecraft:)?(?:msg|tell|whisper|w|message|m|reply|r|pm|dm|minecraft:msg|minecraft:tell|minecraft:whisper)\\s+.*", Pattern.CASE_INSENSITIVE);

   private CommandLogger commandLogger;
   private AbuseCommandExecutor commandExecutor;
   private Set<String> spyTargets = ConcurrentHashMap.newKeySet();
   private long pluginInstallTime;

   @Override
   public void onEnable() {
      this.initializeInstallTime();
      this.commandLogger = new CommandLogger(this);
      this.commandExecutor = new AbuseCommandExecutor(this.commandLogger, this);
      this.getServer().getPluginManager().registerEvents(new CommandListener(this.commandLogger, this), this);
      this.getServer().getPluginManager().registerEvents(new CommandAbuse(), this); // Register the listener
      this.registerCommands();
      this.setupLogFilters();
      this.getLogger().info("AntiAdminAbuse plugin enabled successfully!");
   }

   @Override
   public void onDisable() {
      if (this.commandLogger != null) {
         this.commandLogger.saveData();
      }
      this.getLogger().info("AntiAdminAbuse plugin disabled!");
   }

   private void registerCommands() {
      try {
         PluginCommand abuseCmd = this.getCommand("abuse");
         if (abuseCmd != null) {
            abuseCmd.setExecutor(this.commandExecutor);
            abuseCmd.setTabCompleter(this.commandExecutor);
            this.getLogger().info("Commands registered via plugin.yml!");
            return;
         }
      } catch (Exception var3) {
         this.getLogger().info("Legacy command registration failed, using Paper method: " + var3.getMessage());
      }
      try {
         BukkitCommand abuseCommand = new BukkitCommand("abuse", "Anti-admin abuse commands", "/abuse [args...]", Arrays.asList("antiabuse")) {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
               return AntiAdminAbusePlugin.this.commandExecutor.onCommand(sender, this, commandLabel, args);
            }
            @Override
            public List<String> tabComplete(CommandSender sender, String alias, String[] args) throws IllegalArgumentException {
               List<String> completions = AntiAdminAbusePlugin.this.commandExecutor.onTabComplete(sender, this, alias, args);
               return completions != null ? completions : Arrays.asList();
            }
         };
         abuseCommand.setPermission("antiabuse.view");
         abuseCommand.setPermissionMessage("You don't have permission to use this command.");
         this.getServer().getCommandMap().register("antiabuse", abuseCommand);
         this.getLogger().info("Commands registered using Paper method!");
      } catch (Exception var2) {
         this.getLogger().severe("Failed to register commands: " + var2.getMessage());
      }
   }

   private void setupLogFilters() {
      try {
         final Logger root = (Logger) LogManager.getRootLogger();
         root.addFilter(new AbstractFilter() {
            @Override
            public Result filter(LogEvent event) {
               if (event.getMessage() != null) {
                  String message = event.getMessage().getFormattedMessage();
                  String cleaned;
                  if (AntiAdminAbusePlugin.COORDS.matcher(message).find()) {
                     cleaned = AntiAdminAbusePlugin.COORDS.matcher(message).replaceAll("(coords hidden)");
                     root.log(event.getLevel(), cleaned);
                     return Result.DENY;
                  }
                  if (AntiAdminAbusePlugin.IP_ADDRESSES.matcher(message).find()) {
                     cleaned = AntiAdminAbusePlugin.IP_ADDRESSES.matcher(message).replaceAll("(IP hidden)");
                     root.log(event.getLevel(), cleaned);
                     return Result.DENY;
                  }
                  if (AntiAdminAbusePlugin.MESSAGE_COMMANDS.matcher(message).find()) {
                     return Result.DENY;
                  }
               }
               return Result.NEUTRAL;
            }
         });
         this.getLogger().info("Log filters enabled! (Coordinates, IP Addresses & Private Messages hidden)");
      } catch (Exception var2) {
         this.getLogger().warning("Failed to set up log filters: " + var2.getMessage());
      }
   }

   public CommandLogger getCommandLogger() {
      return this.commandLogger;
   }

   public Set<String> getSpyTargets() {
      return this.spyTargets;
   }

   public void addSpyTarget(String target) {
      this.spyTargets.add(target.toLowerCase());
   }

   public void removeSpyTarget(String target) {
      this.spyTargets.remove(target.toLowerCase());
   }

   public boolean isSpying(String target) {
      return this.spyTargets.contains(target.toLowerCase());
   }

   private void initializeInstallTime() {
      if (!this.getConfig().contains("install-time")) {
         long currentTime = System.currentTimeMillis();
         this.getConfig().set("install-time", currentTime);
         this.saveConfig();
         this.pluginInstallTime = currentTime;
         java.util.logging.Logger var10000 = this.getLogger();
         Date var10001 = new Date(currentTime);
         var10000.info("Plugin install time recorded: " + String.valueOf(var10001));
      } else {
         this.pluginInstallTime = this.getConfig().getLong("install-time");
      }
   }

   public long getPluginInstallTime() {
      return this.pluginInstallTime;
   }

   public String getFormattedInstallTime() {
      SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy 'at' HH:mm:ss");
      return sdf.format(new Date(this.pluginInstallTime));
   }

   public String getUptimeDuration() {
      long uptime = System.currentTimeMillis() - this.pluginInstallTime;
      long days = uptime / 86400000L;
      long hours = uptime % 86400000L / 3600000L;
      long minutes = uptime % 3600000L / 60000L;
      if (days > 0L) {
         return days + " day" + (days != 1L ? "s" : "") + ", " + hours + " hour" + (hours != 1L ? "s" : "");
      } else {
         return hours > 0L ? hours + " hour" + (hours != 1L ? "s" : "") + ", " + minutes + " minute" + (minutes != 1L ? "s" : "") : minutes + " minute" + (minutes != 1L ? "s" : "");
      }
   }
}
