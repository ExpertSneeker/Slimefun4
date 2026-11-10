package com.xzavier0722.mc.plugin.slimefun4.storage.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LegacyBlockStateMetaFixerTest {
    private static final int OLD_VERSION = 3953;
    private static final int CURRENT_VERSION = 4189;

    @Test
    void nbtRoundTripPreservesEveryTagType() throws Exception {
        var root = new LinkedHashMap<String, LegacyNbt.Tag>();
        root.put("b", new LegacyNbt.Tag(LegacyNbt.TAG_BYTE, (byte) 1));
        root.put("s", new LegacyNbt.Tag(LegacyNbt.TAG_SHORT, (short) 2));
        root.put("i", LegacyNbt.intTag(3));
        root.put("l", new LegacyNbt.Tag(LegacyNbt.TAG_LONG, 4L));
        root.put("f", new LegacyNbt.Tag(LegacyNbt.TAG_FLOAT, 5.5f));
        root.put("d", new LegacyNbt.Tag(LegacyNbt.TAG_DOUBLE, 6.5d));
        root.put("ba", new LegacyNbt.Tag(LegacyNbt.TAG_BYTE_ARRAY, new byte[] {7, 8}));
        root.put("str", LegacyNbt.string("中文 text"));
        root.put(
                "list",
                new LegacyNbt.Tag(
                        LegacyNbt.TAG_LIST,
                        new LegacyNbt.NbtList(
                                LegacyNbt.TAG_STRING, List.of(LegacyNbt.string("a"), LegacyNbt.string("b")))));
        root.put("empty", new LegacyNbt.Tag(LegacyNbt.TAG_LIST, new LegacyNbt.NbtList(LegacyNbt.TAG_END, List.of())));
        root.put("ia", new LegacyNbt.Tag(LegacyNbt.TAG_INT_ARRAY, new int[] {9, 10}));
        root.put("la", new LegacyNbt.Tag(LegacyNbt.TAG_LONG_ARRAY, new long[] {11L}));
        root.put("c", LegacyNbt.compound(new LinkedHashMap<>(Map.of("x", LegacyNbt.intTag(12)))));

        var encoded = LegacyNbt.writeBase64(root);
        var decoded = LegacyNbt.readBase64(encoded);

        assertEquals(root.keySet(), decoded.keySet());
        assertArrayEquals(new byte[] {7, 8}, (byte[]) decoded.get("ba").value());
        assertArrayEquals(new int[] {9, 10}, (int[]) decoded.get("ia").value());
        assertEquals("中文 text", decoded.get("str").value());
        assertEquals(12, decoded.get("c").asCompound().get("x").value());
        assertEquals(encoded, LegacyNbt.writeBase64(decoded));
    }

    @Test
    void upgradesBlockEntityComponentsThroughVersionedItemPath() throws Exception {
        var meta = tileEntityMeta("SHULKER_BOX", internalWithContainer(2200291));
        var bridge = new StubBridge();

        var fixed = LegacyBlockStateMetaFixer.fix(meta, OLD_VERSION, bridge);

        assertNotSame(meta, fixed);
        assertEquals(1, bridge.upgradedItems.size());
        var sent = bridge.upgradedItems.getFirst();
        assertEquals("minecraft:shulker_box", sent.get("id").value());
        assertEquals(OLD_VERSION, sent.get("DataVersion").value());

        var internal = LegacyNbt.readBase64((String) fixed.get("internal"));
        assertTrue(internal.containsKey("keep"), "other internal tags are preserved");
        var nestedComponents = nestedItemComponents(internal);
        var customModelData = nestedComponents.get("minecraft:custom_model_data");
        assertEquals(LegacyNbt.TAG_COMPOUND, customModelData.type());
        assertEquals("TILE_ENTITY", fixed.get("meta-type"));
        assertEquals("SHULKER_BOX", fixed.get("blockMaterial"));
    }

    @Test
    void leavesBlockEntityComponentsAloneWhenVersionIsUnknownOrCurrent() throws Exception {
        var meta = tileEntityMeta("SHULKER_BOX", internalWithContainer(1));
        var bridge = new StubBridge();

        assertSame(meta, LegacyBlockStateMetaFixer.fix(meta, LegacyDataVersionReader.UNKNOWN, bridge));
        assertSame(meta, LegacyBlockStateMetaFixer.fix(meta, CURRENT_VERSION, bridge));
        assertTrue(bridge.upgradedItems.isEmpty());
    }

    @Test
    void ignoresNonTileEntityMeta() {
        var meta = new LinkedHashMap<String, Object>(Map.of("==", "ItemMeta", "meta-type", "UNSPECIFIC"));
        assertSame(meta, LegacyBlockStateMetaFixer.fix(meta, OLD_VERSION, new StubBridge()));
    }

    @Test
    void keepsOriginalMetaWhenUpgradeFails() throws Exception {
        var meta = tileEntityMeta("SHULKER_BOX", internalWithContainer(1));
        var bridge = new StubBridge();
        bridge.failUpgrade = true;

        assertSame(meta, LegacyBlockStateMetaFixer.fix(meta, OLD_VERSION, bridge));
    }

    @Test
    void convertsTileEntityShieldToShieldMeta() throws Exception {
        var pattern = new LinkedHashMap<String, LegacyNbt.Tag>();
        pattern.put("color", LegacyNbt.string("white"));
        pattern.put("pattern", LegacyNbt.string("minecraft:flow"));
        var blockEntityComponents = new LinkedHashMap<String, LegacyNbt.Tag>();
        blockEntityComponents.put(
                "minecraft:banner_patterns",
                new LegacyNbt.Tag(
                        LegacyNbt.TAG_LIST,
                        new LegacyNbt.NbtList(LegacyNbt.TAG_COMPOUND, List.of(LegacyNbt.compound(pattern)))));
        var internal = new LinkedHashMap<String, LegacyNbt.Tag>();
        internal.put("block-entity-components", LegacyNbt.compound(blockEntityComponents));
        var unhandled = new LinkedHashMap<String, LegacyNbt.Tag>();
        unhandled.put("minecraft:base_color", LegacyNbt.string("red"));
        unhandled.put("minecraft:rarity", LegacyNbt.string("epic"));

        var meta = tileEntityMeta("SHIELD", LegacyNbt.writeBase64(internal));
        meta.put("unhandled", LegacyNbt.writeBase64(unhandled));
        meta.put("display-name", "name");
        var bridge = new StubBridge();

        var fixed = LegacyBlockStateMetaFixer.fix(meta, OLD_VERSION, bridge);

        var sent = bridge.shieldItems.getFirst();
        assertEquals("minecraft:shield", sent.get("id").value());
        var sentComponents = sent.get("components").asCompound();
        assertTrue(sentComponents.containsKey("minecraft:banner_patterns"));
        assertEquals("red", sentComponents.get("minecraft:base_color").value());

        assertEquals("SHIELD", fixed.get("meta-type"));
        assertFalse(fixed.containsKey("blockMaterial"));
        assertFalse(fixed.containsKey("internal"));
        assertEquals("name", fixed.get("display-name"));
        assertEquals(List.of("pattern-object"), fixed.get("patterns"));
        assertEquals("RED", fixed.get("base-color"));
        var remainingUnhandled = LegacyNbt.readBase64((String) fixed.get("unhandled"));
        assertEquals(List.of("minecraft:rarity"), new ArrayList<>(remainingUnhandled.keySet()));
    }

    @Test
    void refusesShieldConversionThatLosesPatterns() throws Exception {
        var internal = new LinkedHashMap<String, LegacyNbt.Tag>();
        var blockEntityComponents = new LinkedHashMap<String, LegacyNbt.Tag>();
        blockEntityComponents.put(
                "minecraft:banner_patterns",
                new LegacyNbt.Tag(LegacyNbt.TAG_LIST, new LegacyNbt.NbtList(LegacyNbt.TAG_END, List.of())));
        internal.put("block-entity-components", LegacyNbt.compound(blockEntityComponents));
        var meta = tileEntityMeta("SHIELD", LegacyNbt.writeBase64(internal));
        var bridge = new StubBridge();
        bridge.shieldMeta = Map.of("meta-type", "SHIELD");

        assertSame(meta, LegacyBlockStateMetaFixer.fix(meta, OLD_VERSION, bridge));
    }

    @Test
    void upgradesLegacyBlockEntityTagContainerAndKeepsOtherInternalTags() throws Exception {
        var internal = legacyInternal(legacyContainerTag(2));
        var meta = tileEntityMeta("SHULKER_BOX", LegacyNbt.writeBase64(internal));
        var bridge = new StubBridge();
        bridge.shieldMeta = upgradedBlockMeta("SHULKER_BOX", 2);

        var fixed = LegacyBlockStateMetaFixer.fix(meta, 3465, bridge);

        var sent = bridge.shieldItems.getFirst();
        assertEquals("minecraft:shulker_box", sent.get("id").value());
        assertEquals(LegacyNbt.TAG_BYTE, sent.get("Count").type());
        assertEquals(3465, sent.get("DataVersion").value());
        assertTrue(sent.get("tag").asCompound().containsKey("BlockEntityTag"));

        var newInternal = LegacyNbt.readBase64((String) fixed.get("internal"));
        assertFalse(newInternal.containsKey("BlockEntityTag"));
        assertTrue(newInternal.containsKey("block-entity-components"));
        assertEquals("value", newInternal.get("ESGUI").value(), "other plugins' internal tags are preserved");
        assertEquals("TILE_ENTITY", fixed.get("meta-type"));
    }

    @Test
    void refusesLegacyBlockEntityTagUpgradeThatDropsItems() throws Exception {
        var meta = tileEntityMeta("SHULKER_BOX", LegacyNbt.writeBase64(legacyInternal(legacyContainerTag(3))));
        var bridge = new StubBridge();
        bridge.shieldMeta = upgradedBlockMeta("SHULKER_BOX", 2);

        assertSame(meta, LegacyBlockStateMetaFixer.fix(meta, 3465, bridge));
    }

    @Test
    void refusesLegacyBlockEntityTagUpgradeWithoutDataVersion() throws Exception {
        var meta = tileEntityMeta("SHULKER_BOX", LegacyNbt.writeBase64(legacyInternal(legacyContainerTag(1))));
        var bridge = new StubBridge();
        bridge.shieldMeta = upgradedBlockMeta("SHULKER_BOX", 1);

        assertSame(meta, LegacyBlockStateMetaFixer.fix(meta, LegacyDataVersionReader.UNKNOWN, bridge));
        assertTrue(bridge.shieldItems.isEmpty());
    }

    @Test
    void convertsLegacyBlockEntityTagShield() throws Exception {
        var pattern = new LinkedHashMap<String, LegacyNbt.Tag>();
        pattern.put("Pattern", LegacyNbt.string("mr"));
        pattern.put("Color", LegacyNbt.intTag(14));
        var tag = new LinkedHashMap<String, LegacyNbt.Tag>();
        tag.put("Base", LegacyNbt.intTag(0));
        tag.put(
                "Patterns",
                new LegacyNbt.Tag(
                        LegacyNbt.TAG_LIST,
                        new LegacyNbt.NbtList(LegacyNbt.TAG_COMPOUND, List.of(LegacyNbt.compound(pattern)))));
        var meta = tileEntityMeta("SHIELD", LegacyNbt.writeBase64(legacyInternal(tag)));
        var bridge = new StubBridge();

        var fixed = LegacyBlockStateMetaFixer.fix(meta, 3700, bridge);

        var sent = bridge.shieldItems.getFirst();
        assertEquals("minecraft:shield", sent.get("id").value());
        assertEquals(3700, sent.get("DataVersion").value());
        assertEquals("SHIELD", fixed.get("meta-type"));
        assertEquals(List.of("pattern-object"), fixed.get("patterns"));
        assertEquals("RED", fixed.get("base-color"));
        var remaining = LegacyNbt.readBase64((String) fixed.get("internal"));
        assertEquals(List.of("ESGUI"), new ArrayList<>(remaining.keySet()));
    }

    private static Map<String, LegacyNbt.Tag> legacyInternal(Map<String, LegacyNbt.Tag> blockEntityTag) {
        var internal = new LinkedHashMap<String, LegacyNbt.Tag>();
        internal.put("BlockEntityTag", LegacyNbt.compound(blockEntityTag));
        internal.put("ESGUI", LegacyNbt.string("value"));
        return internal;
    }

    private static Map<String, LegacyNbt.Tag> legacyContainerTag(int items) {
        var list = new ArrayList<LegacyNbt.Tag>();
        for (var i = 0; i < items; i++) {
            var item = new LinkedHashMap<String, LegacyNbt.Tag>();
            item.put("Slot", new LegacyNbt.Tag(LegacyNbt.TAG_BYTE, (byte) i));
            item.put("id", LegacyNbt.string("minecraft:diamond"));
            item.put("Count", new LegacyNbt.Tag(LegacyNbt.TAG_BYTE, (byte) 64));
            list.add(LegacyNbt.compound(item));
        }
        var tag = new LinkedHashMap<String, LegacyNbt.Tag>();
        tag.put("id", LegacyNbt.string("minecraft:shulker_box"));
        tag.put("Items", new LegacyNbt.Tag(LegacyNbt.TAG_LIST, new LegacyNbt.NbtList(LegacyNbt.TAG_COMPOUND, list)));
        return tag;
    }

    private static Map<String, Object> upgradedBlockMeta(String material, int containerEntries) throws Exception {
        var entries = new ArrayList<LegacyNbt.Tag>();
        for (var i = 0; i < containerEntries; i++) {
            var item = new LinkedHashMap<String, LegacyNbt.Tag>();
            item.put("id", LegacyNbt.string("minecraft:diamond"));
            item.put("count", LegacyNbt.intTag(64));
            var slot = new LinkedHashMap<String, LegacyNbt.Tag>();
            slot.put("slot", LegacyNbt.intTag(i));
            slot.put("item", LegacyNbt.compound(item));
            entries.add(LegacyNbt.compound(slot));
        }
        var components = new LinkedHashMap<String, LegacyNbt.Tag>();
        components.put(
                "minecraft:container",
                new LegacyNbt.Tag(LegacyNbt.TAG_LIST, new LegacyNbt.NbtList(LegacyNbt.TAG_COMPOUND, entries)));
        var internal = new LinkedHashMap<String, LegacyNbt.Tag>();
        internal.put("block-entity-components", LegacyNbt.compound(components));
        var meta = new LinkedHashMap<String, Object>();
        meta.put("==", "ItemMeta");
        meta.put("meta-type", "TILE_ENTITY");
        meta.put("blockMaterial", material);
        meta.put("internal", LegacyNbt.writeBase64(internal));
        return meta;
    }

    private static LinkedHashMap<String, Object> tileEntityMeta(String material, String internal) {
        var meta = new LinkedHashMap<String, Object>();
        meta.put("==", "ItemMeta");
        meta.put("meta-type", "TILE_ENTITY");
        meta.put("blockMaterial", material);
        meta.put("internal", internal);
        return meta;
    }

    private static String internalWithContainer(int legacyCustomModelData) throws Exception {
        var nestedComponents = new LinkedHashMap<String, LegacyNbt.Tag>();
        nestedComponents.put("minecraft:custom_model_data", LegacyNbt.intTag(legacyCustomModelData));
        var nestedItem = new LinkedHashMap<String, LegacyNbt.Tag>();
        nestedItem.put("id", LegacyNbt.string("minecraft:paper"));
        nestedItem.put("count", LegacyNbt.intTag(3));
        nestedItem.put("components", LegacyNbt.compound(nestedComponents));
        var slot = new LinkedHashMap<String, LegacyNbt.Tag>();
        slot.put("slot", LegacyNbt.intTag(0));
        slot.put("item", LegacyNbt.compound(nestedItem));
        var blockEntityComponents = new LinkedHashMap<String, LegacyNbt.Tag>();
        blockEntityComponents.put(
                "minecraft:container",
                new LegacyNbt.Tag(
                        LegacyNbt.TAG_LIST,
                        new LegacyNbt.NbtList(LegacyNbt.TAG_COMPOUND, List.of(LegacyNbt.compound(slot)))));
        var internal = new LinkedHashMap<String, LegacyNbt.Tag>();
        internal.put("block-entity-components", LegacyNbt.compound(blockEntityComponents));
        internal.put("keep", LegacyNbt.string("value"));
        return LegacyNbt.writeBase64(internal);
    }

    private static Map<String, LegacyNbt.Tag> nestedItemComponents(Map<String, LegacyNbt.Tag> internal) {
        var container = (LegacyNbt.NbtList) internal.get("block-entity-components")
                .asCompound()
                .get("minecraft:container")
                .value();
        return container
                .elements()
                .getFirst()
                .asCompound()
                .get("item")
                .asCompound()
                .get("components")
                .asCompound();
    }

    /** Mimics Paper: upgrades int custom_model_data to the 1.21.4 compound form. */
    private static final class StubBridge implements LegacyBlockStateMetaFixer.ItemBridge {
        private final List<Map<String, LegacyNbt.Tag>> upgradedItems = new ArrayList<>();
        private final List<Map<String, LegacyNbt.Tag>> shieldItems = new ArrayList<>();
        private boolean failUpgrade;
        private Map<String, Object> shieldMeta =
                Map.of("meta-type", "SHIELD", "patterns", List.of("pattern-object"), "base-color", "RED");

        @Override
        public int currentDataVersion() {
            return CURRENT_VERSION;
        }

        @Override
        public String itemKey(String materialName) {
            return "minecraft:" + materialName.toLowerCase(java.util.Locale.ROOT);
        }

        @Override
        public Map<String, LegacyNbt.Tag> upgradeItem(Map<String, LegacyNbt.Tag> item) throws Exception {
            upgradedItems.add(item);
            if (failUpgrade) {
                throw new IllegalStateException("stub failure");
            }
            var copy = LegacyNbt.readCompressed(LegacyNbt.writeCompressed(item));
            var container = (LegacyNbt.NbtList) copy.get("components")
                    .asCompound()
                    .get("minecraft:container")
                    .value();
            for (var slot : container.elements()) {
                var components = slot.asCompound()
                        .get("item")
                        .asCompound()
                        .get("components")
                        .asCompound();
                var legacy = components.get("minecraft:custom_model_data");
                if (legacy != null && legacy.type() == LegacyNbt.TAG_INT) {
                    var floats = new LegacyNbt.NbtList(
                            LegacyNbt.TAG_FLOAT,
                            List.of(new LegacyNbt.Tag(LegacyNbt.TAG_FLOAT, ((Integer) legacy.value()).floatValue())));
                    var upgraded = new LinkedHashMap<String, LegacyNbt.Tag>();
                    upgraded.put("floats", new LegacyNbt.Tag(LegacyNbt.TAG_LIST, floats));
                    components.put("minecraft:custom_model_data", LegacyNbt.compound(upgraded));
                }
            }
            copy.put("DataVersion", LegacyNbt.intTag(CURRENT_VERSION));
            return copy;
        }

        @Override
        public Map<String, Object> serializeItemMeta(Map<String, LegacyNbt.Tag> item) {
            shieldItems.add(item);
            return shieldMeta;
        }
    }
}
