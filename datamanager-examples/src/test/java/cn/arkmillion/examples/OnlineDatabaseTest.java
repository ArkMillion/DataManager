package cn.arkmillion.examples;

import cn.arkmillion.core.annotation.Column;
import cn.arkmillion.core.annotation.Document;
import cn.arkmillion.core.annotation.DocumentId;
import cn.arkmillion.core.annotation.Id;
import cn.arkmillion.core.annotation.Index;
import cn.arkmillion.core.annotation.Indexed;
import cn.arkmillion.core.annotation.Table;
import cn.arkmillion.core.condition.Condition;
import cn.arkmillion.core.config.DataManagerConfig;
import cn.arkmillion.core.db.CacheManager;
import cn.arkmillion.core.db.DataManager;
import cn.arkmillion.core.db.DocumentDB;
import cn.arkmillion.core.db.RelationalDB;
import cn.arkmillion.core.db.Subscription;
import cn.arkmillion.core.enums.GenerationType;
import cn.arkmillion.core.enums.IndexType;
import cn.arkmillion.core.enums.SyncMode;
import cn.arkmillion.core.factory.DataManagerFactory;
import cn.arkmillion.core.filter.Filter;
import cn.arkmillion.core.filter.Update;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static cn.arkmillion.core.condition.Condition.where;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("online")
class OnlineDatabaseTest {

    private static final Properties PROPS = new Properties();
    private static boolean loaded = false;

    @BeforeAll
    static void loadProperties() throws Exception {
        Path local = Paths.get("online-test.properties");
        Path parent = Paths.get("..", "online-test.properties");
        File file = Files.exists(local) ? local.toFile()
                : (Files.exists(parent) ? parent.toFile() : null);
        if (file == null) {
            return;
        }
        try (InputStream in = new FileInputStream(file)) {
            PROPS.load(in);
        }
        loaded = !PROPS.isEmpty();
    }

    private static void assumeConfigured(String... keys) {
        assumeTrue(loaded, "online-test.properties not provided - online tests skipped");
        for (String key : keys) {
            String value = PROPS.getProperty(key);
            assumeTrue(value != null && !value.trim().isEmpty(), "missing property: " + key);
        }
    }

    private static String require(String key) {
        return PROPS.getProperty(key).trim();
    }

    public abstract static class BaseUser {

        @Id(strategy = GenerationType.AUTO)
        @cn.arkmillion.core.annotation.AutoIncrement
        Long id;

        @Column(name = "user_name", length = 64, nullable = false)
        String username;

        @Column(name = "email", length = 128, nullable = false)
        @Index(type = IndexType.UNIQUE, name = "uk_it_email")
        String email;

        @Column(name = "balance", type = cn.arkmillion.core.enums.DataType.DECIMAL,
                precision = 18, scale = 2)
        BigDecimal balance;

        @Column(name = "created_at", nullable = false)
        LocalDateTime createdAt;

        BaseUser(String username, String email, BigDecimal balance) {
            this.username = username;
            this.email = email;
            this.balance = balance;
            this.createdAt = LocalDateTime.now();
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

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }
    }

    @Table(name = "it_dm_user_mysql")
    public static class MysqlUser extends BaseUser {

        public MysqlUser() {
            super("alice", "alice@it.com", new BigDecimal("100.50"));
        }
    }

    @Table(name = "it_dm_user_pg")
    public static class PgUser extends BaseUser {

        public PgUser() {
            super("alice", "alice@it.com", new BigDecimal("100.50"));
        }
    }

    @Document(collection = "dm_it_logs")
    public static class ItLog {

        @DocumentId
        private String id;

        @Indexed(direction = cn.arkmillion.core.enums.IndexDirection.DESC)
        private Instant ts;

        @Indexed
        private String level;

        private Map<String, Object> payload;

        public ItLog() {
        }

        public ItLog(String level, Instant ts, Map<String, Object> payload) {
            this.level = level;
            this.ts = ts;
            this.payload = payload;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public Instant getTs() {
            return ts;
        }

        public void setTs(Instant ts) {
            this.ts = ts;
        }

        public String getLevel() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
        }

        public Map<String, Object> getPayload() {
            return payload;
        }

        public void setPayload(Map<String, Object> payload) {
            this.payload = payload;
        }
    }

    public static class SessionUser {

        private String name;
        private int hits;

        public SessionUser() {
        }

        public SessionUser(String name, int hits) {
            this.name = name;
            this.hits = hits;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getHits() {
            return hits;
        }

        public void setHits(int hits) {
            this.hits = hits;
        }
    }

    @Test
    void mysqlOnlineCrud() {
        assumeConfigured("mysql.url", "mysql.username", "mysql.password");
        DataManagerConfig config = DataManagerConfig.builder()
                .mysql(require("mysql.url"), require("mysql.username"), require("mysql.password"))
                .alias("it-mysql")
                .poolSize(4)
                .maxLifetime(600_000)
                .build()
                .build();

        try (DataManager dm = DataManagerFactory.create(config)) {
            runRelationalFlow(dm, "it-mysql", "it_dm_user_mysql", MysqlUser.class, MysqlUser::new);
        }
    }

    @Test
    void postgresOnlineCrud() {
        assumeConfigured("postgres.url", "postgres.username", "postgres.password");
        DataManagerConfig config = DataManagerConfig.builder()
                .postgres(require("postgres.url"), require("postgres.username"), require("postgres.password"))
                .alias("it-postgres")
                .schema("public")
                .poolSize(4)
                .maxLifetime(600_000)
                .build()
                .build();

        try (DataManager dm = DataManagerFactory.create(config)) {
            runRelationalFlow(dm, "it-postgres", "it_dm_user_pg", PgUser.class, PgUser::new);
        }
    }

    private static <T extends BaseUser> void runRelationalFlow(
            DataManager dm, String alias, String tableName, Class<T> type, Supplier<T> fresh) {
        RelationalDB db = dm.getRelationalDB(alias);

        db.execute("DROP TABLE IF EXISTS " + tableName);
        db.syncSchema(type, SyncMode.CREATE);

        T alice = fresh.get();
        assertEquals(1, db.insert(alice));
        assertNotNull(alice.getId());
        assertTrue(alice.getId() > 0);

        T loaded = db.selectOne(type, where("email").eq(alice.getEmail()).build());
        assertNotNull(loaded);
        assertEquals("alice", loaded.getUsername());
        assertEquals(0, loaded.getBalance().compareTo(new BigDecimal("100.50")));

        loaded.setUsername("alice2");
        assertEquals(1, db.update(loaded));
        assertEquals("alice2", db.selectOne(type, where("email").eq(alice.getEmail()).build()).getUsername());

        assertEquals(1, db.count(type, where("balance").between(
                new BigDecimal("0"), new BigDecimal("1000")).build()));
        assertEquals(1, db.count(type, where("username").in(Arrays.asList("alice2", "ghost")).build()));

        long before = db.count(type, null);
        db.beginTransaction();
        try {
            T ghost = type.getDeclaredConstructor().newInstance();
            ghost.setUsername("ghost");
            ghost.setEmail("ghost-" + UUID.randomUUID() + "@it.com");
            ghost.setBalance(new BigDecimal("1"));
            assertEquals(1, db.insert(ghost));
            db.rollback();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        } catch (RuntimeException e) {
            db.rollback();
            throw e;
        }
        assertEquals(before, db.count(type, null));

        db.beginTransaction();
        T kept;
        try {
            kept = type.getDeclaredConstructor().newInstance();
            kept.setUsername("kept");
            kept.setEmail("kept-" + UUID.randomUUID() + "@it.com");
            kept.setBalance(new BigDecimal("2"));
            assertEquals(1, db.insert(kept));
            db.commit();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        } catch (RuntimeException e) {
            db.rollback();
            throw e;
        }
        assertEquals(before + 1, db.count(type, null));

        assertEquals(1, db.deleteById(type, kept.getId()));
        assertEquals(before, db.count(type, null));

        cn.arkmillion.core.condition.PageResult<T> page =
                db.selectPage(type, Condition.empty(), cn.arkmillion.core.condition.PageParam.of(1, 10));
        assertEquals(before, page.getTotal());
        assertEquals(before, page.getRecords().size());

        db.execute("DROP TABLE IF EXISTS " + tableName);
    }

    @Test
    void mongoOnlineCrud() {
        assumeConfigured("mongo.uri");
        DataManagerConfig config = DataManagerConfig.builder()
                .mongo(require("mongo.uri"))
                .alias("it-mongo")
                .database("test")
                .applicationName("dm-it")
                .build()
                .build();

        try (DataManager dm = DataManagerFactory.create(config)) {
            DocumentDB mongo = dm.getDocumentDB("it-mongo");

            mongo.delete(ItLog.class, Filter.empty());
            mongo.syncSchema(ItLog.class, SyncMode.CREATE);

            ItLog error = new ItLog("ERROR", Instant.now(),
                    Collections.<String, Object>singletonMap("action", "login"));
            mongo.insert(error);
            assertNotNull(error.getId());

            ItLog info1 = new ItLog("INFO", Instant.now(), new HashMap<String, Object>());
            ItLog info2 = new ItLog("INFO", Instant.now(), new HashMap<String, Object>());
            mongo.insertMany(Arrays.asList(info1, info2));

            assertEquals(1, mongo.find(ItLog.class,
                    Filter.where("level").eq("ERROR")).size());

            long modified = mongo.update(ItLog.class,
                    Filter.where("level").eq("INFO"),
                    Update.builder().set("payload.status", "done").inc("payload.retry", 1).build());
            assertEquals(2L, modified);

            ItLog back = mongo.findOne(ItLog.class, Filter.where("level").eq("INFO"));
            assertNotNull(back.getPayload());
            assertEquals(1, ((Number) back.getPayload().get("retry")).intValue());
            assertEquals("done", back.getPayload().get("status"));

            assertEquals(3L, mongo.count(ItLog.class, Filter.empty()));

            cn.arkmillion.core.condition.PageResult<ItLog> page =
                    mongo.findPage(ItLog.class, Filter.where("level").ne("NONE"),
                            cn.arkmillion.core.condition.PageParam.of(1, 2));
            assertEquals(3L, page.getTotal());
            assertEquals(2, page.getRecords().size());

            List<ItLog> counted = mongo.aggregate(ItLog.class, Arrays.asList(
                    cn.arkmillion.core.filter.AggregationStage.match(Filter.where("level").eq("INFO")),
                    cn.arkmillion.core.filter.AggregationStage.count("total")));
            assertEquals(1, counted.size());

            assertEquals(3L, mongo.delete(ItLog.class, Filter.empty()));
            assertEquals(0L, mongo.count(ItLog.class, Filter.empty()));

            mongo.updateOne(ItLog.class, Filter.where("id").eq("missing"),
                    Update.builder().set("x", 1).build());

        }
    }

    @Test
    void redisOnlineOps() {
        assumeConfigured("redis.host", "redis.port");
        DataManagerConfig config = DataManagerConfig.builder()
                .redis(require("redis.host"), Integer.parseInt(require("redis.port")))
                .alias("it-redis")
                .database(PROPS.containsKey("redis.database") ? Integer.parseInt(require("redis.database")) : 0)
                .password(PROPS.getProperty("redis.password") == null ? null : require("redis.password"))
                .timeout(4000)
                .build()
                .build();

        try (DataManager dm = DataManagerFactory.create(config)) {
            CacheManager redis = dm.getCacheManager("it-redis");
            String prefix = "dm-it:" + UUID.randomUUID().toString().substring(0, 8) + ":";

            try {
                redis.set(prefix + "k", "v1");
                assertEquals("v1", redis.get(prefix + "k"));

                redis.set(prefix + "ttl", "x", Duration.ofSeconds(90));
                long ttl = redis.ttl(prefix + "ttl");
                assertTrue(ttl > 0 && ttl <= 90);

                assertTrue(redis.expire(prefix + "k", 300));
                assertTrue(redis.exists(prefix + "k"));
                redis.del(prefix + "k");
                assertEquals(false, redis.exists(prefix + "k"));

                redis.hSet(prefix + "h", "name", "alice");
                redis.hSet(prefix + "h", "role", "admin");
                assertEquals("alice", redis.hGet(prefix + "h", "name"));
                assertEquals(2, redis.hGetAll(prefix + "h").size());
                assertTrue(redis.hExists(prefix + "h", "role"));
                redis.hDel(prefix + "h", "role");
                assertEquals(false, redis.hExists(prefix + "h", "role"));

                redis.rPush(prefix + "list", "a", "b", "c");
                assertEquals(Arrays.asList("a", "b", "c"), redis.lRange(prefix + "list", 0, -1));
                assertEquals("a", redis.lPop(prefix + "list"));
                assertEquals("c", redis.rPop(prefix + "list"));

                redis.sAdd(prefix + "set", "t1", "t2");
                assertEquals(2, redis.sMembers(prefix + "set").size());
                assertTrue(redis.sIsMember(prefix + "set", "t1"));
                redis.sRem(prefix + "set", "t1");

                redis.zAdd(prefix + "z", 98.5, "playerA");
                Map<String, Double> scores = new HashMap<>();
                scores.put("playerB", 91.0);
                redis.zAdd(prefix + "z", scores);
                assertEquals(Arrays.asList("playerB", "playerA"),
                        new java.util.ArrayList<>(redis.zRange(prefix + "z", 0, -1)));
                assertEquals(Collections.singleton("playerA"),
                        redis.zRangeByScore(prefix + "z", 95, 100));
                assertEquals(1L, redis.zRem(prefix + "z", "playerB"));

                Map<String, String> batch = new HashMap<>();
                batch.put(prefix + "m1", "1");
                batch.put(prefix + "m2", "2");
                redis.mSet(batch);
                List<String> got = redis.mGet(prefix + "m1", prefix + "m2", prefix + "missing");
                assertEquals("1", got.get(0));
                assertEquals("2", got.get(1));
                assertEquals(null, got.get(2));

                SessionUser session = new SessionUser("alice", 3);
                redis.setObject(prefix + "obj", session, Duration.ofSeconds(60));
                SessionUser cached = redis.getObject(prefix + "obj", SessionUser.class);
                assertEquals("alice", cached.getName());
                assertEquals(3, cached.getHits());
                assertTrue(redis.ttl(prefix + "obj") > 0);

                String lockKey = prefix + "lock";
                String requestId = UUID.randomUUID().toString();
                assertTrue(redis.tryLock(lockKey, requestId, 10));
                assertEquals(false, redis.tryLock(lockKey, "other", 10));
                assertEquals(false, redis.releaseLock(lockKey, "other"));
                assertTrue(redis.releaseLock(lockKey, requestId));
                assertTrue(redis.tryLock(lockKey, "other", 5));
                redis.del(lockKey);
            } finally {
                java.util.Set<String> mine = redis.keys(prefix + "*");
                if (!mine.isEmpty()) {
                    redis.del(mine.toArray(new String[0]));
                }
            }
        }
    }

    @Test
    void redisOnlinePubSub() throws Exception {
        assumeConfigured("redis.host", "redis.port");
        DataManagerConfig config = DataManagerConfig.builder()
                .redis(require("redis.host"), Integer.parseInt(require("redis.port")))
                .alias("it-redis-pubsub")
                .database(PROPS.containsKey("redis.database") ? Integer.parseInt(require("redis.database")) : 0)
                .password(PROPS.getProperty("redis.password") == null ? null : require("redis.password"))
                .timeout(4000)
                .build()
                .build();

        try (DataManager dm = DataManagerFactory.create(config)) {
            CacheManager redis = dm.getCacheManager("it-redis-pubsub");
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            String channel = "dm-it:chan:" + suffix;
            String patChannel = "dm-it:pat:" + suffix + ":evt";

            java.util.concurrent.BlockingQueue<String> plainMessages =
                    new java.util.concurrent.LinkedBlockingQueue<>();
            java.util.concurrent.BlockingQueue<String> patternMessages =
                    new java.util.concurrent.LinkedBlockingQueue<>();

            Subscription s1 = null;
            Subscription s2 = null;
            try {
                s1 = redis.subscribe(channel, (c, m) -> plainMessages.offer(m));
                waitForSubscriber(redis, channel);
                assertEquals("probe", plainMessages.poll(5, TimeUnit.SECONDS));

                assertEquals(1L, redis.publish(channel, "hello"));
                assertEquals("hello", plainMessages.poll(5, TimeUnit.SECONDS));

                SessionUser payload = new SessionUser("bob", 7);
                assertEquals(1L, redis.publishObject(channel, payload));
                String jsonMessage = plainMessages.poll(5, TimeUnit.SECONDS);
                assertNotNull(jsonMessage);
                assertTrue(jsonMessage.startsWith("{"));
                assertTrue(jsonMessage.contains("\"name\":\"bob\""));

                s2 = redis.pSubscribe("dm-it:pat:" + suffix + ":*", (c, m) -> patternMessages.offer(m));
                waitForSubscriber(redis, patChannel);
                assertEquals("probe", patternMessages.poll(5, TimeUnit.SECONDS));

                assertEquals(1L, redis.publish(patChannel, "e1"));
                assertEquals("e1", patternMessages.poll(5, TimeUnit.SECONDS));
                assertEquals(1L, redis.publish(patChannel, "e2"));
                assertEquals("e2", patternMessages.poll(5, TimeUnit.SECONDS));

                assertTrue(s1.isSubscribed());
                assertTrue(s2.isSubscribed());
                s1.unsubscribe();
                assertEquals(false, s1.isSubscribed());
                assertEquals(0L, redis.publish(channel, "after-unsub"));
            } finally {
                if (s1 != null) {
                    s1.close();
                }
                if (s2 != null) {
                    s2.close();
                }
            }
        }
    }

    private static void waitForSubscriber(CacheManager redis, String probeChannel) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (redis.publish(probeChannel, "probe") == 0) {
            assertTrue(System.currentTimeMillis() < deadline, "subscriber did not register in time");
            Thread.sleep(50);
        }
    }
}
