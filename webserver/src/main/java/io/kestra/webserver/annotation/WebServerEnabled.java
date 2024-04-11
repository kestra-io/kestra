package io.kestra.webserver.annotation;

import io.micronaut.context.annotation.Requires;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE, ElementType.TYPE})
@Requires(property = "kestra.server-type", pattern = "(WEBSERVER|STANDALONE)", defaultValue = "STANDALONE")
public @interface WebServerEnabled {
}
