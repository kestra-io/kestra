package io.kestra.core.models.templates;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE, ElementType.TYPE})
@Requires(property = "kestra.templates.enabled", value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
@Inherited
public @interface TemplateEnabled {

}
