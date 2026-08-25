package cn.arkmillion.core.jdbc;

import cn.arkmillion.core.config.DataSourceConfig;
import cn.arkmillion.core.monitor.ConnectionMonitor;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ConnectionPool {

    private static final Map<String, HikariDataSource> POOLS = new ConcurrentHashMap<>();

    private ConnectionPool() {
    }

    public static DataSource getOrCreate(String poolName, DataSourceConfig config) {
        String key = poolKey(poolName, config);
        HikariDataSource existing = POOLS.get(key);
        if (existing != null && !existing.isClosed()) {
            return existing;
        }
        HikariDataSource created = POOLS.computeIfAbsent(key, k -> createPool(poolName, config));
        ConnectionMonitor.getInstance().notifyConnected(poolName);
        return created;
    }

    private static String poolKey(String poolName, DataSourceConfig config) {
        return poolName + "|" + config.getUrl() + "|" + config.getUsername();
    }

    private static HikariDataSource createPool(String poolName, DataSourceConfig config) {
        HikariConfig hc = new HikariConfig();
        hc.setPoolName(poolName);
        hc.setJdbcUrl(config.getUrl());
        hc.setUsername(config.getUsername());
        hc.setPassword(config.getPassword());
        if (config.getDriverClassName() != null && !config.getDriverClassName().isEmpty()) {
            hc.setDriverClassName(config.getDriverClassName());
        }
        hc.setMaximumPoolSize(Math.max(1, config.getPoolSize()));
        hc.setMinimumIdle(Math.max(0, config.getMinIdle()));
        hc.setConnectionTimeout(config.getConnectionTimeout());
        hc.setIdleTimeout(config.getIdleTimeout());
        hc.setMaxLifetime(config.getMaxLifetime());
        hc.setReadOnly(config.isReadOnly());
        if (config.getInitializationFailTimeout() != null) {
            hc.setInitializationFailTimeout(config.getInitializationFailTimeout());
        }
        return new HikariDataSource(hc);
    }

    public static synchronized void shutdown(DataSource dataSource) {
        if (!(dataSource instanceof HikariDataSource)) {
            return;
        }
        HikariDataSource hikari = (HikariDataSource) dataSource;
        POOLS.values().removeIf(ds -> ds == hikari);
        if (!hikari.isClosed()) {
            hikari.close();
        }
    }

    public static synchronized void shutdownAll() {
        for (Map.Entry<String, HikariDataSource> e : POOLS.entrySet()) {
            if (!e.getValue().isClosed()) {
                e.getValue().close();
            }
        }
        POOLS.clear();
    }

    static int activePoolCount() {
        return POOLS.size();
    }
}
