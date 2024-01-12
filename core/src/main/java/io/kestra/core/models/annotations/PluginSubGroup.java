package io.kestra.core.models.annotations;

import java.lang.annotation.*;

import static io.kestra.core.models.annotations.PluginSubGroup.PluginCategory.MISC;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Inherited
@Retention(RUNTIME)
@Target({ElementType.PACKAGE})
public @interface PluginSubGroup {
    String title() default "";
    String description() default "";

    PluginCategory[] categories() default { MISC };

    enum PluginCategory {
        DATABASE,
        MESSAGING,
        SCRIPT,
        TRANSFORMATION,
        FLOW,
        BATCH,
        ALERTING,
        CLOUD,
        STORAGE,
        MISC,
        TOOL,
        AI,
        CORE
    }
}
