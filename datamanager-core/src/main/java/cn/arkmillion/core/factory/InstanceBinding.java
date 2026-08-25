package cn.arkmillion.core.factory;

import cn.arkmillion.core.exception.DataManagerException;

import java.util.Objects;

public final class InstanceBinding<T> {

    private final String name;
    private final T instance;

    private InstanceBinding(String name, T instance) {
        if (name == null || name.trim().isEmpty()) {
            throw new DataManagerException("Instance alias must not be blank");
        }
        this.name = name.trim();
        this.instance = Objects.requireNonNull(instance, "instance");
    }

    public static <T> InstanceBinding<T> of(String name, T instance) {
        return new InstanceBinding<>(name, instance);
    }

    public static String resolveAlias(String configuredAlias, String providerName) {
        if (configuredAlias == null || configuredAlias.trim().isEmpty()) {
            return providerName;
        }
        return configuredAlias.trim();
    }

    public String getName() {
        return name;
    }

    public T getInstance() {
        return instance;
    }
}
