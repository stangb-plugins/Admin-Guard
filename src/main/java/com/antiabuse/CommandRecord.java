package com.antiabuse;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CommandRecord {
   private final String executor;
   private final String command;
   private final LocalDateTime timestamp;
   private final boolean isConsole;
   private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

   public CommandRecord(String executor, String command, LocalDateTime timestamp, boolean isConsole) {
      this.executor = executor;
      this.command = command;
      this.timestamp = timestamp;
      this.isConsole = isConsole;
   }

   public String getExecutor() {
      return this.executor;
   }

   public String getCommand() {
      return this.command;
   }

   public LocalDateTime getTimestamp() {
      return this.timestamp;
   }

   public boolean isConsole() {
      return this.isConsole;
   }

   public String getFormattedTimestamp() {
      return this.timestamp.format(FORMATTER);
   }

   public String toString() {
      return String.format("[%s] %s: %s", this.getFormattedTimestamp(), this.isConsole ? "CONSOLE" : this.executor, this.command);
   }

   public String toSerializedString() {
      return String.format("%s|%s|%s|%s", this.executor, this.command, this.timestamp.toString(), this.isConsole);
   }

   public static CommandRecord fromSerializedString(String serialized) {
      String[] parts = serialized.split("\\|", 4);
      if (parts.length != 4) {
         throw new IllegalArgumentException("Invalid serialized command record: " + serialized);
      } else {
         return new CommandRecord(parts[0], parts[1], LocalDateTime.parse(parts[2]), Boolean.parseBoolean(parts[3]));
      }
   }
}
