package cn.arkmillion.core.accessor;

import cn.arkmillion.core.exception.DataManagerException;

import java.lang.reflect.Field;

public class ReflectivePropertyAccessor implements PropertyAccessor {

    private final Field field;

    public ReflectivePropertyAccessor(Field field) {
        this.field = field;
        field.setAccessible(true);
    }

    @Override
    public Object get(Object entity) {
        try {
            return field.get(entity);
        } catch (IllegalAccessException e) {
            throw new DataManagerException("Failed to read field '" + field.getName()
                    + "' of " + entity.getClass().getName(), e);
        }
    }

    @Override
    public void set(Object entity, Object value) {
        try {
            field.set(entity, value);
        } catch (IllegalAccessException e) {
            throw new DataManagerException("Failed to write field '" + field.getName()
                    + "' of " + entity.getClass().getName(), e);
        }
    }

    public Field getField() {
        return field;
    }
}
