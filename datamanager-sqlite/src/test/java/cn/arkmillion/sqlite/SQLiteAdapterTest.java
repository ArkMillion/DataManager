package cn.arkmillion.sqlite;

import cn.arkmillion.core.annotation.AutoIncrement;
import cn.arkmillion.core.annotation.Column;
import cn.arkmillion.core.annotation.Id;
import cn.arkmillion.core.annotation.Index;
import cn.arkmillion.core.annotation.Table;
import cn.arkmillion.core.condition.Condition;
import cn.arkmillion.core.condition.PageParam;
import cn.arkmillion.core.condition.PageResult;
import cn.arkmillion.core.enums.DataType;
import cn.arkmillion.core.enums.GenerationType;
import cn.arkmillion.core.enums.IndexType;
import cn.arkmillion.core.enums.SyncMode;
import cn.arkmillion.core.exception.DataManagerException;
import cn.arkmillion.core.jdbc.ConnectionPool;
import cn.arkmillion.core.schema.SchemaSyncPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.arkmillion.core.condition.Condition.where;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLiteAdapterTest {

    @Table(name = "it_user")
    public static class ItUser {

        @Id(strategy = GenerationType.AUTO)
        @AutoIncrement
        private Long id;

        @Column(name = "user_name", length = 64, nullable = false)
        private String username;

        @Column(name = "email", length = 128)
        @Index(type = IndexType.UNIQUE, name = "uk_it_email")
        private String email;

        @Column(name = "balance", type = DataType.DECIMAL, precision = 18, scale = 2)
        private BigDecimal balance;

        @Column(name = "active", type = DataType.BOOLEAN, nullable = false)
        private Boolean active;

        @Column(name = "created_at")
        private LocalDateTime createdAt;

        @Column(name = "birthday")
        private LocalDate birthday;

        public ItUser() {
        }

        public ItUser(String username, String email) {
            this.username = username;
            this.email = email;
            this.active = true;
            this.balance = new BigDecimal("100.50");
            this.createdAt = LocalDateTime.of(2026, 8, 25, 10, 30);
            this.birthday = LocalDate.of(1990, 1, 15);
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public BigDecimal getBalance() {
            return balance;
        }

        public void setBalance(BigDecimal balance) {
            this.balance = balance;
        }

        public Boolean getActive() {
            return active;
        }

        public void setActive(Boolean active) {
            this.active = active;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public LocalDate getBirthday() {
            return birthday;
        }

        public void setBirthday(LocalDate birthday) {
            this.birthday = birthday;
        }
    }

    private Path dbFile;
    private SQLiteAdapter db;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = Files.createTempFile("sqlite-it", ".db");
        Files.deleteIfExists(dbFile);
        db = new SQLiteAdapter(new cn.arkmillion.core.config.SQLiteConfig(dbFile.toString()));
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.close();
        }
        ConnectionPool.shutdownAll();
    }

    @Test
    void schemaCreateValidateAndDropCreate() {
        SchemaSyncPolicy.clearConfirmation();
        db.syncSchema(ItUser.class, SyncMode.CREATE);
        db.syncSchema(ItUser.class, SyncMode.CREATE);
        db.syncSchema(ItUser.class, SyncMode.VALIDATE);

        db.syncSchema(ItUser.class, SyncMode.UPDATE);
        db.syncSchema(ItUser.class, SyncMode.VALIDATE);
    }

    @Test
    void crudLifecycleWithGeneratedKeys() {
        db.syncSchema(ItUser.class, SyncMode.CREATE);

        ItUser user = new ItUser("alice", "alice@it.com");
        int inserted = db.insert(user);
        assertEquals(1, inserted);
        assertNotNull(user.getId());

        ItUser loaded = db.selectById(ItUser.class, user.getId());
        assertEquals("alice", loaded.getUsername());
        assertEquals("alice@it.com", loaded.getEmail());
        assertEquals(Boolean.TRUE, loaded.getActive());
        assertEquals(0, loaded.getBalance().compareTo(new BigDecimal("100.50")));
        assertEquals(LocalDateTime.of(2026, 8, 25, 10, 30), loaded.getCreatedAt());
        assertEquals(LocalDate.of(1990, 1, 15), loaded.getBirthday());
    }

    @Test
    void updateEntityByPkSkipsNulls() {
        db.syncSchema(ItUser.class, SyncMode.CREATE);
        ItUser user = new ItUser("bob", "bob@it.com");
        db.insert(user);

        ItUser patch = new ItUser();
        patch.setId(user.getId());
        patch.setUsername("bobby");
        int affected = db.update(patch);
        assertEquals(1, affected);

        ItUser reloaded = db.selectById(ItUser.class, user.getId());
        assertEquals("bobby", reloaded.getUsername());
        assertEquals("bob@it.com", reloaded.getEmail());
        assertTrue(reloaded.getActive());
    }

    @Test
    void conditionalUpdateDeleteAndCount() {
        db.syncSchema(ItUser.class, SyncMode.CREATE);
        for (int i = 1; i <= 5; i++) {
            ItUser u = new ItUser("user" + i, "u" + i + "@it.com");
            u.setActive(i % 2 == 1);
            db.insert(u);
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("active", false);
        int updated = db.update(ItUser.class, where("active").eq(true).build(), updates);
        assertEquals(3, updated);

        long inactive = db.count(ItUser.class, where("active").eq(false).build());
        assertEquals(5, inactive);

        int deleted = db.delete(ItUser.class,
                where("email").like("%1@it.com").or("email").like("%3@it.com").build());
        assertEquals(2, deleted);
        assertEquals(3, db.count(ItUser.class, null));
    }

    @Test
    void conditionOperatorsCovered() {
        db.syncSchema(ItUser.class, SyncMode.CREATE);
        ItUser a = new ItUser("ann", "ann@x.com");
        ItUser b = new ItUser("ben", "ben@x.com");
        b.setBalance(new BigDecimal("500.00"));
        db.insert(a);
        db.insert(b);

        assertEquals(1, db.select(ItUser.class, where("balance").gt(new BigDecimal("200")).build()).size());
        assertEquals(1, db.select(ItUser.class, where("username").in(Arrays.asList("ann", "zed")).build()).size());
        assertEquals(1, db.select(ItUser.class, where("id").between(a.getId(), a.getId()).build()).size());
        assertNull(db.selectOne(ItUser.class, where("username").eq("ghost").build()));
    }

    @Test
    void paginationReturnsOrderedSlices() {
        db.syncSchema(ItUser.class, SyncMode.CREATE);
        List<ItUser> users = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            users.add(new ItUser("page" + i, "page" + i + "@it.com"));
        }
        db.batchInsert(users);
        assertEquals(7, db.count(ItUser.class, null));

        PageResult<ItUser> page1 = db.selectPage(ItUser.class, null, PageParam.of(1, 3));
        assertEquals(7, page1.getTotal());
        assertEquals(3, page1.getRecords().size());
        assertEquals(3, page1.getPages());
        assertTrue(page1.hasNext());
        assertEquals("page1", page1.getRecords().get(0).getUsername());

        PageResult<ItUser> page3 = db.selectPage(ItUser.class, Condition.empty(), PageParam.of(3, 3));
        assertEquals(1, page3.getRecords().size());

        Condition sorted = Condition.where("username").ne("").orderBy("username", false);
        PageResult<ItUser> descPage = db.selectPage(ItUser.class, sorted, PageParam.of(1, 2));
        assertEquals("page7", descPage.getRecords().get(0).getUsername());
    }

    @Test
    void transactionRollbackRestoresState() {
        db.syncSchema(ItUser.class, SyncMode.CREATE);
        db.beginTransaction();
        try {
            ItUser ghost = new ItUser("ghost", "ghost@it.com");
            db.insert(ghost);
            Map<String, Object> updates = new HashMap<>();
            updates.put("username", "hacked");
            db.update(ItUser.class, where("id").gt(0).build(), updates);
        } finally {
            db.rollback();
        }
        assertEquals(0, db.count(ItUser.class, null));

        assertFalse(db.isInTransaction());
    }

    @Test
    void transactionCommitPersists() {
        db.syncSchema(ItUser.class, SyncMode.CREATE);
        db.beginTransaction();
        try {
            db.insert(new ItUser("kept", "kept@it.com"));
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            throw e;
        }
        assertEquals(1, db.count(ItUser.class, null));
    }

    @Test
    void nestedBeginTransactionRejected() {
        db.syncSchema(ItUser.class, SyncMode.CREATE);
        db.beginTransaction();
        try {
            assertThrows(DataManagerException.class, () -> db.beginTransaction());
        } finally {
            db.rollback();
        }
    }

    @Test
    void commitWithoutTransactionThrows() {
        assertThrows(DataManagerException.class, () -> db.commit());
        assertThrows(DataManagerException.class, () -> db.rollback());
    }

    @Test
    void executeSqlFileFromClasspathAndFilesystem() throws Exception {
        db.syncSchema(ItUser.class, SyncMode.CREATE);

        Path sqlFile = Files.createTempFile("seed", ".sql");
        Files.write(sqlFile, Arrays.asList(
                "-- seed script",
                "INSERT INTO it_user (user_name, email, active, balance) VALUES ('seedA', 'a@seed.com', 1, 10.00);",
                "/* block */",
                "INSERT INTO it_user (user_name, email, active, balance) VALUES ('seedB', 'b@seed.com', 1, 20.00);"
        ));
        db.executeSqlFile(sqlFile.toString());
        assertEquals(2, db.count(ItUser.class, null));
        Files.deleteIfExists(sqlFile);
    }

    @Test
    void rawSqlExecuteQueryAndQueryMap() {
        db.syncSchema(ItUser.class, SyncMode.CREATE);
        int created = db.execute("CREATE TABLE IF NOT EXISTS plain_t (v TEXT)");
        assertEquals(0, created);

        db.execute("INSERT INTO plain_t VALUES (?)", "hello");
        List<String> values = db.query("SELECT v FROM plain_t", String.class);
        assertEquals(1, values.size());
        assertEquals("hello", values.get(0));

        List<Map<String, Object>> maps = db.queryMap("SELECT v FROM plain_t");
        assertEquals("hello", maps.get(0).get("v"));
    }

    @Test
    void deleteWithoutConditionRefused() {
        db.syncSchema(ItUser.class, SyncMode.CREATE);
        assertThrows(DataManagerException.class, () -> db.delete(ItUser.class, Condition.empty()));
        assertThrows(DataManagerException.class,
                () -> db.update(ItUser.class, Condition.empty(), new HashMap<>()));
    }

    @Test
    void batchInsertRollsBackAtomicallyOnFailure() {
        db.syncSchema(ItUser.class, SyncMode.CREATE);
        List<ItUser> users = new ArrayList<>();
        users.add(new ItUser("ok1", "ok1@it.com"));
        users.add(null);
        users.add(new ItUser("ok2", "ok2@it.com"));

        assertThrows(Exception.class, () -> db.batchInsert(users));
        assertEquals(0, db.count(ItUser.class, null));
    }

    @Test
    void uuidPrimaryKeyStrategySupported() {
        db.syncSchema(UuidEntity.class, SyncMode.CREATE);
        UuidEntity e = new UuidEntity();
        e.setName("with-uuid");
        db.insert(e);
        assertNotNull(e.getId());
        UuidEntity loaded = db.selectById(UuidEntity.class, e.getId());
        assertEquals("with-uuid", loaded.getName());
    }

    @Table(name = "uuid_entity")
    public static class UuidEntity {

        @Id(strategy = cn.arkmillion.core.enums.GenerationType.UUID)
        @Column(name = "uid", length = 36)
        private String id;

        @Column(nullable = false)
        private String name;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
