package com.xzavier0722.mc.plugin.slimefun4.storage.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectOutputStream;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class LegacyDataVersionReaderTest {
    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @SuppressWarnings("deprecation")
    void readsTopLevelItemStackDataVersion() throws Exception {
        var itemStack = new ItemStack(Material.DIAMOND_SWORD);
        var meta = itemStack.getItemMeta();
        meta.setDisplayName("名字");
        meta.setLore(List.of("lore"));
        itemStack.setItemMeta(meta);

        assertEquals(Bukkit.getUnsafe().getDataVersion(), LegacyDataVersionReader.read(serialize(itemStack)));
    }

    @Test
    void returnsUnknownForGarbageAndDisallowedClasses() throws Exception {
        assertEquals(LegacyDataVersionReader.UNKNOWN, LegacyDataVersionReader.read(new byte[] {1, 2, 3}));

        var bytes = new ByteArrayOutputStream();
        try (var output = new ObjectOutputStream(bytes)) {
            output.writeObject(new File("x"));
        }
        assertEquals(LegacyDataVersionReader.UNKNOWN, LegacyDataVersionReader.read(bytes.toByteArray()));
    }

    private static byte[] serialize(ItemStack itemStack) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var output = new BukkitObjectOutputStream(bytes)) {
            output.writeObject(itemStack);
        }
        return bytes.toByteArray();
    }
}
