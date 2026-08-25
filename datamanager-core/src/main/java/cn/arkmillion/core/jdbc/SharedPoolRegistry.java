package cn.arkmillion.core.jdbc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

final class SharedPoolRegistry {

    private final Map<String, PoolEntry> entries = new ConcurrentHashMap<>();

    javax.sql.DataSource acquire(String key, HikariPoolFactory factory) {
        PoolEntry entry = entries.compute(key, (k, existing) -> {
            if (existing != null && !existing.dataSource.isClosed()) {
                existing.references.incrementAndGet();
                return existing;
            }
            return new PoolEntry(factory.create(), new AtomicInteger(1));
        });
        return entry.dataSource;
    }

    boolean release(javax.sql.DataSource dataSource) {
        if (!(dataSource instanceof com.zaxxer.hikari.HikariDataSource)) {
            return false;
        }
        com.zaxxer.hikari.HikariDataSource hikari = (com.zaxxer.hikari.HikariDataSource) dataSource;
        for (Map.Entry<String, PoolEntry> e : entries.entrySet()) {
            if (e.getValue().dataSource != hikari) {
                continue;
            }
            int remaining = e.getValue().references.decrementAndGet();
            if (remaining <= 0) {
                entries.remove(e.getKey());
                if (!hikari.isClosed()) {
                    hikari.close();
                }
                return true;
            }
            return false;
        }
        return false;
    }

    void releaseAll() {
        for (Map.Entry<String, PoolEntry> e : entries.entrySet()) {
            if (!e.getValue().dataSource.isClosed()) {
                e.getValue().dataSource.close();
            }
        }
        entries.clear();
    }

    int size() {
        return entries.size();
    }

    static final class PoolEntry {

        final com.zaxxer.hikari.HikariDataSource dataSource;
        final AtomicInteger references;

        PoolEntry(com.zaxxer.hikari.HikariDataSource dataSource, AtomicInteger references) {
            this.dataSource = dataSource;
            this.references = references;
        }
    }

    @FunctionalInterface
    interface HikariPoolFactory {

        com.zaxxer.hikari.HikariDataSource create();
    }
}
