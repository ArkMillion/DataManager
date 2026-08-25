package cn.arkmillion.examples;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuickStartTest {

    @Test
    void quickStartRunsEndToEnd() throws Exception {
        Path dbFile = Files.createTempFile("quickstart-test", ".db");
        Files.deleteIfExists(dbFile);
        try {
            Path result = QuickStart.run(dbFile);
            assertTrue(Files.exists(result));
            assertTrue(Files.size(result) > 0);
        } finally {
            Files.deleteIfExists(dbFile);
        }
    }

    @Test
    void missingCacheManagerThrows() throws Exception {
        Path dbFile = Files.createTempFile("quickstart-nocache", ".db");
        Files.deleteIfExists(dbFile);
        try {
            cn.arkmillion.core.config.DataManagerConfig config =
                    cn.arkmillion.core.config.DataManagerConfig.builder()
                            .sqlite(dbFile.toString())
                            .poolSize(1)
                            .build()
                            .build();
            try (cn.arkmillion.core.db.DataManager dm = cn.arkmillion.core.factory.DataManagerFactory.create(config)) {
                assertEquals(java.util.Collections.singleton("sqlite"), dm.getRelationalDBNames());
                assertThrows(cn.arkmillion.core.exception.DataManagerException.class, dm::getCacheManager);
                assertNotNull(dm.getRelationalDB("sqlite"));
            }
        } finally {
            Files.deleteIfExists(dbFile);
        }
    }
}
