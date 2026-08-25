package cn.arkmillion.core.jdbc;

import cn.arkmillion.core.annotation.Transactional;
import cn.arkmillion.core.condition.Condition;
import cn.arkmillion.core.condition.PageParam;
import cn.arkmillion.core.condition.PageResult;
import cn.arkmillion.core.db.RelationalDB;
import cn.arkmillion.core.enums.SyncMode;
import cn.arkmillion.core.exception.DataManagerException;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionProxyTimeoutTest {

    interface Service {

        void work();

        void slowWork();

        @Transactional(timeoutSeconds = 0)
        void workNoTimeout();
    }

    static class CountingRelationalDB implements RelationalDB {

        final AtomicInteger begins = new AtomicInteger();
        final AtomicInteger commits = new AtomicInteger();
        final AtomicInteger rollbacks = new AtomicInteger();

        boolean inTransaction;

        @Override
        public void beginTransaction() {
            begins.incrementAndGet();
            inTransaction = true;
        }

        @Override
        public void commit() {
            commits.incrementAndGet();
            inTransaction = false;
        }

        @Override
        public void rollback() {
            rollbacks.incrementAndGet();
            inTransaction = false;
        }

        @Override
        public boolean isInTransaction() {
            return inTransaction;
        }

        @Override
        public <T> int insert(T entity) { return 0; }

        @Override
        public <T> int batchInsert(List<T> entities) { return 0; }

        @Override
        public <T> int update(T entity) { return 0; }

        @Override
        public <T> int update(Class<T> clazz, Condition condition, Map<String, Object> updates) { return 0; }

        @Override
        public <T> int delete(Class<T> clazz, Condition condition) { return 0; }

        @Override
        public <T> int deleteById(Class<T> clazz, Object id) { return 0; }

        @Override
        public <T> List<T> select(Class<T> clazz, Condition condition) { return Collections.emptyList(); }

        @Override
        public <T> T selectOne(Class<T> clazz, Condition condition) { return null; }

        @Override
        public <T> T selectById(Class<T> clazz, Object id) { return null; }

        @Override
        public <T> long count(Class<T> clazz, Condition condition) { return 0; }

        @Override
        public <T> PageResult<T> selectPage(Class<T> clazz, Condition condition, PageParam page) {
            return PageResult.empty(page);
        }

        @Override
        public int execute(String sql, Object... params) { return 0; }

        @Override
        public <T> List<T> query(String sql, Class<T> clazz, Object... params) { return Collections.emptyList(); }

        @Override
        public List<Map<String, Object>> queryMap(String sql, Object... params) { return Collections.emptyList(); }

        @Override
        public void syncSchema(Class<?> entityClass, SyncMode mode) { }

        @Override
        public void executeSqlFile(String filePath) { }

        @Override
        public void executeSqlFile(String filePath, Map<String, String> placeholders) { }

        @Override
        public void close() { }
    }

    static class ServiceImpl implements Service {

        long sleepMs;

        @Transactional
        @Override
        public void work() {
        }

        @Transactional(timeoutSeconds = 1)
        @Override
        public void slowWork() {
            if (sleepMs > 0) {
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        @Transactional(timeoutSeconds = 0)
        @Override
        public void workNoTimeout() {
        }
    }

    private static CountingRelationalDB dbInOuterTransaction() {
        CountingRelationalDB db = new CountingRelationalDB();
        db.beginTransaction();
        return db;
    }

    @Test
    void successfulMethodCommits() {
        CountingRelationalDB db = new CountingRelationalDB();
        Service service = TransactionProxy.createProxy(new ServiceImpl(), db, Service.class);

        service.work();

        assertEquals(1, db.begins.get());
        assertEquals(1, db.commits.get());
        assertEquals(0, db.rollbacks.get());
    }

    @Test
    void businessExceptionRollsBack() {
        CountingRelationalDB db = new CountingRelationalDB();
        Service failing = TransactionProxy.createProxy(new ServiceImpl(), db, Service.class);

        assertThrows(IllegalStateException.class, () -> TransactionProxy.createProxy(
                new ServiceImpl() {
                    @Transactional
                    @Override
                    public void work() {
                        throw new IllegalStateException("boom");
                    }
                }, db, Service.class).work());

        assertEquals(1, db.rollbacks.get());
    }

    @Test
    void timeoutRollsBackAndThrows() {
        CountingRelationalDB db = new CountingRelationalDB();
        ServiceImpl impl = new ServiceImpl();
        impl.sleepMs = 1_200;
        Service service = TransactionProxy.createProxy(impl, db, Service.class);

        DataManagerException ex = assertThrows(DataManagerException.class, service::slowWork);
        assertTrue(ex.getMessage().contains("timeout"));
        assertEquals(1, db.rollbacks.get());
        assertEquals(0, db.commits.get());
    }

    @Test
    void zeroTimeoutDisablesCheck() {
        CountingRelationalDB db = new CountingRelationalDB();
        Service service = TransactionProxy.createProxy(new ServiceImpl(), db, Service.class);

        service.workNoTimeout();

        assertEquals(1, db.commits.get());
    }

    @Test
    void outerTransactionIsNotCommittedOrTimed() {
        CountingRelationalDB db = dbInOuterTransaction();
        ServiceImpl impl = new ServiceImpl();
        impl.sleepMs = 1_100;
        Service service = TransactionProxy.createProxy(impl, db, Service.class);

        service.slowWork();

        assertEquals(1, db.begins.get());
        assertEquals(0, db.commits.get());
        assertEquals(0, db.rollbacks.get());
        assertTrue(db.isInTransaction());
        db.rollback();
    }
}
