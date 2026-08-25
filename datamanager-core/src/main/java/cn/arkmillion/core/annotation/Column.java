package cn.arkmillion.core.annotation;

import cn.arkmillion.core.enums.DataType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Column {

    String name() default "";

    DataType type() default DataType.AUTO;

    int length() default 255;

    int precision() default 0;

    int scale() default 0;

    boolean nullable() default true;

    boolean unique() default false;

    String defaultValue() default "";

    String comment() default "";
}
