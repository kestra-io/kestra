package io.kestra.core.models.annotations;

import java.lang.annotation.*;

import static io.kestra.core.models.annotations.PluginSubGroup.PluginCategory.OTHER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Inherited
@Retention(RUNTIME)
@Target({ElementType.PACKAGE})
public @interface PluginSubGroup {
    String title() default "";
    String description() default "";

    PluginCategory[] categories() default {OTHER};

    enum PluginCategory {
        DATABASE,
        MESSAGING,
        SCRIPT,
        TRANSFORMATION,
        BATCH,
        ALERTING,
        CLOUD,
        STORAGE,
        OTHER,
        TOOL,
        AI,
        CORE,
        INGESTION,
        BI,
        DATA_QUALITY
    }
}
