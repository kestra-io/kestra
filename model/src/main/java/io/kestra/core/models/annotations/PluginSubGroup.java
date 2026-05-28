package io.kestra.core.models.annotations;

import java.lang.annotation.*;

import static io.kestra.core.models.annotations.PluginSubGroup.PluginCategory.OTHER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Inherited
@Retention(RUNTIME)
@Target({ ElementType.PACKAGE })
public @interface PluginSubGroup {
    String title() default "";

    String description() default "";

    PluginCategory[] categories() default { OTHER };

    enum PluginCategory {
        @Deprecated
        DATABASE,
        @Deprecated
        MESSAGING,
        @Deprecated
        SCRIPT,
        @Deprecated
        TRANSFORMATION,
        @Deprecated
        BATCH,
        @Deprecated
        ALERTING,
        CLOUD,
        @Deprecated
        STORAGE,
        @Deprecated
        OTHER,
        @Deprecated
        TOOL,
        AI,
        CORE,
        @Deprecated
        INGESTION,
        @Deprecated
        BI,
        BUSINESS,
        DATA,
        INFRASTRUCTURE,
    }
}
