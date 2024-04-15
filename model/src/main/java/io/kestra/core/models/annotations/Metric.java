package io.kestra.core.models.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Inherited
@Retention(RUNTIME)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Repeatable(Metrics.class)
public @interface Metric {
    /**
     * The name of the metric
     */
    String name();

    /**
     * The type of the metric, should be 'counter' or 'timer'.
     */
    String type();

    /**
     * Optional unit, can be used for counter metric to denote the unit (records, bytes, ...)
     */
    String unit() default "";

    /**
     * Optional description
     */
    String description() default "";
}
