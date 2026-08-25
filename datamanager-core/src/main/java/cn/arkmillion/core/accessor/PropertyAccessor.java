package cn.arkmillion.core.accessor;

public interface PropertyAccessor {

    Object get(Object entity);

    void set(Object entity, Object value);
}
