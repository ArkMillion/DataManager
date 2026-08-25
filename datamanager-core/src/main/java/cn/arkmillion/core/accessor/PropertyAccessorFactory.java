package cn.arkmillion.core.accessor;

import java.lang.reflect.Field;

public interface PropertyAccessorFactory {

    PropertyAccessor create(Field field);
}
