package cn.arkmillion.core.factory;

import cn.arkmillion.core.config.DataManagerConfig;
import cn.arkmillion.core.db.RelationalDB;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiInstanceRegistryTest {

    private static RelationalDB dummyDb() {
        return new RelationalDB() {

            @Override
            public <T> int insert(T entity) {
                return 0;
            }

            @Override
            public <T> int batchInsert(List<T> entities) {
                return 0;
            }

            @Override
            public <T> int update(T entity) {
                return 0;
            }

            @Override
            public <T> int update(Class<T> clazz, cn.arkmillion.core.condition.Condition condition, java.util.Map<String, Object> updates) {
                return 0;
            }

            @Override
            public <T> int delete(Class<T> clazz, cn.arkmillion.core.condition.Condition condition) {
                return 0;
            }

            @Override
            public <T> int deleteById(Class<T> clazz, Object id) {
                return 0;
            }

            @Override
            public <T> List<T> select(Class<T> clazz, cn.arkmillion.core.condition.Condition condition) {
                return Collections.emptyList();
            }

            @Override
            public <T> T selectOne(Class<T> clazz, cn.arkmillion.core.condition.Condition condition) {
                return null;
            }

            @Override
            public <T> T selectById(Class<T> clazz, Object id) {
                return null;
            }

            @Override
            public <T> long count(Class<T> clazz, cn.arkmillion.core.condition.Condition condition) {
                return 0;
            }

            @Override
            public <T> cn.arkmillion.core.condition.PageResult<T> selectPage(Class<T> clazz,
                    cn.arkmillion.core.condition.Condition condition, cn.arkmillion.core.condition.PageParam page) {
                return cn.arkmillion.core.condition.PageResult.empty(page);
            }

            @Override
            public int execute(String sql, Object... params) {
                return 0;
            }

            @Override
            public <T> List<T> query(String sql, Class<T> clazz, Object... params) {
                return Collections.emptyList();
            }

            @Override
            public java.util.List<java.util.Map<String, Object>> queryMap(String sql, Object... params) {
                return Collections.emptyList();
            }

            @Override
            public void beginTransaction() {
            }

            @Override
            public void commit() {
            }

            @Override
            public void rollback() {
            }

            @Override
            public boolean isInTransaction() {
                return false;
            }

            @Override
            public void syncSchema(Class<?> entityClass, cn.arkmillion.core.enums.SyncMode mode) {
            }

            @Override
            public void executeSqlFile(String filePath) {
            }

            @Override
            public void executeSqlFile(String filePath, java.util.Map<String, String> placeholders) {
            }

            @Override
            public void close() {
            }
        };
    }

    @Test
    void registersMultipleAliasesForSameType() {
        DataManagerImpl manager = new DataManagerImpl();
        RelationalDB main = dummyDb();
        RelationalDB analytics = dummyDb();
        manager.register("main", main);
        manager.register("analytics", analytics);

        assertTrue(manager.getRelationalDBNames().contains("main"));
        assertTrue(manager.getRelationalDBNames().contains("analytics"));
        assertEquals(2, manager.getRelationalDBNames().size());
        assertTrue(manager.getRelationalDB("main") == main);
        assertTrue(manager.getRelationalDB("analytics") == analytics);
    }

    @Test
    void duplicateAliasRejected() {
        DataManagerImpl manager = new DataManagerImpl();
        manager.register("main", dummyDb());
        assertThrows(cn.arkmillion.core.exception.DataManagerException.class,
                () -> manager.register("main", dummyDb()));
    }

    @Test
    void blankAliasRejected() {
        DataManagerImpl manager = new DataManagerImpl();
        assertThrows(cn.arkmillion.core.exception.DataManagerException.class,
                () -> manager.register("  ", dummyDb()));
    }

    @Test
    void cacheDefaultIsFirstRegisteredUnlessOverridden() {
        DataManagerImpl manager = new DataManagerImpl();
        cn.arkmillion.core.db.CacheManager a = dummyCache();
        cn.arkmillion.core.db.CacheManager b = dummyCache();
        manager.registerCache("session", a, false);
        manager.registerCache("rate-limit", b, true);

        assertTrue(manager.getCacheManager() == b);
        assertTrue(manager.getCacheManager("session") == a);
        assertEquals(2, manager.getCacheNames().size());
    }

    private static cn.arkmillion.core.db.CacheManager dummyCache() {
        return new cn.arkmillion.core.db.CacheManager() {

            @Override public void set(String key, String value) { }
            @Override public void set(String key, String value, java.time.Duration ttl) { }
            @Override public void set(String key, String value, long seconds) { }
            @Override public String get(String key) { return null; }
            @Override public void del(String key) { }
            @Override public boolean exists(String key) { return false; }
            @Override public boolean expire(String key, long seconds) { return false; }
            @Override public long ttl(String key) { return 0; }
            @Override public void hSet(String key, String field, String value) { }
            @Override public String hGet(String key, String field) { return null; }
            @Override public java.util.Map<String, String> hGetAll(String key) { return Collections.emptyMap(); }
            @Override public void hDel(String key, String... fields) { }
            @Override public boolean hExists(String key, String field) { return false; }
            @Override public void lPush(String key, String... values) { }
            @Override public void rPush(String key, String... values) { }
            @Override public String lPop(String key) { return null; }
            @Override public String rPop(String key) { return null; }
            @Override public java.util.List<String> lRange(String key, long start, long end) { return Collections.emptyList(); }
            @Override public void sAdd(String key, String... members) { }
            @Override public void sRem(String key, String... members) { }
            @Override public java.util.Set<String> sMembers(String key) { return Collections.emptySet(); }
            @Override public boolean sIsMember(String key, String member) { return false; }
            @Override public void zAdd(String key, double score, String member) { }
            @Override public void zAdd(String key, java.util.Map<String, Double> scoreMembers) { }
            @Override public java.util.Set<String> zRange(String key, long start, long end) { return Collections.emptySet(); }
            @Override public java.util.Set<String> zRangeByScore(String key, double min, double max) { return Collections.emptySet(); }
            @Override public long zRem(String key, String... members) { return 0; }
            @Override public <T> void setObject(String key, T obj) { }
            @Override public <T> void setObject(String key, T obj, java.time.Duration ttl) { }
            @Override public <T> T getObject(String key, Class<T> clazz) { return null; }
            @Override public <T> T getObject(String key, com.fasterxml.jackson.core.type.TypeReference<T> typeRef) { return null; }
            @Override public void mSet(java.util.Map<String, String> keyValues) { }
            @Override public java.util.List<String> mGet(String... keys) { return Collections.emptyList(); }
            @Override public void del(String... keys) { }
            @Override public boolean tryLock(String lockKey, String requestId, long expireSeconds) { return false; }
            @Override public boolean releaseLock(String lockKey, String requestId) { return false; }
            @Override public java.util.Set<String> keys(String pattern) { return Collections.emptySet(); }
            @Override public long dbSize() { return 0; }
            @Override public void flushDb() { }
            @Override public void flushAll() { }
            @Override public void close() { }
        };
    }
}
