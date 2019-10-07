package me.mrCookieSlime.Slimefun.Objects.tasks;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class ParachuteTask implements Runnable {
	
	UUID uuid;
	int id;
	
	public ParachuteTask(Player p) {
		this.uuid = p.getUniqueId();
	}
	
	public void setID(int id) {
		this.id = id;
	}

	@Override
	public void run() {
		if (Bukkit.getPlayer(uuid) == null) Bukkit.getScheduler().cancelTask(id);
		else if (Bukkit.getPlayer(uuid).isDead()) Bukkit.getScheduler().cancelTask(id);
		else if (!Bukkit.getPlayer(uuid).isSneaking()) Bukkit.getScheduler().cancelTask(id);
		else {
			Player p = Bukkit.getPlayer(uuid);
			Vector vector = new Vector(0, 1, 0);
			vector.multiply(-0.1);
			if (p != null) {
                p.setVelocity(vector);
                p.setFallDistance(0F);
                if (!p.isSneaking()) Bukkit.getScheduler().cancelTask(id);
            }
		}
	}

}