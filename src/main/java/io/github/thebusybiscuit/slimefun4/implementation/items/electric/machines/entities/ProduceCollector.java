package io.github.thebusybiscuit.slimefun4.implementation.items.electric.machines.entities;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import io.github.bakedlibs.dough.inventory.InvUtils;
import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.api.MinecraftVersion;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.ItemSetting;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.items.settings.IntRangeSetting;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.RecipeDisplayItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AContainer;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.apache.commons.lang.Validate;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;

/**
 * The {@link ProduceCollector} allows you to collect produce from animals.
 * Providing it with a bucket and a nearby {@link Cow} will allow you to obtain milk.
 *
 * @author TheBusyBiscuit
 * @author Walshy
 *
 */
public class ProduceCollector extends AContainer implements RecipeDisplayItem {

    private final ItemSetting<Integer> range = new IntRangeSetting(this, "range", 1, 2, 32);
    private final Set<AnimalProduce> animalProduces = new HashSet<>();

    @ParametersAreNonnullByDefault
    public ProduceCollector(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);

        addItemSetting(range);
    }

    @Override
    protected void registerDefaultRecipes() {
        // Milk from adult cows and goats
        addProduce(new AnimalProduce(new ItemStack(Material.BUCKET), new ItemStack(Material.MILK_BUCKET), n -> {
            MinecraftVersion version = Slimefun.getMinecraftVersion();

            if (n instanceof Cow || (version.isAtLeast(MinecraftVersion.MINECRAFT_1_17) && n instanceof Goat)) {
                return ((Ageable) n).isAdult();
            } else {
                return false;
            }
        }));

        // Mushroom Stew from Mooshrooms
        addProduce(new AnimalProduce(new ItemStack(Material.BOWL), new ItemStack(Material.MUSHROOM_STEW), n -> {
            if (n instanceof MushroomCow mushroomCow) {
                return mushroomCow.isAdult();
            } else {
                return false;
            }
        }));

        // 铁傀儡产电路板
        ItemStack ironBlock = new ItemStack(Material.IRON_BLOCK);
        ironBlock.setAmount(5);
        addProduce(new AnimalProduce(ironBlock, new ItemStack(SlimefunItems.BASIC_CIRCUIT_BOARD), n -> {
            if (n instanceof IronGolem ironGolem) {
                return !ironGolem.isDead();
            } else {
                return false;
            }
        }));

        // 小麦 牛肉 & 羊肉
        ItemStack wheat = new ItemStack(Material.WHEAT);
        wheat.setAmount(10);
        addProduce(new AnimalProduce(wheat, new ItemStack(Material.BEEF), n -> {
            if (n instanceof Cow cow) {
                return cow.isAdult();
            } else {
                return false;
            }
        }));
        addProduce(new AnimalProduce(wheat, new ItemStack(Material.MUTTON), n -> {
            if (n instanceof Sheep sheep) {
                return sheep.isAdult();
            } else {
                return false;
            }
        }));
        // 小麦种子 鸡肉
        ItemStack wheatSeeds = new ItemStack(Material.WHEAT_SEEDS);
        wheatSeeds.setAmount(10);
        addProduce(new AnimalProduce(wheatSeeds, new ItemStack(Material.CHICKEN), n -> {
            if (n instanceof Chicken chicken) {
                return chicken.isAdult();
            } else {
                return false;
            }
        }));
        // 甜菜根 猪肉
        ItemStack beetrootPig = new ItemStack(Material.BEETROOT);
        beetrootPig.setAmount(10);
        addProduce(new AnimalProduce(beetrootPig, new ItemStack(Material.PORKCHOP), n -> {
            if (n instanceof Pig pig) {
                return pig.isAdult();
            } else {
                return false;
            }
        }));
        // 蒲公英 兔肉
        ItemStack dandelion = new ItemStack(Material.DANDELION);
        dandelion.setAmount(10);
        addProduce(new AnimalProduce(dandelion, new ItemStack(Material.RABBIT), n -> {
            if (n instanceof Rabbit rabbit) {
                return rabbit.isAdult();
            } else {
                return false;
            }
        }));
        // 胡萝卜 兔腿
        ItemStack carrot = new ItemStack(Material.CARROT);
        carrot.setAmount(10);
        addProduce(new AnimalProduce(carrot, new ItemStack(Material.RABBIT_FOOT), n -> {
            if (n instanceof Rabbit rabbit) {
                return rabbit.isAdult();
            } else {
                return false;
            }
        }));
        // 皮革 兔子皮
        ItemStack leather = new ItemStack(Material.LEATHER);
        leather.setAmount(4);
        addProduce(new AnimalProduce(leather, new ItemStack(Material.RABBIT_HIDE), n -> {
            if (n instanceof Rabbit rabbit) {
                return rabbit.isAdult();
            } else {
                return false;
            }
        }));
        // 海草 鳞甲
        ItemStack seaGrass = new ItemStack(Material.SEAGRASS);
        seaGrass.setAmount(10);
        addProduce(new AnimalProduce(seaGrass, new ItemStack(Material.SCUTE), n -> {
            if (n instanceof Turtle turtle) {
                return turtle.isAdult();
            } else {
                return false;
            }
        }));
        // 腐肉 皮革
        ItemStack rottenFlesh = new ItemStack(Material.ROTTEN_FLESH);
        rottenFlesh.setAmount(4);
        addProduce(new AnimalProduce(rottenFlesh, new ItemStack(Material.LEATHER), n -> {
            if (n instanceof Horse horse) {
                return horse.isAdult();
            } else {
                return false;
            }
        }));
        // 糖 鸡蛋
        ItemStack sugar = new ItemStack(Material.SUGAR);
        sugar.setAmount(10);
        addProduce(new AnimalProduce(sugar, new ItemStack(Material.EGG), n -> {
            if (n instanceof Chicken chicken) {
                return chicken.isAdult();
            } else {
                return false;
            }
        }));
        // 线 羽毛
        ItemStack string = new ItemStack(Material.STRING);
        string.setAmount(5);
        addProduce(new AnimalProduce(string, new ItemStack(Material.FEATHER), n -> {
            if (n instanceof Chicken chicken) {
                return chicken.isAdult();
            } else {
                return false;
            }
        }));
        // 燧石 蜜脾
        ItemStack flint = new ItemStack(Material.FLINT);
        flint.setAmount(2);
        addProduce(new AnimalProduce(flint, new ItemStack(Material.HONEYCOMB), n -> {
            if (n instanceof Bee bee) {
                return bee.isAdult();
            } else {
                return false;
            }
        }));
        // 玻璃瓶 蜂蜜瓶
        ItemStack glassBottle = new ItemStack(Material.GLASS_BOTTLE);
        glassBottle.setAmount(1);
        addProduce(new AnimalProduce(glassBottle, new ItemStack(Material.HONEY_BOTTLE), n -> {
            if (n instanceof Bee bee) {
                return bee.isAdult();
            } else {
                return false;
            }
        }));
        // 燧石 腐肉
        ItemStack flintZombie = new ItemStack(Material.FLINT);
        flintZombie.setAmount(1);
        addProduce(new AnimalProduce(flintZombie, new ItemStack(Material.ROTTEN_FLESH), n -> {
            if (n instanceof Zombie zombie) {
                return zombie.isAdult();
            } else {
                return false;
            }
        }));
        // 鸡蛋 鸡肉
        ItemStack egg = new ItemStack(Material.EGG);
        egg.setAmount(1);
        addProduce(new AnimalProduce(egg, new ItemStack(Material.CHICKEN), n -> {
            if (n instanceof Chicken chicken) {
                return chicken.isAdult();
            } else {
                return false;
            }
        }));
    }

    /**
     * This method adds a new {@link AnimalProduce} to this machine.
     *
     * @param produce
     *            The {@link AnimalProduce} to add
     */
    public void addProduce(@Nonnull AnimalProduce produce) {
        Validate.notNull(produce, "A produce cannot be null");

        this.animalProduces.add(produce);
    }

    @Override
    public void preRegister() {
        addItemHandler(new BlockTicker() {

            @Override
            public void tick(Block b, SlimefunItem sf, SlimefunBlockData data) {
                ProduceCollector.this.tick(b);
            }

            @Override
            public boolean isSynchronized() {
                // We override the preRegister() method to override the sync setting here
                return true;
            }
        });
    }

    @Override
    public @Nonnull List<ItemStack> getDisplayRecipes() {
        List<ItemStack> displayRecipes = new ArrayList<>();

        displayRecipes.add(new CustomItemStack(Material.BUCKET, null, "&f需要 &b牛 &f在附近"));
        displayRecipes.add(new ItemStack(Material.MILK_BUCKET));

        if (Slimefun.getMinecraftVersion().isAtLeast(MinecraftVersion.MINECRAFT_1_17)) {
            displayRecipes.add(new CustomItemStack(Material.BUCKET, null, "&f需要 &b山羊 &f在附近"));
            displayRecipes.add(new ItemStack(Material.MILK_BUCKET));
        }

        displayRecipes.add(new CustomItemStack(Material.BOWL, null, "&f需要 &b哞菇 &f在附近"));
        displayRecipes.add(new ItemStack(Material.MUSHROOM_STEW));

        ItemStack ironBlock = new CustomItemStack(Material.IRON_BLOCK, null, "&f需要 &b铁傀儡 &f在附近");
        ironBlock.setAmount(5);
        displayRecipes.add(ironBlock);
        displayRecipes.add(new ItemStack(SlimefunItems.BASIC_CIRCUIT_BOARD));

        ItemStack wheatCow = new CustomItemStack(Material.WHEAT, null, "&f需要 &b牛 &f在附近");
        wheatCow.setAmount(10);
        displayRecipes.add(wheatCow);
        displayRecipes.add(new ItemStack(Material.BEEF));

        ItemStack wheatSheep = new CustomItemStack(Material.WHEAT, null, "&f需要 &b绵羊 &f在附近");
        wheatSheep.setAmount(10);
        displayRecipes.add(wheatSheep);
        displayRecipes.add(new ItemStack(Material.MUTTON));

        ItemStack wheatSeedsChicken = new CustomItemStack(Material.WHEAT_SEEDS, null, "&f需要 &b鸡 &f在附近");
        wheatSeedsChicken.setAmount(10);
        displayRecipes.add(wheatSeedsChicken);
        displayRecipes.add(new ItemStack(Material.CHICKEN));

        ItemStack carrotPig = new CustomItemStack(Material.CARROT, null, "&f需要 &b猪 &f在附近");
        carrotPig.setAmount(10);
        displayRecipes.add(carrotPig);
        displayRecipes.add(new ItemStack(Material.PORKCHOP));

        ItemStack dandelion = new CustomItemStack(Material.DANDELION, null, "&f需要 &b兔子 &f在附近");
        dandelion.setAmount(10);
        displayRecipes.add(dandelion);
        displayRecipes.add(new ItemStack(Material.RABBIT));

        ItemStack carrot = new CustomItemStack(Material.CARROT, null, "&f需要 &b兔子 &f在附近");
        carrot.setAmount(10);
        displayRecipes.add(carrot);
        displayRecipes.add(new ItemStack(Material.RABBIT_FOOT));

        ItemStack leather = new CustomItemStack(Material.LEATHER, null, "&f需要 &b兔子 &f在附近");
        leather.setAmount(4);
        displayRecipes.add(leather);
        displayRecipes.add(new ItemStack(Material.RABBIT_HIDE));

        ItemStack seaGrass = new CustomItemStack(Material.SEAGRASS, null, "&f需要 &b海龟 &f在附近");
        seaGrass.setAmount(10);
        displayRecipes.add(seaGrass);
        displayRecipes.add(new ItemStack(Material.SCUTE));

        ItemStack rottenFlesh = new CustomItemStack(Material.ROTTEN_FLESH, null, "&f需要 &b马 &f在附近");
        rottenFlesh.setAmount(4);
        displayRecipes.add(rottenFlesh);
        displayRecipes.add(new ItemStack(Material.LEATHER));

        ItemStack sugar = new CustomItemStack(Material.SUGAR, null, "&f需要 &b鸡 &f在附近");
        sugar.setAmount(10);
        displayRecipes.add(sugar);
        displayRecipes.add(new ItemStack(Material.EGG));

        ItemStack string = new CustomItemStack(Material.STRING, null, "&f需要 &b鸡 &f在附近");
        string.setAmount(5);
        displayRecipes.add(string);
        displayRecipes.add(new ItemStack(Material.FEATHER));

        ItemStack flint = new CustomItemStack(Material.FLINT, null, "&f需要 &b蜜蜂 &f在附近");
        flint.setAmount(2);
        displayRecipes.add(flint);
        displayRecipes.add(new ItemStack(Material.HONEYCOMB));

        ItemStack glassBottle = new CustomItemStack(Material.GLASS_BOTTLE, null, "&f需要 &b蜜蜂 &f在附近");
        glassBottle.setAmount(1);
        displayRecipes.add(glassBottle);
        displayRecipes.add(new ItemStack(Material.HONEY_BOTTLE));

        ItemStack flintZombie = new CustomItemStack(Material.FLINT, null, "&f需要 &b僵尸 &f在附近");
        flintZombie.setAmount(1);
        displayRecipes.add(flintZombie);
        displayRecipes.add(new ItemStack(Material.ROTTEN_FLESH));

        ItemStack egg = new CustomItemStack(Material.EGG, null, "&f需要 &b鸡 &f在附近");
        egg.setAmount(1);
        displayRecipes.add(egg);
        displayRecipes.add(new ItemStack(Material.CHICKEN));

        return displayRecipes;
    }

    @Override
    protected @Nullable MachineRecipe findNextRecipe(@Nonnull BlockMenu inv) {
        for (int slot : getInputSlots()) {
            for (AnimalProduce produce : animalProduces) {
                ItemStack item = inv.getItemInSlot(slot);

                if (!SlimefunUtils.isItemSimilar(item, produce.getInput()[0], true, true)
                        || !InvUtils.fits(inv.toInventory(), produce.getOutput()[0], getOutputSlots())) {
                    continue;
                }

                if (isAnimalNearby(inv.getBlock(), produce)) {
                    inv.consumeItem(slot, produce.getInput()[0].getAmount());
                    return produce;
                }
            }
        }

        return null;
    }

    @ParametersAreNonnullByDefault
    private boolean isAnimalNearby(Block b, Predicate<LivingEntity> predicate) {
        int radius = range.getValue();
        return !b.getWorld()
                .getNearbyEntities(b.getLocation(), radius, radius, radius, n -> isValidAnimal(n, predicate))
                .isEmpty();
    }

    @ParametersAreNonnullByDefault
    private boolean isValidAnimal(Entity n, Predicate<LivingEntity> predicate) {
        if (n instanceof LivingEntity livingEntity) {
            return predicate.test(livingEntity);
        } else {
            return false;
        }
    }

    @Override
    public @Nonnull String getMachineIdentifier() {
        return "PRODUCE_COLLECTOR";
    }

    @Override
    public @Nonnull ItemStack getProgressBar() {
        return new ItemStack(Material.SHEARS);
    }
}
