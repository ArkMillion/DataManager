package cn.arkmillion.core.db;

import java.util.Set;

public interface DataManager extends AutoCloseable {

    RelationalDB getRelationalDB(String name);

    DocumentDB getDocumentDB(String name);

    CacheManager getCacheManager();

    CacheManager getCacheManager(String name);

    Set<String> getRelationalDBNames();

    Set<String> getDocumentDBNames();

    Set<String> getCacheNames();

    @Override
    void close();
}
