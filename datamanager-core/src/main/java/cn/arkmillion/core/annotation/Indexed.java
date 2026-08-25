package cn.arkmillion.core.annotation;

import cn.arkmillion.core.enums.IndexDirection;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Indexed {

    IndexDirection direction() default IndexDirection.ASC;

    boolean unique() default false;

    boolean sparse() default false;
}
