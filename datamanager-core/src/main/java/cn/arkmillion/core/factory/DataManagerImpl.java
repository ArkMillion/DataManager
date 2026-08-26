package cn.arkmillion.core.factory;

import cn.arkmillion.core.db.CacheManager;
import cn.arkmillion.core.db.DataManager;
import cn.arkmillion.core.db.DocumentDB;
import cn.arkmillion.core.db.RelationalDB;
import cn.arkmillion.core.exception.DataManagerException;
import cn.arkmillion.core.mq.MessagingManager;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DataManagerImpl implements DataManager {

    private final Map<String, RelationalDB> relationalDBs = new ConcurrentHashMap<>();
    private final Map<String, DocumentDB> documentDBs = new ConcurrentHashMap<>();
    private final Map<String, CacheManager> cacheManagers = new ConcurrentHashMap<>();
    private final Map<String, MessagingManager> messagingManagers = new ConcurrentHashMap<>();
    private volatile String defaultCacheName;
    private volatile String defaultMessagingName;

    void register(String name, RelationalDB db) {
        requireName(name);
        RelationalDB existing = relationalDBs.putIfAbsent(name, db);
        if (existing != null) {
            throw new DataManagerException("RelationalDB '" + name + "' already registered");
        }
    }

    void register(String name, DocumentDB db) {
        requireName(name);
        DocumentDB existing = documentDBs.putIfAbsent(name, db);
        if (existing != null) {
            throw new DataManagerException("DocumentDB '" + name + "' already registered");
        }
    }

    void registerCache(String name, CacheManager cache, boolean makeDefault) {
        requireName(name);
        CacheManager existing = cacheManagers.putIfAbsent(name, cache);
        if (existing != null) {
            throw new DataManagerException("CacheManager '" + name + "' already registered");
        }
        if (makeDefault || defaultCacheName == null) {
            defaultCacheName = name;
        }
    }

    void registerMessaging(String name, MessagingManager messaging, boolean makeDefault) {
        requireName(name);
        MessagingManager existing = messagingManagers.putIfAbsent(name, messaging);
        if (existing != null) {
            throw new DataManagerException("MessagingManager '" + name + "' already registered");
        }
        if (makeDefault || defaultMessagingName == null) {
            defaultMessagingName = name;
        }
    }

    private static void requireName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new DataManagerException("Instance alias must not be blank");
        }
    }

    @Override
    public RelationalDB getRelationalDB(String name) {
        RelationalDB db = relationalDBs.get(name);
        if (db == null) {
            throw new DataManagerException("RelationalDB '" + name + "' not found. Registered: " + relationalDBs.keySet());
        }
        return db;
    }

    @Override
    public DocumentDB getDocumentDB(String name) {
        DocumentDB db = documentDBs.get(name);
        if (db == null) {
            throw new DataManagerException("DocumentDB '" + name + "' not found. Registered: " + documentDBs.keySet());
        }
        return db;
    }

    @Override
    public CacheManager getCacheManager() {
        return getCacheManager(defaultCacheName);
    }

    @Override
    public CacheManager getCacheManager(String name) {
        CacheManager cache = name == null ? null : cacheManagers.get(name);
        if (cache == null) {
            throw new DataManagerException("CacheManager '" + name + "' not found. Registered: " + cacheManagers.keySet());
        }
        return cache;
    }

    @Override
    public Set<String> getRelationalDBNames() {
        return Collections.unmodifiableSet(relationalDBs.keySet());
    }

    @Override
    public Set<String> getDocumentDBNames() {
        return Collections.unmodifiableSet(documentDBs.keySet());
    }

    @Override
    public Set<String> getCacheNames() {
        return Collections.unmodifiableSet(cacheManagers.keySet());
    }

    @Override
    public MessagingManager getMessaging() {
        return getMessaging(defaultMessagingName);
    }

    @Override
    public MessagingManager getMessaging(String name) {
        MessagingManager messaging = name == null ? null : messagingManagers.get(name);
        if (messaging == null) {
            throw new DataManagerException("MessagingManager '" + name + "' not found. Registered: "
                    + messagingManagers.keySet());
        }
        return messaging;
    }

    @Override
    public Set<String> getMessagingNames() {
        return Collections.unmodifiableSet(messagingManagers.keySet());
    }

    @Override
    public void close() {
        RuntimeException first = null;
        for (RelationalDB db : relationalDBs.values()) {
            try {
                db.close();
            } catch (RuntimeException e) {
                if (first == null) {
                    first = e;
                }
            }
        }
        for (DocumentDB db : documentDBs.values()) {
            try {
                db.close();
            } catch (RuntimeException e) {
                if (first == null) {
                    first = e;
                }
            }
        }
        for (CacheManager cache : cacheManagers.values()) {
            try {
                cache.close();
            } catch (RuntimeException e) {
                if (first == null) {
                    first = e;
                }
            }
        }
        for (MessagingManager messaging : messagingManagers.values()) {
            try {
                messaging.close();
            } catch (RuntimeException e) {
                if (first == null) {
                    first = e;
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }
}
