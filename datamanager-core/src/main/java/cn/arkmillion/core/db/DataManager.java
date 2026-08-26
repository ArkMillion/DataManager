package cn.arkmillion.core.db;

import cn.arkmillion.core.mq.MessagingManager;

import java.util.Set;

public interface DataManager extends AutoCloseable {

    RelationalDB getRelationalDB(String name);

    DocumentDB getDocumentDB(String name);

    CacheManager getCacheManager();

    CacheManager getCacheManager(String name);

    MessagingManager getMessaging();

    MessagingManager getMessaging(String name);

    Set<String> getRelationalDBNames();

    Set<String> getDocumentDBNames();

    Set<String> getCacheNames();

    Set<String> getMessagingNames();

    @Override
    void close();
}
