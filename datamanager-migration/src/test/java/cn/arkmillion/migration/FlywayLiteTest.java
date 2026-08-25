package cn.arkmillion.migration;

import cn.arkmillion.core.config.SQLiteConfig;
import cn.arkmillion.core.db.RelationalDB;
import cn.arkmillion.core.exception.DataManagerException;
import cn.arkmillion.sqlite.SQLiteAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayLiteTest {

    private Path dbFile;
    private RelationalDB db;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = Files.createTempFile("migration-test", ".db");
        Files.deleteIfExists(dbFile);
        db = new SQLiteAdapter(new SQLiteConfig(dbFile.toString()));
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.close();
        }
        cn.arkmillion.core.jdbc.ConnectionPool.shutdownAll();
    }

    @Test
    void appliesScriptsInOrderAndRecordsHistory() {
        FlywayLite flyway = FlywayLite.builder()
                .relational(db)
                .locations("classpath:db/migration")
                .build();

        MigrationSummary summary = flyway.migrate();

        assertEquals(2, summary.getAppliedVersions().size());
        assertEquals("0002", summary.getCurrentVersion());

        List<Map<String, Object>> history = db.queryMap(
                "SELECT version, script, success FROM dm_schema_history ORDER BY version");
        assertEquals(2, history.size());
        assertEquals("0001", history.get(0).get("version"));
        Number successFlag = (Number) history.get(0).get("success");
        assertEquals(1L, successFlag.longValue());

        Number itemCount = (Number) db.queryMap("SELECT COUNT(*) AS c FROM migration_item").get(0).get("c");
        assertEquals(2L, itemCount.longValue());
    }

    @Test
    void secondRunIsNoOp() {
        FlywayLite flyway = FlywayLite.builder()
                .relational(db)
                .locations("classpath:db/migration")
                .build();
        flyway.migrate();

        MigrationSummary second = flyway.migrate();

        assertTrue(second.isEmpty());
        assertEquals("0002", second.getCurrentVersion());
        Number itemCount = (Number) db.queryMap("SELECT COUNT(*) AS c FROM migration_item").get(0).get("c");
        assertEquals(2L, itemCount.longValue());
    }

    @Test
    void placeholdersAreReplaced() throws Exception {
        Path dir = Files.createTempDirectory("ph-migrations");
        Files.write(dir.resolve("V1__notes.sql"), Arrays.asList(
                "CREATE TABLE notes (id INTEGER PRIMARY KEY, note TEXT);",
                "INSERT INTO notes (note) VALUES ('${SCHEMA_PREFIX}-note');"
        ));

        FlywayLite flyway = FlywayLite.builder()
                .relational(db)
                .locations(dir.toString())
                .placeholder("SCHEMA_PREFIX", "demo")
                .build();
        flyway.migrate();

        Map<String, Object> row = db.queryMap("SELECT note FROM notes WHERE id = 1").get(0);
        assertEquals("demo-note", row.get("note"));
    }

    @Test
    void missingPlaceholderFailsFastWithoutSideEffects() throws Exception {
        Path dir = Files.createTempDirectory("missing-ph-migrations");
        Files.write(dir.resolve("V1__needs_placeholder.sql"), Arrays.asList(
                "CREATE TABLE broken (id INTEGER PRIMARY KEY);",
                "INSERT INTO broken VALUES (${NOT_PROVIDED});"
        ));

        FlywayLite flyway = FlywayLite.builder()
                .relational(db)
                .locations(dir.toString())
                .build();

        assertThrows(DataManagerException.class, () -> flyway.migrate());
        assertEquals(0L, countRowsIn("dm_schema_history"));
    }

    @Test
    void failedMigrationIsNotRecordedAsApplied() throws Exception {
        Path dir = Files.createTempDirectory("failing-migrations");
        Files.write(dir.resolve("V1__bad.sql"), java.util.Collections.singletonList(
                "CREATE TABLE will_fail ("
        ));

        FlywayLite flyway = FlywayLite.builder()
                .relational(db)
                .locations(dir.toString())
                .build();

        assertThrows(DataManagerException.class, () -> flyway.migrate());
        assertEquals(0L, countRowsIn("dm_schema_history"));
    }

    @Test
    void checksumMismatchIsRejected() throws Exception {
        FlywayLite good = FlywayLite.builder()
                .relational(db)
                .locations("classpath:db/migration")
                .build();
        good.migrate();

        Path dir = Files.createTempDirectory("tampered-migrations");
        Files.write(dir.resolve("V0001__init_schema.sql"), Arrays.asList(
                "-- tampered content changes the checksum",
                "CREATE TABLE IF NOT EXISTS migration_item (id INTEGER PRIMARY KEY, name TEXT);"
        ));

        FlywayLite tampered = FlywayLite.builder()
                .relational(db)
                .locations(dir.toString())
                .build();

        assertThrows(DataManagerException.class, tampered::validate);
    }

    private long countRowsIn(String table) {
        Number n = (Number) db.queryMap("SELECT COUNT(*) AS c FROM " + table).get(0).get("c");
        return n.longValue();
    }
}
