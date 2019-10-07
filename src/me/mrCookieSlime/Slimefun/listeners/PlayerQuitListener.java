package me.mrCookieSlime.Slimefun.listeners;

import me.mrCookieSlime.Slimefun.SlimefunPlugin;
import me.mrCookieSlime.Slimefun.api.PlayerProfile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

	public PlayerQuitListener(SlimefunPlugin plugin) {
		plugin.getServer().getPluginManager().registerEvents(this, plugin);
	}

	@EventHandler
	public void onDisconnect(PlayerQuitEvent e) {
        SlimefunPlugin.instance.getUtilities().guideHistory.remove(e.getPlayer().getUniqueId());

        if (PlayerProfile.isLoaded(e.getPlayer().getUniqueId())) {
            PlayerProfile.fromUUID(e.getPlayer().getUniqueId()).markForDeletion();
        }
	}


}