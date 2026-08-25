package cn.arkmillion.mysql;

import cn.arkmillion.core.config.MySQLConfig;
import cn.arkmillion.core.config.PlaceholderResolver;
import cn.arkmillion.core.exception.DataManagerException;
import cn.arkmillion.core.jdbc.AbstractJdbcRelationalDB;
import cn.arkmillion.core.jdbc.ConnectionPool;
import cn.arkmillion.core.schema.SchemaSynchronizer;

public class MySQLAdapter extends AbstractJdbcRelationalDB {

    private final MySQLConfig config;

    public MySQLAdapter(MySQLConfig config) {
        super("mysql",
                ConnectionPool.getOrCreate("mysql", resolvedConfig(config)),
                MySqlDialect.INSTANCE);
        this.config = config;
    }

    private static MySQLConfig resolvedConfig(MySQLConfig config) {
        config.setUrl(PlaceholderResolver.resolve(config.getUrl()));
        if (config.getUsername() != null) {
            config.setUsername(PlaceholderResolver.resolve(config.getUsername()));
        }
        if (config.getPassword() != null) {
            config.setPassword(PlaceholderResolver.resolve(config.getPassword()));
        }
        return config;
    }

    @Override
    protected SchemaSynchronizer createSynchronizer() {
        return new MySQLSchemaSynchronizer(dataSource);
    }

    public MySQLConfig getConfig() {
        return config;
    }

    static MySQLAdapter createIfConfigured(cn.arkmillion.core.config.DataManagerConfig managerConfig) {
        MySQLConfig cfg = managerConfig.getMySQLConfig();
        if (cfg == null) {
            throw new DataManagerException("MySQL is not configured in DataManagerConfig");
        }
        return new MySQLAdapter(cfg);
    }
}
