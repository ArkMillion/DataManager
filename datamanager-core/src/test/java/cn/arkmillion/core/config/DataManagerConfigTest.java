package cn.arkmillion.core.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class DataManagerConfigTest {

    @Test
    void buildsAllSubConfigsWithFluentNesting() {
        DataManagerConfig config = DataManagerConfig.builder()
                .mysql("jdbc:mysql://localhost:3306/db", "root", "pass")
                .poolSize(20)
                .minIdle(5)
                .maxLifetime(1800000)
                .charset("utf8mb4")
                .build()
                .sqlite("data/app.db")
                .poolSize(2)
                .busyTimeout(3000)
                .build()
                .mongo("mongodb://localhost:27017/logs")
                .database("logs")
                .build()
                .redis("localhost", 6379)
                .database(0)
                .password("secret")
                .pool(cfg -> cfg.setMaxTotal(50))
                .build()
                .build();

        assertNotNull(config.getMySQLConfig());
        assertEquals(20, config.getMySQLConfig().getPoolSize());
        assertEquals("utf8mb4", config.getMySQLConfig().getCharset());

        assertNotNull(config.getSQLiteConfig());
        assertEquals("data/app.db", config.getSQLiteConfig().getFilePath());
        assertEquals(3000, config.getSQLiteConfig().getBusyTimeoutMs());

        assertNotNull(config.getMongoConfig());
        assertEquals("logs", config.getMongoConfig().getDatabase());

        assertNotNull(config.getRedisConfig());
        assertEquals(0, config.getRedisConfig().getDatabase());
        assertEquals(50, config.getRedisConfig().getMaxTotal());
    }

    @Test
    void partialConfigLeavesOthersNull() {
        DataManagerConfig config = DataManagerConfig.builder()
                .sqlite("x.db")
                .build()
                .build();
        assertNull(config.getMySQLConfig());
        assertNotNull(config.getSQLiteConfig());
        assertNull(config.getMongoConfig());
        assertNull(config.getRedisConfig());
    }

    @Test
    void sameTypeInstancesAccumulateWithAliases() {
        DataManagerConfig config = DataManagerConfig.builder()
                .mysql("jdbc:mysql://a/db", "u", "p")
                .alias("main")
                .poolSize(10)
                .build()
                .mysql("jdbc:mysql://b/db", "u", "p")
                .poolSize(5)
                .build()
                .sqlite("primary.db")
                .alias("primary")
                .build()
                .sqlite("replica.db")
                .alias("replica")
                .build()
                .build();

        assertEquals(2, config.getMySQLConfigs().size());
        assertEquals("main", config.getMySQLConfigs().get(0).getAlias());
        assertNull(config.getMySQLConfigs().get(1).getAlias());
        assertEquals(10, config.getMySQLConfigs().get(0).getPoolSize());
        assertEquals(5, config.getMySQLConfigs().get(1).getPoolSize());

        assertEquals(2, config.getSQLiteConfigs().size());
        assertEquals("primary", config.getSQLiteConfigs().get(0).getAlias());
        assertEquals("replica", config.getSQLiteConfigs().get(1).getAlias());

        assertEquals(config.getMySQLConfigs().get(0), config.getMySQLConfig());

        assertFalse(DataManagerConfig.builder().sqlite("s.db").build().build().getSQLiteConfigs().isEmpty());
    }

    @Test
    void placeholderResolutionFromOverrides() {
        PlaceholderResolver.clear();
        try {
            PlaceholderResolver.register("TEST_DB_PASSWORD", "s3cret");
            assertEquals("s3cret", PlaceholderResolver.resolve("${TEST_DB_PASSWORD}"));
            assertEquals("prefix-s3cret-suffix", PlaceholderResolver.resolve("prefix-${TEST_DB_PASSWORD}-suffix"));
        } finally {
            PlaceholderResolver.clear();
        }
    }

    @Test
    void missingPlaceholderThrows() {
        PlaceholderResolver.clear();
        try {
            assertEquals("plain", PlaceholderResolver.resolve("plain"));
            org.junit.jupiter.api.Assertions.assertThrows(
                    PlaceholderResolver.DataManagerPlaceholderException.class,
                    () -> PlaceholderResolver.resolve("${DEFINITELY_MISSING_VAR_42}"));
        } finally {
            PlaceholderResolver.clear();
        }
    }
}
