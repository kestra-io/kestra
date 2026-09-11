package io.kestra.webserver.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a route that {@link io.kestra.webserver.filter.AuthenticationFilter} may serve without
 * credentials, and only when its path is also listed in {@code kestra.server.basic-auth.open-urls}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
public @interface AnonymousAccess {
}
