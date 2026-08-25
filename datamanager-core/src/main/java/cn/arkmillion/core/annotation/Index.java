package cn.arkmillion.core.annotation;

import cn.arkmillion.core.enums.IndexType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Index {

    IndexType type() default IndexType.NORMAL;

    String name() default "";
}
