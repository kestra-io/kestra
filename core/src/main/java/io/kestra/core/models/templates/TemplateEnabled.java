package io.kestra.core.models.templates;

import java.lang.annotation.*;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.PACKAGE, ElementType.TYPE })
@Requires(property = "kestra.templates.enabled", value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
@Inherited
public @interface TemplateEnabled {

}
