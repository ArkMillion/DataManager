package cn.arkmillion.sqlite;

import cn.arkmillion.core.config.PlaceholderResolver;
import cn.arkmillion.core.config.SQLiteConfig;
import cn.arkmillion.core.jdbc.AbstractJdbcRelationalDB;
import cn.arkmillion.core.jdbc.ConnectionPool;
import cn.arkmillion.core.jdbc.JdbcDialect;
import cn.arkmillion.core.schema.SchemaSynchronizer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SQLiteAdapter extends AbstractJdbcRelationalDB {

    private final SQLiteConfig config;

    public SQLiteAdapter(SQLiteConfig config) {
        super("sqlite", ConnectionPool.getOrCreate("sqlite", resolved(config)), JdbcDialect.DEFAULT_DIALECT);
        this.config = config;
    }

    private static SQLiteConfig resolved(SQLiteConfig config) {
        String filePath = PlaceholderResolver.resolve(config.getFilePath());
        if (!":memory:".equals(filePath) && !filePath.startsWith("file:")) {
            Path path = Paths.get(filePath).toAbsolutePath();
            try {
                if (path.getParent() != null && !Files.exists(path.getParent())) {
                    Files.createDirectories(path.getParent());
                }
            } catch (java.io.IOException e) {
                throw new cn.arkmillion.core.exception.DataManagerException(
                        "Failed to create directory for SQLite database: " + path, e);
            }
        }
        config.setFilePath(filePath);
        config.setUrl(buildJdbcUrl(filePath));
        return config;
    }

    static String buildJdbcUrl(String filePath) {
        if (filePath.startsWith("jdbc:")) {
            return filePath;
        }
        if (filePath.startsWith("file:") || ":memory:".equals(filePath)) {
            return "jdbc:sqlite:" + filePath;
        }
        return "jdbc:sqlite:" + filePath.replace('\\', '/');
    }

    @Override
    protected SchemaSynchronizer createSynchronizer() {
        return new SQLiteSchemaSynchronizer(dataSource);
    }

    public SQLiteConfig getConfig() {
        return config;
    }
}
