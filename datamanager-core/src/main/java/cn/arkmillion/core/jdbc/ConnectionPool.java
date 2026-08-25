package cn.arkmillion.core.jdbc;

import cn.arkmillion.core.config.DataSourceConfig;
import cn.arkmillion.core.monitor.ConnectionMonitor;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

public final class ConnectionPool {

    private static final SharedPoolRegistry REGISTRY = new SharedPoolRegistry();

    private ConnectionPool() {
    }

    public static DataSource getOrCreate(String poolName, DataSourceConfig config) {
        String key = poolKey(poolName, config);
        DataSource dataSource = REGISTRY.acquire(key, () -> createPool(poolName, config));
        ConnectionMonitor.getInstance().notifyConnected(poolName);
        return dataSource;
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
        REGISTRY.release(dataSource);
    }

    public static synchronized void shutdownAll() {
        REGISTRY.releaseAll();
    }

    public static int activePoolCount() {
        return REGISTRY.size();
    }
}
