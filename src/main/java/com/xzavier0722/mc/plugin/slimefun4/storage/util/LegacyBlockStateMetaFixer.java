package com.xzavier0722.mc.plugin.slimefun4.storage.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Repairs legacy {@code TILE_ENTITY} ItemMeta maps before Paper deserializes them.
 *
 * <p>Paper keeps the block entity data of block items (shulker box contents, spawner data, bees,
 * shield patterns) as raw NBT inside the {@code internal} field of the serialized meta and parses it with
 * the current codecs, without running the data fixers:
 *
 * <ul>
 *   <li>{@code BlockEntityTag} (saved before 1.20.5): nested items in the old {@code {id, Count, tag}}
 *       format silently lose their stack size, names, enchantments and plugin data; shield patterns are
 *       dropped.
 *   <li>{@code block-entity-components} (1.20.5+): nested items fail to load once a component format
 *       changes, e.g. {@code minecraft:custom_model_data} became a compound in 1.21.4.
 *   <li>Shields saved as {@code TILE_ENTITY} meta make Paper 1.21.4 throw while converting them.
 * </ul>
 *
 * <p>All three are handled by building an item compound carrying the DataVersion recorded in the legacy
 * ItemStack and loading it through Paper's regular versioned item path ({@link
 * ItemStack#deserializeBytes(byte[])}), which runs the official data converters. The upgraded block
 * entity data is then put back into the meta map. Conversions that would drop items, bees or patterns are
 * rejected and the original data is kept.
 */
final class LegacyBlockStateMetaFixer {
    static final String META_TYPE = "meta-type";
    static final String TILE_ENTITY = "TILE_ENTITY";
    static final String SHIELD = "SHIELD";
    static final String BLOCK_MATERIAL = "blockMaterial";
    static final String INTERNAL = "internal";
    static final String UNHANDLED = "unhandled";
    static final String LEGACY_BLOCK_ENTITY_TAG = "BlockEntityTag";
    static final String BLOCK_ENTITY_COMPONENTS = "block-entity-components";
    static final String BLOCK_ENTITY_TAG = "block-entity-tag";
    static final String CONTAINER = "minecraft:container";
    static final String BEES = "minecraft:bees";
    static final String BANNER_PATTERNS = "minecraft:banner_patterns";
    static final String BASE_COLOR = "minecraft:base_color";
    static final String SHIELD_PATTERNS_KEY = "patterns";
    static final String SHIELD_BASE_COLOR_KEY = "base-color";

    private static final Set<String> EXPECTED_BLOCK_META_KEYS = Set.of("==", META_TYPE, BLOCK_MATERIAL, INTERNAL);
    private static final Logger LOGGER = Logger.getLogger(LegacyBlockStateMetaFixer.class.getName());

    private LegacyBlockStateMetaFixer() {}

    /** Server operations used by the fixer; replaced in unit tests. */
    interface ItemBridge {
        int currentDataVersion();

        /** @return the namespaced item id for a Bukkit material name, or {@code null} if it is not an item */
        String itemKey(String materialName);

        /** Upgrades a full item compound (with {@code DataVersion}) and returns the re-serialized compound. */
        Map<String, LegacyNbt.Tag> upgradeItem(Map<String, LegacyNbt.Tag> item) throws Exception;

        /** Upgrades a full item compound (with {@code DataVersion}) and returns its serialized ItemMeta map. */
        Map<String, Object> serializeItemMeta(Map<String, LegacyNbt.Tag> item) throws Exception;
    }

    static final ItemBridge SERVER = new ItemBridge() {
        @Override
        @SuppressWarnings("deprecation")
        public int currentDataVersion() {
            return Bukkit.getUnsafe().getDataVersion();
        }

        @Override
        public String itemKey(String materialName) {
            var material = Material.getMaterial(materialName);
            return material == null || !material.isItem()
                    ? null
                    : material.getKey().toString();
        }

        @Override
        public Map<String, LegacyNbt.Tag> upgradeItem(Map<String, LegacyNbt.Tag> item) throws Exception {
            var itemStack = ItemStack.deserializeBytes(LegacyNbt.writeCompressed(item));
            return LegacyNbt.readCompressed(itemStack.serializeAsBytes());
        }

        @Override
        public Map<String, Object> serializeItemMeta(Map<String, LegacyNbt.Tag> item) throws Exception {
            var itemMeta =
                    ItemStack.deserializeBytes(LegacyNbt.writeCompressed(item)).getItemMeta();
            if (itemMeta == null) {
                throw new IllegalStateException("Paper returned no ItemMeta for " + item.get("id"));
            }
            return new LinkedHashMap<>(itemMeta.serialize());
        }
    };

    static Map<String, Object> fix(Map<String, Object> serializedMeta, int dataVersion) {
        return fix(serializedMeta, dataVersion, SERVER);
    }

    static Map<String, Object> fix(Map<String, Object> serializedMeta, int dataVersion, ItemBridge bridge) {
        if (!TILE_ENTITY.equals(serializedMeta.get(META_TYPE))
                || !(serializedMeta.get(BLOCK_MATERIAL) instanceof String material)) {
            return serializedMeta;
        }
        try {
            if (SHIELD.equals(material)) {
                return convertShield(serializedMeta, dataVersion, bridge);
            }
            if (!(serializedMeta.get(INTERNAL) instanceof String internal)) {
                return serializedMeta;
            }
            var internalRoot = LegacyNbt.readBase64(internal);
            var legacyTag = internalRoot.get(LEGACY_BLOCK_ENTITY_TAG);
            if (legacyTag != null && legacyTag.type() == LegacyNbt.TAG_COMPOUND) {
                return upgradeLegacyBlockEntityTag(serializedMeta, internalRoot, material, dataVersion, bridge);
            }
            return upgradeBlockEntityComponents(serializedMeta, internalRoot, material, dataVersion, bridge);
        } catch (Exception | StackOverflowError e) {
            LOGGER.log(
                    Level.WARNING,
                    "无法修复旧版方块物品元数据 (blockMaterial=" + material + ", DataVersion=" + dataVersion + ")，按原数据继续",
                    e);
            return serializedMeta;
        }
    }

    /** Pre-1.20.5 {@code BlockEntityTag}: let the data converters turn it into block entity components. */
    private static Map<String, Object> upgradeLegacyBlockEntityTag(
            Map<String, Object> serializedMeta,
            Map<String, LegacyNbt.Tag> internalRoot,
            String material,
            int dataVersion,
            ItemBridge bridge)
            throws Exception {
        if (dataVersion <= 0) {
            throw new IllegalStateException("Unknown DataVersion for a legacy BlockEntityTag");
        }
        var itemKey = bridge.itemKey(material);
        if (itemKey == null) {
            return serializedMeta;
        }
        var legacyTag = internalRoot.get(LEGACY_BLOCK_ENTITY_TAG).asCompound();
        var upgradedMeta = bridge.serializeItemMeta(legacyItemCompound(itemKey, legacyTag, dataVersion));
        for (var key : upgradedMeta.keySet()) {
            if (!EXPECTED_BLOCK_META_KEYS.contains(key)) {
                throw new IllegalStateException("Upgraded block entity data produced unexpected meta key: " + key);
            }
        }

        var upgradedInternal = upgradedMeta.get(INTERNAL) instanceof String encoded
                ? LegacyNbt.readBase64(encoded)
                : Map.<String, LegacyNbt.Tag>of();
        var components = upgradedInternal.get(BLOCK_ENTITY_COMPONENTS);
        var componentMap = components == null ? Map.<String, LegacyNbt.Tag>of() : components.asCompound();
        requireSameSize(legacyTag, "Items", componentMap, CONTAINER);
        requireSameSize(legacyTag, "Bees", componentMap, BEES);

        var newRoot = new LinkedHashMap<>(internalRoot);
        newRoot.remove(LEGACY_BLOCK_ENTITY_TAG);
        for (var key : List.of(BLOCK_ENTITY_COMPONENTS, BLOCK_ENTITY_TAG)) {
            var value = upgradedInternal.get(key);
            if (value != null) {
                newRoot.put(key, value);
            }
        }
        var fixed = new LinkedHashMap<>(serializedMeta);
        if (newRoot.isEmpty()) {
            fixed.remove(INTERNAL);
        } else {
            fixed.put(INTERNAL, LegacyNbt.writeBase64(newRoot));
        }
        return fixed;
    }

    /** 1.20.5+ {@code block-entity-components}: run the data converters over the nested components. */
    private static Map<String, Object> upgradeBlockEntityComponents(
            Map<String, Object> serializedMeta,
            Map<String, LegacyNbt.Tag> internalRoot,
            String material,
            int dataVersion,
            ItemBridge bridge)
            throws Exception {
        if (dataVersion <= 0 || dataVersion >= bridge.currentDataVersion()) {
            return serializedMeta;
        }
        var components = internalRoot.get(BLOCK_ENTITY_COMPONENTS);
        if (components == null || components.type() != LegacyNbt.TAG_COMPOUND) {
            return serializedMeta;
        }
        var itemKey = bridge.itemKey(material);
        if (itemKey == null) {
            return serializedMeta;
        }

        var upgraded = bridge.upgradeItem(itemCompound(itemKey, components.asCompound(), dataVersion));
        var upgradedComponents = upgraded.get("components");
        var newComponents = upgradedComponents == null
                ? new LinkedHashMap<String, LegacyNbt.Tag>()
                : new LinkedHashMap<>(upgradedComponents.asCompound());
        for (var key : newComponents.keySet()) {
            if (key.startsWith("!")) {
                throw new IllegalStateException("Upgraded block entity components contain a removal: " + key);
            }
        }
        requireSameListSize(components.asCompound(), newComponents, CONTAINER);
        requireSameListSize(components.asCompound(), newComponents, BEES);

        var newRoot = new LinkedHashMap<>(internalRoot);
        newRoot.put(BLOCK_ENTITY_COMPONENTS, LegacyNbt.compound(newComponents));
        var fixed = new LinkedHashMap<>(serializedMeta);
        fixed.put(INTERNAL, LegacyNbt.writeBase64(newRoot));
        return fixed;
    }

    private static Map<String, Object> convertShield(
            Map<String, Object> serializedMeta, int dataVersion, ItemBridge bridge) throws Exception {
        var shieldComponents = new LinkedHashMap<String, LegacyNbt.Tag>();
        Map<String, LegacyNbt.Tag> legacyTag = null;
        var fixed = new LinkedHashMap<>(serializedMeta);

        if (serializedMeta.get(INTERNAL) instanceof String internal) {
            var internalRoot = LegacyNbt.readBase64(internal);
            var blockEntityComponents = internalRoot.remove(BLOCK_ENTITY_COMPONENTS);
            if (blockEntityComponents != null) {
                for (var entry : blockEntityComponents.asCompound().entrySet()) {
                    if (!entry.getKey().equals(BANNER_PATTERNS)) {
                        throw new IllegalStateException("Unexpected shield block entity component: " + entry.getKey());
                    }
                    shieldComponents.put(entry.getKey(), entry.getValue());
                }
            }
            var legacy = internalRoot.remove(LEGACY_BLOCK_ENTITY_TAG);
            if (legacy != null) {
                legacyTag = legacy.asCompound();
            }
            if (internalRoot.containsKey(BLOCK_ENTITY_TAG)) {
                throw new IllegalStateException("Unexpected block entity data on a shield");
            }
            if (internalRoot.isEmpty()) {
                fixed.remove(INTERNAL);
            } else {
                fixed.put(INTERNAL, LegacyNbt.writeBase64(internalRoot));
            }
        }

        if (serializedMeta.get(UNHANDLED) instanceof String unhandled) {
            var unhandledRoot = LegacyNbt.readBase64(unhandled);
            var baseColor = unhandledRoot.remove(BASE_COLOR);
            if (baseColor != null) {
                shieldComponents.put(BASE_COLOR, baseColor);
            }
            if (unhandledRoot.isEmpty()) {
                fixed.remove(UNHANDLED);
            } else {
                fixed.put(UNHANDLED, LegacyNbt.writeBase64(unhandledRoot));
            }
        }

        Map<String, LegacyNbt.Tag> item;
        boolean expectsPatterns;
        boolean expectsBaseColor;
        if (legacyTag != null) {
            if (!shieldComponents.isEmpty()) {
                throw new IllegalStateException("Shield has both legacy and component banner data");
            }
            if (dataVersion <= 0) {
                throw new IllegalStateException("Unknown DataVersion for a legacy shield BlockEntityTag");
            }
            item = legacyItemCompound("minecraft:shield", legacyTag, dataVersion);
            expectsPatterns = legacyTag.get("Patterns") != null && listSize(legacyTag.get("Patterns")) > 0;
            expectsBaseColor = legacyTag.containsKey("Base");
        } else {
            var version = dataVersion > 0 ? dataVersion : bridge.currentDataVersion();
            item = itemCompound("minecraft:shield", shieldComponents, version);
            expectsPatterns = shieldComponents.containsKey(BANNER_PATTERNS);
            expectsBaseColor = shieldComponents.containsKey(BASE_COLOR);
        }

        var shieldMeta = bridge.serializeItemMeta(item);
        if (!SHIELD.equals(shieldMeta.get(META_TYPE))) {
            throw new IllegalStateException("Paper did not create ShieldMeta for a shield");
        }
        if ((expectsPatterns && !shieldMeta.containsKey(SHIELD_PATTERNS_KEY))
                || (expectsBaseColor && !shieldMeta.containsKey(SHIELD_BASE_COLOR_KEY))) {
            throw new IllegalStateException("Shield patterns or base colour were lost while converting");
        }

        fixed.remove(BLOCK_MATERIAL);
        fixed.put(META_TYPE, SHIELD);
        if (shieldMeta.containsKey(SHIELD_PATTERNS_KEY)) {
            fixed.put(SHIELD_PATTERNS_KEY, shieldMeta.get(SHIELD_PATTERNS_KEY));
        }
        if (shieldMeta.containsKey(SHIELD_BASE_COLOR_KEY)) {
            fixed.put(SHIELD_BASE_COLOR_KEY, shieldMeta.get(SHIELD_BASE_COLOR_KEY));
        }
        return fixed;
    }

    private static void requireSameSize(
            Map<String, LegacyNbt.Tag> legacyTag,
            String legacyKey,
            Map<String, LegacyNbt.Tag> components,
            String componentKey) {
        var expected = listSize(legacyTag.get(legacyKey));
        if (expected > 0 && listSize(components.get(componentKey)) != expected) {
            throw new IllegalStateException(legacyKey + " count changed while upgrading: expected " + expected
                    + ", got " + listSize(components.get(componentKey)));
        }
    }

    private static void requireSameListSize(
            Map<String, LegacyNbt.Tag> before, Map<String, LegacyNbt.Tag> after, String key) {
        var expected = listSize(before.get(key));
        if (expected > 0 && listSize(after.get(key)) != expected) {
            throw new IllegalStateException(
                    key + " count changed while upgrading: expected " + expected + ", got " + listSize(after.get(key)));
        }
    }

    private static int listSize(LegacyNbt.Tag tag) {
        return tag != null && tag.value() instanceof LegacyNbt.NbtList list
                ? list.elements().size()
                : 0;
    }

    /** Item compound in the 1.20.5+ format. */
    private static Map<String, LegacyNbt.Tag> itemCompound(
            String itemKey, Map<String, LegacyNbt.Tag> components, int dataVersion) {
        var item = new LinkedHashMap<String, LegacyNbt.Tag>();
        item.put("id", LegacyNbt.string(itemKey));
        item.put("count", LegacyNbt.intTag(1));
        if (!components.isEmpty()) {
            item.put("components", LegacyNbt.compound(new LinkedHashMap<>(components)));
        }
        item.put("DataVersion", LegacyNbt.intTag(dataVersion));
        return item;
    }

    /** Item compound in the pre-1.20.5 format, carrying the legacy BlockEntityTag. */
    private static Map<String, LegacyNbt.Tag> legacyItemCompound(
            String itemKey, Map<String, LegacyNbt.Tag> blockEntityTag, int dataVersion) {
        var tag = new LinkedHashMap<String, LegacyNbt.Tag>();
        tag.put(LEGACY_BLOCK_ENTITY_TAG, LegacyNbt.compound(new LinkedHashMap<>(blockEntityTag)));
        var item = new LinkedHashMap<String, LegacyNbt.Tag>();
        item.put("id", LegacyNbt.string(itemKey));
        item.put("Count", new LegacyNbt.Tag(LegacyNbt.TAG_BYTE, (byte) 1));
        item.put("tag", LegacyNbt.compound(tag));
        item.put("DataVersion", LegacyNbt.intTag(dataVersion));
        return item;
    }
}
