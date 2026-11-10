package com.xzavier0722.mc.plugin.slimefun4.storage.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.util.Map;

/**
 * Reads the DataVersion ({@code v}) of the top-level ItemStack in a legacy Bukkit object stream
 * without deserializing any Bukkit objects.
 *
 * <p>Bukkit resolves nested objects (such as ItemMeta) before the ItemStack that contains them, so the
 * meta deserializer cannot see the ItemStack's DataVersion. This pre-pass reads the raw serialized maps
 * only, allowing nothing but JDK collections, Guava immutable collections and Bukkit's Wrapper.
 */
final class LegacyDataVersionReader {
    static final int UNKNOWN = -1;
    private static final String ITEM_STACK_CLASS = "org.bukkit.inventory.ItemStack";
    private static final ObjectInputFilter FILTER = ObjectInputFilter.Config.createFilter(
            "maxdepth=128;maxrefs=1000000;java.lang.*;java.util.*;com.google.common.collect.*;org.bukkit.util.io.Wrapper;!*");

    private LegacyDataVersionReader() {}

    static int read(byte[] serializedObject) {
        try (var input = new MapCollectingInputStream(new ByteArrayInputStream(serializedObject))) {
            input.readObject();
            return input.dataVersion;
        } catch (Exception | StackOverflowError e) {
            return UNKNOWN;
        }
    }

    private static final class MapCollectingInputStream extends ObjectInputStream {
        private int dataVersion = UNKNOWN;

        private MapCollectingInputStream(InputStream input) throws IOException {
            super(input);
            setObjectInputFilter(FILTER);
            enableResolveObject(true);
        }

        @Override
        protected Object resolveObject(Object object) {
            // Nested objects are resolved first, so the last ItemStack map is the top-level one.
            if (object instanceof Map<?, ?> map && ITEM_STACK_CLASS.equals(map.get("=="))) {
                // Older Paper writes "v"; newer Paper writes "DataVersion".
                if (map.get("v") instanceof Number version) {
                    dataVersion = version.intValue();
                } else if (map.get("DataVersion") instanceof Number version) {
                    dataVersion = version.intValue();
                }
            }
            return object;
        }
    }
}
