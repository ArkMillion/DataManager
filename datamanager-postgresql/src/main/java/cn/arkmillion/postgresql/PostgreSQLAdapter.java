package cn.arkmillion.postgresql;

import cn.arkmillion.core.config.PlaceholderResolver;
import cn.arkmillion.core.config.PostgresConfig;
import cn.arkmillion.core.jdbc.AbstractJdbcRelationalDB;
import cn.arkmillion.core.jdbc.ConnectionPool;
import cn.arkmillion.core.schema.SchemaSynchronizer;

public class PostgreSQLAdapter extends AbstractJdbcRelationalDB {

    private final PostgresConfig config;

    public PostgreSQLAdapter(PostgresConfig config) {
        super("postgres", ConnectionPool.getOrCreate("postgres", resolved(config)), PgDialect.INSTANCE);
        this.config = config;
    }

    private static PostgresConfig resolved(PostgresConfig config) {
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
        return new PostgreSQLSchemaSynchronizer(dataSource);
    }

    public PostgresConfig getConfig() {
        return config;
    }
}
