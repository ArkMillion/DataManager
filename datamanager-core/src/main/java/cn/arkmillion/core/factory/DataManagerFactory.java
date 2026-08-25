package cn.arkmillion.core.factory;

import cn.arkmillion.core.config.DataManagerConfig;
import cn.arkmillion.core.db.DataManager;

import java.util.List;
import java.util.ServiceLoader;

public final class DataManagerFactory {

    private DataManagerFactory() {
    }

    public static DataManager create(DataManagerConfig config) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = DataManagerFactory.class.getClassLoader();
        }
        return create(config, cl);
    }

    public static DataManager create(DataManagerConfig config, ClassLoader classLoader) {
        DataManagerImpl manager = new DataManagerImpl();
        try {
            for (RelationalDBProvider provider : ServiceLoader.load(RelationalDBProvider.class, classLoader)) {
                List<InstanceBinding<cn.arkmillion.core.db.RelationalDB>> bindings = new java.util.ArrayList<>();
                try {
                    bindings = provider.createInstances(config);
                    for (InstanceBinding<cn.arkmillion.core.db.RelationalDB> binding : bindings) {
                        manager.register(binding.getName(), binding.getInstance());
                    }
                } catch (RuntimeException e) {
                    closeBindings(bindings, e);
                    throw e;
                }
            }
            for (DocumentDBProvider provider : ServiceLoader.load(DocumentDBProvider.class, classLoader)) {
                List<InstanceBinding<cn.arkmillion.core.db.DocumentDB>> bindings = new java.util.ArrayList<>();
                try {
                    bindings = provider.createInstances(config);
                    for (InstanceBinding<cn.arkmillion.core.db.DocumentDB> binding : bindings) {
                        manager.register(binding.getName(), binding.getInstance());
                    }
                } catch (RuntimeException e) {
                    closeBindings(bindings, e);
                    throw e;
                }
            }
            boolean firstCache = true;
            for (CacheProvider provider : ServiceLoader.load(CacheProvider.class, classLoader)) {
                List<InstanceBinding<cn.arkmillion.core.db.CacheManager>> bindings = new java.util.ArrayList<>();
                try {
                    bindings = provider.createInstances(config);
                    for (InstanceBinding<cn.arkmillion.core.db.CacheManager> binding : bindings) {
                        manager.registerCache(binding.getName(), binding.getInstance(), firstCache);
                        firstCache = false;
                    }
                } catch (RuntimeException e) {
                    closeBindings(bindings, e);
                    throw e;
                }
            }
            return manager;
        } catch (RuntimeException failure) {
            try {
                manager.close();
            } catch (RuntimeException suppressed) {
                failure.addSuppressed(suppressed);
            }
            throw failure;
        }
    }

    private static <T> void closeBindings(List<InstanceBinding<T>> bindings, RuntimeException failure) {
        for (InstanceBinding<T> binding : bindings) {
            T instance = binding.getInstance();
            if (instance instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) instance).close();
                } catch (Exception e) {
                    failure.addSuppressed(e);
                }
            }
        }
    }

    static <T> T first(Iterable<T> iterable) {
        java.util.Iterator<T> it = iterable.iterator();
        return it.hasNext() ? it.next() : null;
    }
}
