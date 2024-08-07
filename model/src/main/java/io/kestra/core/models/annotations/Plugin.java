package io.kestra.core.models.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

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
     * An internal plugin can be resolved through the PluginRegistry, but cannot
     * be referenced directly in a YAML flow definition.
     *
     * @return {@code true} if the plugin is internal. Otherwise {@code false}.
     */
    boolean internal() default false;

    /**
     * Specifies optional plugin aliases.
     * <p>
     * Aliases are alternate name for the plugin that will resolve to the class annotated.
     * For the moment, aliases are considered as deprecated plugins replaced by the class annotated.
     */
    String[] aliases() default {};

    /**
     * Specifies whether to auto-rendering of properties must be enabled.
     *
     * @return {@code true} if auto-rendering is enabled. Otherwise {@code false}.
     */
    boolean enableAutoPropertiesDynamicRendering() default false;

    @Documented
    @Inherited
    @Retention(RUNTIME)
    @Target({ElementType.TYPE})
    @interface Id {
        /**
         * Specifies the unique ID for identifying a plugin. ID is case-insensitive.
         * @return  The string identifier.
         */
        String value();
    }
}
