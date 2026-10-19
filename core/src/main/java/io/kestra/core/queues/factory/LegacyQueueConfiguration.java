package io.kestra.core.queues.factory;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a property prefix a queue backend used to be configured under before it became a plugin.
 * <p>
 * Properties found under {@link #prefix()} are mapped into the plugin configuration at {@link #path()}
 * with a deprecation warning; a value also present under {@code kestra.queue.<id>} takes precedence.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(LegacyQueueConfiguration.List.class)
public @interface LegacyQueueConfiguration {

    String prefix();

    /**
     * Dotted path inside the plugin configuration the legacy subtree maps to, the root when empty.
     */
    String path() default "";

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface List {
        LegacyQueueConfiguration[] value();
    }
}
