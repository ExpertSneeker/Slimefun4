package me.mrCookieSlime.Slimefun.GEO;

import java.util.Collection;
import java.util.logging.Level;

import me.mrCookieSlime.Slimefun.SlimefunPlugin;
import me.mrCookieSlime.Slimefun.api.Slimefun;
import org.bukkit.Chunk;
import org.bukkit.block.Biome;

import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.Slimefun.api.BlockStorage;

public final class OreGenSystem {

    private OreGenSystem() {}


    public static Collection<OreGenResource> listResources() {
        return SlimefunPlugin.instance.getUtilities().resources.values();
    }

    public static void registerResource(OreGenResource resource) {
        SlimefunPlugin.instance.getUtilities().resources.put(resource.getName(), resource);
        Slimefun.getLogger().log(Level.INFO, "正在注册资源 " + resource.getName());

        Config cfg = new Config("plugins/Slimefun/generators/" + resource.getName() + ".cfg");
        for (Biome biome: Biome.values()) {
            cfg.setDefaultValue(biome.toString(), resource.getDefaultSupply(biome));
        }
        cfg.save();
    }

    public static OreGenResource getResource(String name) {
        return SlimefunPlugin.instance.getUtilities().resources.get(name);
    }

    private static int getDefault(OreGenResource resource, Biome biome) {
        if (resource == null) return 0;
        Config cfg = new Config("plugins/Slimefun/generators/" + resource.getName() + ".cfg");
        return cfg.getInt(biome.toString());
    }

    public static void setSupplies(OreGenResource resource, Chunk chunk, int amount) {
        if (resource == null) return;
        BlockStorage.setChunkInfo(chunk, "resources_" + resource.getName().toUpperCase(), String.valueOf(amount));
    }

    public static int generateSupplies(OreGenResource resource, Chunk chunk) {
        if (resource == null) return 0;
        int supplies = getDefault(resource, chunk.getBlock(5, 50, 5).getBiome());
        BlockStorage.setChunkInfo(chunk, "resources_" + resource.getName().toUpperCase(), String.valueOf(supplies));
        return supplies;
    }

    public static int getSupplies(OreGenResource resource, Chunk chunk, boolean generate) {
        if (resource == null) return 0;
        if (BlockStorage.hasChunkInfo(chunk, "resources_" + resource.getName().toUpperCase())) {
            return Integer.parseInt(BlockStorage.getChunkInfo(chunk, "resources_" + resource.getName().toUpperCase()));
        }
        else if (!generate) {
            return 0;
        }
        else {
            return generateSupplies(resource, chunk);
        }
    }

    public static boolean wasResourceGenerated(OreGenResource resource, Chunk chunk) {
        if (resource == null) return false;
        return BlockStorage.hasChunkInfo(chunk, "resources_" + resource.getName().toUpperCase());
    }

}