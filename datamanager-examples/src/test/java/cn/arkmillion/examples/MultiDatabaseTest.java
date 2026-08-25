package cn.arkmillion.examples;

import cn.arkmillion.core.annotation.Column;
import cn.arkmillion.core.annotation.Id;
import cn.arkmillion.core.annotation.Table;
import cn.arkmillion.core.config.DataManagerConfig;
import cn.arkmillion.core.db.DataManager;
import cn.arkmillion.core.db.RelationalDB;
import cn.arkmillion.core.enums.GenerationType;
import cn.arkmillion.core.enums.SyncMode;
import cn.arkmillion.core.exception.DataManagerException;
import cn.arkmillion.core.factory.DataManagerFactory;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static cn.arkmillion.core.condition.Condition.where;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiDatabaseTest {

    @Table(name = "alias_user")
    public static class AliasUser {

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

    @Test
    void twoSqliteInstancesAreAddressableByAliasAndIsolated() throws Exception {
        Path primaryFile = Files.createTempFile("alias-primary", ".db");
        Path replicaFile = Files.createTempFile("alias-replica", ".db");
        Files.deleteIfExists(primaryFile);
        Files.deleteIfExists(replicaFile);
        try {
            DataManagerConfig config = DataManagerConfig.builder()
                    .sqlite(primaryFile.toString())
                    .alias("primary")
                    .poolSize(1)
                    .build()
                    .sqlite(replicaFile.toString())
                    .alias("replica")
                    .poolSize(1)
                    .build()
                    .build();

            try (DataManager dm = DataManagerFactory.create(config)) {
                assertEquals(java.util.Collections.singleton("primary").size() + 1,
                        dm.getRelationalDBNames().size());
                assertTrue(dm.getRelationalDBNames().contains("primary"));
                assertTrue(dm.getRelationalDBNames().contains("replica"));

                RelationalDB primary = dm.getRelationalDB("primary");
                RelationalDB replica = dm.getRelationalDB("replica");

                primary.syncSchema(AliasUser.class, SyncMode.CREATE);
                replica.syncSchema(AliasUser.class, SyncMode.CREATE);

                AliasUser user = new AliasUser();
                user.setUserName("alice");
                primary.insert(user);

                assertEquals(1, primary.count(AliasUser.class, null));
                assertEquals(0, replica.count(AliasUser.class, where("userName").eq("alice").build()));
            }
        } finally {
            Files.deleteIfExists(primaryFile);
            Files.deleteIfExists(replicaFile);
        }
    }

    @Test
    void duplicateAliasAcrossInstancesIsRejectedAtStartup() throws Exception {
        Path f1 = Files.createTempFile("dup-a", ".db");
        Path f2 = Files.createTempFile("dup-b", ".db");
        Files.deleteIfExists(f1);
        Files.deleteIfExists(f2);
        try {
            DataManagerConfig config = DataManagerConfig.builder()
                    .sqlite(f1.toString())
                    .alias("same")
                    .poolSize(1)
                    .build()
                    .sqlite(f2.toString())
                    .alias("same")
                    .poolSize(1)
                    .build()
                    .build();

            assertThrows(DataManagerException.class, () -> DataManagerFactory.create(config));
        } finally {
            Files.deleteIfExists(f1);
            Files.deleteIfExists(f2);
        }
    }

    @Test
    void unnamedInstanceFallsBackToProviderName() throws Exception {
        Path file = Files.createTempFile("fallback-name", ".db");
        Files.deleteIfExists(file);
        try {
            DataManagerConfig config = DataManagerConfig.builder()
                    .sqlite(file.toString())
                    .poolSize(1)
                    .build()
                    .build();
            try (DataManager dm = DataManagerFactory.create(config)) {
                assertTrue(dm.getRelationalDBNames().contains("sqlite"));
                dm.getRelationalDB("sqlite");
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
