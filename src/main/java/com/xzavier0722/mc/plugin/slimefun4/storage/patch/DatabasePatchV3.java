package com.xzavier0722.mc.plugin.slimefun4.storage.patch;

import static com.xzavier0722.mc.plugin.slimefun4.storage.adapter.sqlcommon.SqlConstants.FIELD_BACKPACK_ID;
import static com.xzavier0722.mc.plugin.slimefun4.storage.adapter.sqlcommon.SqlConstants.FIELD_INVENTORY_ITEM;
import static com.xzavier0722.mc.plugin.slimefun4.storage.adapter.sqlcommon.SqlConstants.FIELD_INVENTORY_SLOT;
import static com.xzavier0722.mc.plugin.slimefun4.storage.adapter.sqlcommon.SqlConstants.FIELD_LOCATION;
import static com.xzavier0722.mc.plugin.slimefun4.storage.adapter.sqlcommon.SqlConstants.FIELD_UNIVERSAL_UUID;

import com.xzavier0722.mc.plugin.slimefun4.storage.adapter.mysql.MysqlConfig;
import com.xzavier0722.mc.plugin.slimefun4.storage.adapter.postgresql.PostgreSqlConfig;
import com.xzavier0722.mc.plugin.slimefun4.storage.adapter.sqlcommon.ISqlCommonConfig;
import com.xzavier0722.mc.plugin.slimefun4.storage.adapter.sqlcommon.SqlCommonConfig;
import com.xzavier0722.mc.plugin.slimefun4.storage.adapter.sqlcommon.SqlUtils;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.ItemStackDataCodec;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabasePatchV3 extends DatabasePatch {
    static final String QUARANTINE_TABLE = "legacy_item_quarantine";
    private final Supplier<Logger> logger;

    public DatabasePatchV3() {
        this(Slimefun::logger);
    }

    DatabasePatchV3(Supplier<Logger> logger) {
        super(3);
        this.logger = logger;
    }

    @Override
    public void patch(Statement stmt, ISqlCommonConfig config) throws SQLException {
        var prefix = config instanceof SqlCommonConfig sqlConfig ? sqlConfig.tablePrefix() : "";
        var quarantineTable = prefix + QUARANTINE_TABLE;
        migrateTable(
                stmt,
                config,
                SqlUtils.mapTable(DataScope.BACKPACK_INVENTORY, prefix),
                FIELD_BACKPACK_ID,
                quarantineTable);
        migrateTable(
                stmt, config, SqlUtils.mapTable(DataScope.BLOCK_INVENTORY, prefix), FIELD_LOCATION, quarantineTable);
        migrateTable(
                stmt,
                config,
                SqlUtils.mapTable(DataScope.UNIVERSAL_INVENTORY, prefix),
                FIELD_UNIVERSAL_UUID,
                quarantineTable);
    }

    private void migrateTable(
            Statement stmt, ISqlCommonConfig config, String table, String ownerField, String quarantineTable)
            throws SQLException {
        if (!tableExists(stmt, table)) {
            return;
        }

        migrateColumnType(stmt, config, table);

        var selectSql =
                "SELECT " + ownerField + ", " + FIELD_INVENTORY_SLOT + ", " + FIELD_INVENTORY_ITEM + " FROM " + table;
        var updateSql = "UPDATE " + table + " SET " + FIELD_INVENTORY_ITEM + "=? WHERE " + ownerField + "=? AND "
                + FIELD_INVENTORY_SLOT + "=?";
        var migrated = 0;
        var failed = 0;

        try (var result = stmt.executeQuery(selectSql);
                var update = stmt.getConnection().prepareStatement(updateSql)) {
            while (result.next()) {
                var storedItem = result.getObject(FIELD_INVENTORY_ITEM);
                var serializedItem = storedItem instanceof byte[] bytes
                        ? bytes
                        : storedItem.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                if (!ItemStackDataCodec.isLegacy(serializedItem)) {
                    continue;
                }

                try {
                    var itemStack = ItemStackDataCodec.deserialize(serializedItem);
                    if (itemStack == null) {
                        continue;
                    }

                    var migratedItem = ItemStackDataCodec.serialize(itemStack);
                    if (migratedItem.length == 0) {
                        throw new IllegalStateException("迁移后的物品数据为空");
                    }
                    update.setBytes(1, migratedItem);
                    update.setString(2, result.getString(ownerField));
                    update.setInt(3, result.getInt(FIELD_INVENTORY_SLOT));
                    update.addBatch();
                    migrated++;
                } catch (Exception exception) {
                    failed++;
                    var owner = result.getString(ownerField);
                    var slot = result.getInt(FIELD_INVENTORY_SLOT);
                    logger.get()
                            .log(
                                    Level.SEVERE,
                                    "无法迁移旧版物品数据: table=" + table + ", " + ownerField + "=" + owner + ", slot=" + slot,
                                    exception);
                    quarantine(stmt, config, quarantineTable, table, owner, slot, serializedItem, exception);
                }
            }
            update.executeBatch();
        }

        logger.get()
                .log(Level.INFO, "物品数据迁移完成: table={0}, migrated={1}, failed={2}", new Object[] {table, migrated, failed
                });

        if (failed > 0) {
            logger.get().log(Level.SEVERE, "{0} 中有 {1} 条物品数据无法迁移：原记录保持不变，原始数据另存到 {2}，需要管理员人工处理", new Object[] {
                table, failed, quarantineTable
            });
        }
    }

    /**
     * Keeps a copy of a legacy item that could not be migrated, so that it survives even if the
     * original (unreadable) row is later overwritten when the inventory is saved.
     */
    private void quarantine(
            Statement stmt,
            ISqlCommonConfig config,
            String quarantineTable,
            String table,
            String owner,
            int slot,
            byte[] serializedItem,
            Exception exception)
            throws SQLException {
        var connection = stmt.getConnection();
        try (var create = connection.createStatement()) {
            var itemType = config instanceof MysqlConfig ? "MEDIUMTEXT" : "TEXT";
            create.execute("CREATE TABLE IF NOT EXISTS " + quarantineTable
                    + " (source_table VARCHAR(64) NOT NULL, owner VARCHAR(255) NOT NULL, slot INTEGER NOT NULL,"
                    + " legacy_item " + itemType + " NOT NULL, error TEXT, quarantined_at BIGINT NOT NULL)");
        }
        try (var insert = connection.prepareStatement("INSERT INTO " + quarantineTable
                + " (source_table, owner, slot, legacy_item, error, quarantined_at) VALUES (?, ?, ?, ?, ?, ?)")) {
            insert.setString(1, table);
            insert.setString(2, owner);
            insert.setInt(3, slot);
            insert.setString(4, new String(serializedItem, java.nio.charset.StandardCharsets.US_ASCII));
            insert.setString(5, describe(exception));
            insert.setLong(6, System.currentTimeMillis());
            insert.executeUpdate();
        }
    }

    private static String describe(Throwable throwable) {
        var description = new StringBuilder();
        for (var current = throwable; current != null && description.length() < 2000; current = current.getCause()) {
            if (!description.isEmpty()) {
                description.append(" <- ");
            }
            description.append(current.getClass().getName()).append(": ").append(current.getMessage());
        }
        return description.length() > 2000 ? description.substring(0, 2000) : description.toString();
    }

    private void migrateColumnType(Statement stmt, ISqlCommonConfig config, String table) throws SQLException {
        if (isBinaryColumn(stmt, table)) {
            return;
        }

        if (config instanceof MysqlConfig) {
            stmt.execute("ALTER TABLE " + table + " MODIFY COLUMN " + FIELD_INVENTORY_ITEM + " MEDIUMBLOB NOT NULL");
        } else if (config instanceof PostgreSqlConfig) {
            stmt.execute("ALTER TABLE " + table + " ALTER COLUMN " + FIELD_INVENTORY_ITEM
                    + " TYPE BYTEA USING convert_to(" + FIELD_INVENTORY_ITEM + ", 'UTF8')");
        }
    }

    private boolean isBinaryColumn(Statement stmt, String table) throws SQLException {
        try (var columns = stmt.getConnection().getMetaData().getColumns(null, null, table, FIELD_INVENTORY_ITEM)) {
            if (!columns.next()) {
                return false;
            }

            var type = columns.getInt("DATA_TYPE");
            return type == Types.BINARY || type == Types.VARBINARY || type == Types.LONGVARBINARY || type == Types.BLOB;
        }
    }

    private boolean tableExists(Statement stmt, String table) throws SQLException {
        try (var tables = stmt.getConnection().getMetaData().getTables(null, null, table, new String[] {"TABLE"})) {
            return tables.next();
        }
    }
}
