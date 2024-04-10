package com.xzavier0722.mc.plugin.slimefun4.storage.adapter.sqlcommon;

import city.norain.slimefun4.utils.SimpleTimer;
import com.xzavier0722.mc.plugin.slimefun4.storage.adapter.IDataSourceAdapter;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordSet;
import com.zaxxer.hikari.HikariDataSource;
import io.github.thebusybiscuit.slimefun4.core.debug.Debug;
import io.github.thebusybiscuit.slimefun4.core.debug.TestCase;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;

public abstract class SqlCommonAdapter<T extends ISqlCommonConfig> implements IDataSourceAdapter<T> {
    protected HikariDataSource ds;
    protected String profileTable, researchTable, backpackTable, bpInvTable;
    protected String blockRecordTable, blockDataTable, chunkDataTable, blockInvTable;
    protected T config;

    @Override
    public void prepare(T config) {
        this.config = config;
        ds = config.createDataSource();
    }

    protected void executeSql(String sql) {
        var timer = new SimpleTimer();

        try (var conn = ds.getConnection()) {
            SqlUtils.execSql(conn, sql);
        } catch (SQLException e) {
            throw new IllegalStateException("An exception thrown while executing sql: " + sql, e);
        } finally {
            if (timer.isTimeout(Duration.ofSeconds(2))) { // FIXME: hardcode slow sql check duration
                Debug.log(TestCase.DATABASE, "Detected slow sql costs {}, sql: {}", timer.durationStr(), sql);
            }
        }
    }

    protected List<RecordSet> executeQuery(String sql) {
        var timer = new SimpleTimer();

        try (var conn = ds.getConnection()) {
            return SqlUtils.execQuery(conn, sql);
        } catch (SQLException e) {
            throw new IllegalStateException("An exception thrown while executing sql: " + sql, e);
        } finally {
            if (timer.isTimeout(Duration.ofSeconds(2))) { // FIXME: hardcode slow sql check duration
                Debug.log(TestCase.DATABASE, "Detected slow sql costs {}, sql: {}", timer.durationStr(), sql);
            }
        }
    }

    protected String mapTable(DataScope scope) {
        return switch (scope) {
            case PLAYER_PROFILE -> profileTable;
            case BACKPACK_INVENTORY -> bpInvTable;
            case BACKPACK_PROFILE -> backpackTable;
            case PLAYER_RESEARCH -> researchTable;
            case BLOCK_INVENTORY -> blockInvTable;
            case CHUNK_DATA -> chunkDataTable;
            case BLOCK_DATA -> blockDataTable;
            case BLOCK_RECORD -> blockRecordTable;
            case NONE -> throw new IllegalArgumentException("NONE cannot be a storage data scope!");
        };
    }

    @Override
    public void shutdown() {
        ds.close();
        ds = null;
        profileTable = null;
        researchTable = null;
        backpackTable = null;
        bpInvTable = null;
        blockDataTable = null;
        blockRecordTable = null;
        chunkDataTable = null;
        blockInvTable = null;
    }
}
