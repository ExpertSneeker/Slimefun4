package com.xzavier0722.mc.plugin.slimefun4.storage.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Minimal NBT tree used to rewrite the gzip-compressed NBT blobs that Paper embeds in legacy
 * ItemMeta maps ({@code internal} / {@code unhandled}) and in {@code ItemStack#serializeAsBytes}.
 */
final class LegacyNbt {
    static final int TAG_END = 0;
    static final int TAG_BYTE = 1;
    static final int TAG_SHORT = 2;
    static final int TAG_INT = 3;
    static final int TAG_LONG = 4;
    static final int TAG_FLOAT = 5;
    static final int TAG_DOUBLE = 6;
    static final int TAG_BYTE_ARRAY = 7;
    static final int TAG_STRING = 8;
    static final int TAG_LIST = 9;
    static final int TAG_COMPOUND = 10;
    static final int TAG_INT_ARRAY = 11;
    static final int TAG_LONG_ARRAY = 12;

    private static final int MAX_DEPTH = 512;

    private LegacyNbt() {}

    record Tag(int type, Object value) {
        @SuppressWarnings("unchecked")
        Map<String, Tag> asCompound() {
            if (type != TAG_COMPOUND) {
                throw new IllegalStateException("Not a compound tag: " + type);
            }
            return (Map<String, Tag>) value;
        }
    }

    record NbtList(int elementType, List<Tag> elements) {}

    static Tag compound(Map<String, Tag> entries) {
        return new Tag(TAG_COMPOUND, entries);
    }

    static Tag string(String value) {
        return new Tag(TAG_STRING, value);
    }

    static Tag intTag(int value) {
        return new Tag(TAG_INT, value);
    }

    static Map<String, Tag> readBase64(String encoded) throws IOException {
        return readCompressed(Base64.getDecoder().decode(encoded));
    }

    static String writeBase64(Map<String, Tag> root) throws IOException {
        return Base64.getEncoder().encodeToString(writeCompressed(root));
    }

    static Map<String, Tag> readCompressed(byte[] data) throws IOException {
        try (var input = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(data)))) {
            var rootType = input.readUnsignedByte();
            if (rootType != TAG_COMPOUND) {
                throw new IOException("Root tag is not a compound: " + rootType);
            }
            input.readUTF();
            return readCompound(input, 0);
        }
    }

    static byte[] writeCompressed(Map<String, Tag> root) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(new GZIPOutputStream(bytes))) {
            output.writeByte(TAG_COMPOUND);
            output.writeUTF("");
            writeCompound(output, root);
        }
        return bytes.toByteArray();
    }

    private static Object readPayload(int type, DataInputStream input, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException("NBT nesting too deep");
        }
        return switch (type) {
            case TAG_BYTE -> input.readByte();
            case TAG_SHORT -> input.readShort();
            case TAG_INT -> input.readInt();
            case TAG_LONG -> input.readLong();
            case TAG_FLOAT -> input.readFloat();
            case TAG_DOUBLE -> input.readDouble();
            case TAG_BYTE_ARRAY -> input.readNBytes(checkedLength(input.readInt()));
            case TAG_STRING -> input.readUTF();
            case TAG_LIST -> readList(input, depth);
            case TAG_COMPOUND -> readCompound(input, depth);
            case TAG_INT_ARRAY -> {
                var values = new int[checkedLength(input.readInt())];
                for (var i = 0; i < values.length; i++) {
                    values[i] = input.readInt();
                }
                yield values;
            }
            case TAG_LONG_ARRAY -> {
                var values = new long[checkedLength(input.readInt())];
                for (var i = 0; i < values.length; i++) {
                    values[i] = input.readLong();
                }
                yield values;
            }
            default -> throw new IOException("Unsupported NBT tag type: " + type);
        };
    }

    private static Map<String, Tag> readCompound(DataInputStream input, int depth) throws IOException {
        var entries = new LinkedHashMap<String, Tag>();
        while (true) {
            var type = input.readUnsignedByte();
            if (type == TAG_END) {
                return entries;
            }
            var name = input.readUTF();
            entries.put(name, new Tag(type, readPayload(type, input, depth + 1)));
        }
    }

    private static NbtList readList(DataInputStream input, int depth) throws IOException {
        var elementType = input.readUnsignedByte();
        var length = checkedLength(input.readInt());
        var elements = new ArrayList<Tag>(Math.min(length, 1024));
        for (var i = 0; i < length; i++) {
            elements.add(new Tag(elementType, readPayload(elementType, input, depth + 1)));
        }
        return new NbtList(elementType, elements);
    }

    private static int checkedLength(int length) throws IOException {
        if (length < 0) {
            throw new IOException("Negative NBT length: " + length);
        }
        return length;
    }

    @SuppressWarnings("unchecked")
    private static void writePayload(DataOutputStream output, Tag tag) throws IOException {
        switch (tag.type()) {
            case TAG_BYTE -> output.writeByte((Byte) tag.value());
            case TAG_SHORT -> output.writeShort((Short) tag.value());
            case TAG_INT -> output.writeInt((Integer) tag.value());
            case TAG_LONG -> output.writeLong((Long) tag.value());
            case TAG_FLOAT -> output.writeFloat((Float) tag.value());
            case TAG_DOUBLE -> output.writeDouble((Double) tag.value());
            case TAG_BYTE_ARRAY -> {
                var values = (byte[]) tag.value();
                output.writeInt(values.length);
                output.write(values);
            }
            case TAG_STRING -> output.writeUTF((String) tag.value());
            case TAG_LIST -> {
                var list = (NbtList) tag.value();
                output.writeByte(list.elementType());
                output.writeInt(list.elements().size());
                for (var element : list.elements()) {
                    if (element.type() != list.elementType()) {
                        throw new IOException("Mixed NBT list element types");
                    }
                    writePayload(output, element);
                }
            }
            case TAG_COMPOUND -> writeCompound(output, (Map<String, Tag>) tag.value());
            case TAG_INT_ARRAY -> {
                var values = (int[]) tag.value();
                output.writeInt(values.length);
                for (var value : values) {
                    output.writeInt(value);
                }
            }
            case TAG_LONG_ARRAY -> {
                var values = (long[]) tag.value();
                output.writeInt(values.length);
                for (var value : values) {
                    output.writeLong(value);
                }
            }
            default -> throw new IOException("Unsupported NBT tag type: " + tag.type());
        }
    }

    private static void writeCompound(DataOutputStream output, Map<String, Tag> entries) throws IOException {
        for (var entry : entries.entrySet()) {
            output.writeByte(entry.getValue().type());
            output.writeUTF(entry.getKey());
            writePayload(output, entry.getValue());
        }
        output.writeByte(TAG_END);
    }
}
