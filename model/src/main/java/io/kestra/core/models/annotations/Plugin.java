package io.kestra.core.models.annotations;

import java.lang.annotation.*;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Inherited
@Retention(RUNTIME)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
public @interface Plugin {
    Example[] examples() default {};

    Metric[] metrics() default {};

    /**
     * @return whether the plugin is in beta
     */
    boolean beta() default false;

    /**
     * Specifies whether the annotated plugin class is internal to Kestra.
     * <p>
     * An internal plugin can be resolved through the {@link io.kestra.core.plugins.PluginRegistry}, but cannot
     * be referenced directly in a YAML flow definition.
     *
     * @return {@code true} if the plugin is internal. Otherwise {@link false}.
     */
    boolean internal() default false;

    /**
     * Specifies optional plugin aliases.
     * <p>
     * Aliases are alternate name for the plugin that will resolve to the class annotated.
     * For the moment, aliases are considered as deprecated plugins replaced by the class annotated.
     */
    String[] aliases() default {};
}
