package cn.arkmillion.core.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DataManagerConfig {

    private final List<MySQLConfig> mysqlConfigs;
    private final List<PostgresConfig> postgresConfigs;
    private final List<SQLiteConfig> sqliteConfigs;
    private final List<MongoConfig> mongoConfigs;
    private final List<RedisConfig> redisConfigs;

    private DataManagerConfig(Builder builder) {
        this.mysqlConfigs = Collections.unmodifiableList(new ArrayList<>(builder.mysqlConfigs));
        this.postgresConfigs = Collections.unmodifiableList(new ArrayList<>(builder.postgresConfigs));
        this.sqliteConfigs = Collections.unmodifiableList(new ArrayList<>(builder.sqliteConfigs));
        this.mongoConfigs = Collections.unmodifiableList(new ArrayList<>(builder.mongoConfigs));
        this.redisConfigs = Collections.unmodifiableList(new ArrayList<>(builder.redisConfigs));
    }

    public static Builder builder() {
        return new Builder();
    }

    public MySQLConfig getMySQLConfig() {
        return mysqlConfigs.isEmpty() ? null : mysqlConfigs.get(0);
    }

    public PostgresConfig getPostgresConfig() {
        return postgresConfigs.isEmpty() ? null : postgresConfigs.get(0);
    }

    public SQLiteConfig getSQLiteConfig() {
        return sqliteConfigs.isEmpty() ? null : sqliteConfigs.get(0);
    }

    public MongoConfig getMongoConfig() {
        return mongoConfigs.isEmpty() ? null : mongoConfigs.get(0);
    }

    public RedisConfig getRedisConfig() {
        return redisConfigs.isEmpty() ? null : redisConfigs.get(0);
    }

    public List<MySQLConfig> getMySQLConfigs() {
        return mysqlConfigs;
    }

    public List<PostgresConfig> getPostgresConfigs() {
        return postgresConfigs;
    }

    public List<SQLiteConfig> getSQLiteConfigs() {
        return sqliteConfigs;
    }

    public List<MongoConfig> getMongoConfigs() {
        return mongoConfigs;
    }

    public List<RedisConfig> getRedisConfigs() {
        return redisConfigs;
    }

    public static final class Builder {

        private final List<MySQLConfig> mysqlConfigs = new ArrayList<>();
        private final List<PostgresConfig> postgresConfigs = new ArrayList<>();
        private final List<SQLiteConfig> sqliteConfigs = new ArrayList<>();
        private final List<MongoConfig> mongoConfigs = new ArrayList<>();
        private final List<RedisConfig> redisConfigs = new ArrayList<>();

        public MySQLBuilder mysql(String url, String user, String password) {
            MySQLConfig config = new MySQLConfig(url, user, password);
            mysqlConfigs.add(config);
            return new MySQLBuilder(this, config);
        }

        public PostgresBuilder postgres(String url, String user, String password) {
            PostgresConfig config = new PostgresConfig(url, user, password);
            postgresConfigs.add(config);
            return new PostgresBuilder(this, config);
        }

        public SQLiteBuilder sqlite(String filePath) {
            SQLiteConfig config = new SQLiteConfig(filePath);
            sqliteConfigs.add(config);
            return new SQLiteBuilder(this, config);
        }

        public MongoBuilder mongo(String uri) {
            MongoConfig config = new MongoConfig(uri);
            mongoConfigs.add(config);
            return new MongoBuilder(this, config);
        }

        public RedisBuilder redis(String host, int port) {
            RedisConfig config = new RedisConfig(host, port);
            redisConfigs.add(config);
            return new RedisBuilder(this, config);
        }

        public DataManagerConfig build() {
            return new DataManagerConfig(this);
        }
    }

    public static final class MySQLBuilder {

        private final Builder outer;
        private final MySQLConfig config;

        private MySQLBuilder(Builder outer, MySQLConfig config) {
            this.outer = outer;
            this.config = config;
        }

        public MySQLBuilder alias(String alias) {
            config.setAlias(alias);
            return this;
        }

        public MySQLBuilder poolSize(int poolSize) {
            config.setPoolSize(poolSize);
            return this;
        }

        public MySQLBuilder minIdle(int minIdle) {
            config.setMinIdle(minIdle);
            return this;
        }

        public MySQLBuilder connectionTimeout(long timeoutMs) {
            config.setConnectionTimeout(timeoutMs);
            return this;
        }

        public MySQLBuilder idleTimeout(long timeoutMs) {
            config.setIdleTimeout(timeoutMs);
            return this;
        }

        public MySQLBuilder maxLifetime(long lifetimeMs) {
            config.setMaxLifetime(lifetimeMs);
            return this;
        }

        public MySQLBuilder initializationFailTimeout(long timeoutMs) {
            config.setInitializationFailTimeout(timeoutMs);
            return this;
        }

        public MySQLBuilder charset(String charset) {
            config.setCharset(charset);
            return this;
        }

        public MySQLBuilder ssl(boolean enabled) {
            config.setSslEnabled(enabled);
            return this;
        }

        public Builder end() {
            return outer;
        }

        public Builder build() {
            return outer;
        }
    }

    public static final class PostgresBuilder {

        private final Builder outer;
        private final PostgresConfig config;

        private PostgresBuilder(Builder outer, PostgresConfig config) {
            this.outer = outer;
            this.config = config;
        }

        public PostgresBuilder alias(String alias) {
            config.setAlias(alias);
            return this;
        }

        public PostgresBuilder poolSize(int poolSize) {
            config.setPoolSize(poolSize);
            return this;
        }

        public PostgresBuilder minIdle(int minIdle) {
            config.setMinIdle(minIdle);
            return this;
        }

        public PostgresBuilder connectionTimeout(long timeoutMs) {
            config.setConnectionTimeout(timeoutMs);
            return this;
        }

        public PostgresBuilder idleTimeout(long timeoutMs) {
            config.setIdleTimeout(timeoutMs);
            return this;
        }

        public PostgresBuilder maxLifetime(long lifetimeMs) {
            config.setMaxLifetime(lifetimeMs);
            return this;
        }

        public PostgresBuilder initializationFailTimeout(long timeoutMs) {
            config.setInitializationFailTimeout(timeoutMs);
            return this;
        }

        public PostgresBuilder schema(String schema) {
            config.setSchema(schema);
            return this;
        }

        public Builder end() {
            return outer;
        }

        public Builder build() {
            return outer;
        }
    }

    public static final class SQLiteBuilder {

        private final Builder outer;
        private final SQLiteConfig config;

        private SQLiteBuilder(Builder outer, SQLiteConfig config) {
            this.outer = outer;
            this.config = config;
        }

        public SQLiteBuilder alias(String alias) {
            config.setAlias(alias);
            return this;
        }

        public SQLiteBuilder poolSize(int poolSize) {
            config.setPoolSize(poolSize);
            return this;
        }

        public SQLiteBuilder busyTimeout(int ms) {
            config.setBusyTimeoutMs(ms);
            return this;
        }

        public SQLiteBuilder foreignKeys(boolean enabled) {
            config.setForeignKeys(enabled);
            return this;
        }

        public SQLiteBuilder initializationFailTimeout(long timeoutMs) {
            config.setInitializationFailTimeout(timeoutMs);
            return this;
        }

        public Builder end() {
            return outer;
        }

        public Builder build() {
            return outer;
        }
    }

    public static final class MongoBuilder {

        private final Builder outer;
        private final MongoConfig config;

        private MongoBuilder(Builder outer, MongoConfig config) {
            this.outer = outer;
            this.config = config;
        }

        public MongoBuilder alias(String alias) {
            config.setAlias(alias);
            return this;
        }

        public MongoBuilder database(String database) {
            config.setDatabase(database);
            return this;
        }

        public MongoBuilder applicationName(String name) {
            config.setApplicationName(name);
            return this;
        }

        public Builder end() {
            return outer;
        }

        public Builder build() {
            return outer;
        }
    }

    public static final class RedisBuilder {

        private final Builder outer;
        private final RedisConfig config;

        private RedisBuilder(Builder outer, RedisConfig config) {
            this.outer = outer;
            this.config = config;
        }

        public RedisBuilder alias(String alias) {
            config.setAlias(alias);
            return this;
        }

        public RedisBuilder database(int database) {
            config.setDatabase(database);
            return this;
        }

        public RedisBuilder password(String password) {
            config.setPassword(password);
            return this;
        }

        public RedisBuilder timeout(int timeoutMs) {
            config.setTimeoutMs(timeoutMs);
            return this;
        }

        public RedisBuilder ssl(boolean enabled) {
            config.setSsl(enabled);
            return this;
        }

        public RedisBuilder pool(PoolCustomizer customizer) {
            customizer.customize(config);
            return this;
        }

        public Builder end() {
            return outer;
        }

        public Builder build() {
            return outer;
        }
    }

    @FunctionalInterface
    public interface PoolCustomizer {

        void customize(RedisConfig config);
    }
}
