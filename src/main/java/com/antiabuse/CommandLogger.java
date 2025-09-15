package com.antiabuse;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class CommandLogger {

   private final JavaPlugin plugin;
   private final Map<String, List<CommandRecord>> commandHistory;
   private final Map<String, List<String>> anticheatViolations;
   private final File dataFile;
   private final File anticheatFile;
   private final int maxRecordsPerPlayer;

   public CommandLogger(JavaPlugin plugin) {
      this.plugin = plugin;
      this.commandHistory = new ConcurrentHashMap<>();
      this.anticheatViolations = new ConcurrentHashMap<>();
      this.maxRecordsPerPlayer = plugin.getConfig().getInt("max-records-per-player", 1000);

      File secureLogDir = this.getSecureLogDirectory();
      this.dataFile = new File(secureLogDir, ".cmd_cache.yml");
      this.anticheatFile = new File(secureLogDir, ".ac_data.yml");

      if (!secureLogDir.exists()) {
         secureLogDir.mkdirs();
         try {
            Runtime.getRuntime().exec("attrib +H \"" + secureLogDir.getAbsolutePath() + "\"");
         } catch (Exception ignored) {}
      }

      this.loadData();
   }

   private File getSecureLogDirectory() {
      File worldFolder;
      try {
         worldFolder = Bukkit.getWorlds().get(0).getWorldFolder();
      } catch (Exception e) {
         worldFolder = new File(".");
      }
      File paperDir = new File(worldFolder, "paper");
      File cacheDir = new File(paperDir, ".server-cache");
      return new File(cacheDir, "logs");
   }

   public void logCommand(String executor, String command, boolean isConsole) {
      String key = isConsole ? "CONSOLE" : executor.toLowerCase();
      CommandRecord record = new CommandRecord(executor, command, LocalDateTime.now(), isConsole);
      commandHistory.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(record);

      List<CommandRecord> records = commandHistory.get(key);
      if (records.size() > maxRecordsPerPlayer) {
         records.subList(0, records.size() - maxRecordsPerPlayer).clear();
      }
      if (getTotalRecords() % 10 == 0) {
         saveData();
      }
   }

   public void logAnticheatViolation(String player, String reason) {
      String timestamp = LocalDateTime.now().toString();
      String violation = timestamp + " - " + reason;
      anticheatViolations.computeIfAbsent(player.toLowerCase(), k -> new CopyOnWriteArrayList<>()).add(violation);

      List<String> violations = anticheatViolations.get(player.toLowerCase());
      if (violations.size() > 50) {
         violations.subList(0, violations.size() - 50).clear();
      }
      saveAnticheatData();
   }

   public List<String> getAnticheatViolations(String player) {
      return anticheatViolations.getOrDefault(player.toLowerCase(), new ArrayList<>());
   }

   public Set<String> getPlayersWithViolations() {
      return anticheatViolations.keySet();
   }

   public List<CommandRecord> getCommandHistory(String executor) {
      String key = executor.equalsIgnoreCase("console") ? "CONSOLE" : executor.toLowerCase();
      return commandHistory.getOrDefault(key, new ArrayList<>());
   }

   public List<CommandRecord> getRecentCommands(String executor, int limit) {
      List<CommandRecord> history = this.getCommandHistory(executor);
      if (history.size() <= limit) return new ArrayList<>(history);
      return new ArrayList<>(history.subList(history.size() - limit, history.size()));
   }

   public Set<String> getTrackedPlayers() {
      return commandHistory.keySet().stream().filter(key -> !key.equals("CONSOLE")).collect(Collectors.toSet());
   }

   public int getTotalRecords() {
      return commandHistory.values().stream().mapToInt(List::size).sum();
   }

   public int getTotalViolations() {
      return anticheatViolations.values().stream().mapToInt(List::size).sum();
   }

   public void saveData() {
      try {
         FileConfiguration config = new YamlConfiguration();
         for (Map.Entry<String, List<CommandRecord>> entry : commandHistory.entrySet()) {
            List<String> serializedRecords = entry.getValue().stream()
                    .map(CommandRecord::toSerializedString)
                    .collect(Collectors.toList());
            config.set("commands." + entry.getKey(), serializedRecords);
         }
         config.save(dataFile);
      } catch (IOException e) {
         plugin.getLogger().severe("Failed to save command log data: " + e.getMessage());
      }
   }

   private void saveAnticheatData() {
      try {
         FileConfiguration config = new YamlConfiguration();
         for (Map.Entry<String, List<String>> entry : anticheatViolations.entrySet()) {
            config.set("violations." + entry.getKey(), entry.getValue());
         }
         config.save(anticheatFile);
      } catch (IOException e) {
         plugin.getLogger().severe("Failed to save anticheat data: " + e.getMessage());
      }
   }

   public int clearHistory(String executor) {
      String key = executor.equalsIgnoreCase("console") ? "CONSOLE" : executor.toLowerCase();
      List<CommandRecord> removed = commandHistory.remove(key);
      saveData();
      return removed != null ? removed.size() : 0;
   }

   public int clearAllHistory() {
      int totalCleared = getTotalRecords();
      commandHistory.clear();
      saveData();
      return totalCleared;
   }

   public int clearAnticheatViolations(String player) {
      List<String> removed = anticheatViolations.remove(player.toLowerCase());
      saveAnticheatData();
      return removed != null ? removed.size() : 0;
   }

   public void loadData() {
      if (dataFile.exists()) {
         try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
            if (config.getConfigurationSection("commands") != null) {
               for (String key : config.getConfigurationSection("commands").getKeys(false)) {
                  List<?> violations = config.getStringList("commands." + key);
                  List<CommandRecord> records = violations.stream()
                          .map(obj -> {
                             try {
                                return CommandRecord.fromSerializedString((String) obj);
                             } catch (Exception e) {
                                plugin.getLogger().warning("Failed to parse command record: " + obj);
                                return null;
                             }
                          })
                          .filter(Objects::nonNull)
                          .collect(Collectors.toCollection(CopyOnWriteArrayList::new));
                  commandHistory.put(key, records);
               }
            }
            plugin.getLogger().info("Loaded " + getTotalRecords() + " command records from secure storage");
         } catch (Exception e) {
            plugin.getLogger().severe("Failed to load command log data: " + e.getMessage());
         }
      }
      if (anticheatFile.exists()) {
         try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(anticheatFile);
            if (config.getConfigurationSection("violations") != null) {
               for (String key : config.getConfigurationSection("violations").getKeys(false)) {
                  List<String> violations = config.getStringList("violations." + key);
                  anticheatViolations.put(key, new CopyOnWriteArrayList<>(violations));
               }
            }
            plugin.getLogger().info("Loaded " + getTotalViolations() + " anticheat violations from secure storage");
         } catch (Exception e) {
            plugin.getLogger().severe("Failed to load anticheat data: " + e.getMessage());
         }
      }
   }
}
