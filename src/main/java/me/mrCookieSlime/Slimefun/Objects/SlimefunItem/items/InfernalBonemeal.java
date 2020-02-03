package me.mrCookieSlime.Slimefun.Objects.SlimefunItem.items;

import java.util.Optional;

import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.inventory.ItemStack;

import io.github.thebusybiscuit.cscorelib2.inventory.ItemUtils;
import me.mrCookieSlime.Slimefun.Lists.RecipeType;
import me.mrCookieSlime.Slimefun.Objects.Category;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.SimpleSlimefunItem;
import me.mrCookieSlime.Slimefun.Objects.handlers.ItemUseHandler;
import me.mrCookieSlime.Slimefun.api.SlimefunItemStack;

public class InfernalBonemeal extends SimpleSlimefunItem<ItemUseHandler> {

	public InfernalBonemeal(Category category, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe, ItemStack recipeOutput) {
		super(category, item, recipeType, recipe, recipeOutput);
	}

	@Override
	public ItemUseHandler getItemHandler() {
		return e -> {
			Optional<Block> block = e.getClickedBlock();
			
			if (block.isPresent()) {
				Block b = block.get();
				
				if (b.getType() == Material.NETHER_WART) {
					Ageable ageable = (Ageable) b.getBlockData();
					
					if (ageable.getAge() < ageable.getMaximumAge()) {
						ageable.setAge(ageable.getMaximumAge());
						b.setBlockData(ageable);
						b.getWorld().playEffect(b.getLocation(), Effect.STEP_SOUND, Material.REDSTONE_BLOCK);
						
						if (e.getPlayer().getGameMode() != GameMode.CREATIVE) {
							ItemUtils.consumeItem(e.getItem(), false);
						}
					}
				}
			}
		};
	}

}