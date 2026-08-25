package cn.arkmillion.sqlite;

import cn.arkmillion.core.annotation.Column;
import cn.arkmillion.core.annotation.Id;
import cn.arkmillion.core.annotation.Table;
import cn.arkmillion.core.config.SQLiteConfig;
import cn.arkmillion.core.enums.GenerationType;
import cn.arkmillion.core.enums.SyncMode;
import cn.arkmillion.core.jdbc.ConnectionPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SharedPoolLifecycleTest {

    @Table(name = "pool_user")
    public static class PoolUser {

        @Id(strategy = GenerationType.AUTO)
        private Long id;

        @Column(name = "user_name", nullable = false)
        private String userName;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }
    }

    private Path dbFile;
    private Path otherFile;
    private SQLiteAdapter first;
    private SQLiteAdapter second;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = Files.createTempFile("shared-pool", ".db");
        Files.deleteIfExists(dbFile);
        otherFile = Files.createTempFile("shared-pool-other", ".db");
        Files.deleteIfExists(otherFile);
    }

    @AfterEach
    void tearDown() {
        if (first != null) {
            first.close();
        }
        if (second != null) {
            second.close();
        }
        ConnectionPool.shutdownAll();
    }

    @Test
    void sameEndpointReusesPoolAndLastCloseReleasesIt() {
        int poolsBefore = ConnectionPool.activePoolCount();

        first = new SQLiteAdapter(configFor(dbFile));
        second = new SQLiteAdapter(configFor(dbFile));

        DataSource shared = first.getDataSource();
        assertSame(shared, second.getDataSource());
        assertEquals(poolsBefore + 1, ConnectionPool.activePoolCount());

        first.close();

        assertEquals(poolsBefore + 1, ConnectionPool.activePoolCount());
        second.syncSchema(PoolUser.class, SyncMode.CREATE);
        second.execute("INSERT INTO pool_user (user_name) VALUES ('alice')");
        assertEquals(1L, second.count(PoolUser.class, null));

        second.close();
        assertEquals(poolsBefore, ConnectionPool.activePoolCount());
    }

    @Test
    void differentEndpointsGetDifferentPools() {
        int poolsBefore = ConnectionPool.activePoolCount();

        SQLiteAdapter a = new SQLiteAdapter(configFor(dbFile));
        SQLiteAdapter b = new SQLiteAdapter(configFor(otherFile));
        try {
            assertNotEquals(a.getDataSource(), b.getDataSource());
            assertEquals(poolsBefore + 2, ConnectionPool.activePoolCount());
        } finally {
            a.close();
            b.close();
        }
        assertEquals(poolsBefore, ConnectionPool.activePoolCount());

        first = null;
        second = null;
    }

    private SQLiteConfig configFor(Path file) {
        return new SQLiteConfig(file.toString());
    }
}
