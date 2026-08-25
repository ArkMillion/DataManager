package cn.arkmillion.core.accessor;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

public final class PropertyAccessors {

    private static volatile boolean resolved;
    private static volatile PropertyAccessorFactory factory;
    private static final Map<Field, PropertyAccessor> CACHE = new ConcurrentHashMap<>();

    private PropertyAccessors() {
    }

    public static PropertyAccessor forField(Field field) {
        PropertyAccessor accessor = CACHE.get(field);
        if (accessor != null) {
            return accessor;
        }
        PropertyAccessorFactory f = resolveFactory();
        PropertyAccessor created = f == null ? null : f.create(field);
        if (created == null) {
            created = new ReflectivePropertyAccessor(field);
        }
        PropertyAccessor existing = CACHE.putIfAbsent(field, created);
        return existing == null ? created : existing;
    }

    public static void clearCache() {
        CACHE.clear();
    }

    static int cachedCount() {
        return CACHE.size();
    }

    private static PropertyAccessorFactory resolveFactory() {
        if (!resolved) {
            synchronized (PropertyAccessors.class) {
                if (!resolved) {
                    ClassLoader cl = Thread.currentThread().getContextClassLoader();
                    if (cl == null) {
                        cl = PropertyAccessors.class.getClassLoader();
                    }
                    Iterator<PropertyAccessorFactory> it =
                            ServiceLoader.load(PropertyAccessorFactory.class, cl).iterator();
                    if (it.hasNext()) {
                        factory = it.next();
                    }
                    resolved = true;
                }
            }
        }
        return factory;
    }
}
