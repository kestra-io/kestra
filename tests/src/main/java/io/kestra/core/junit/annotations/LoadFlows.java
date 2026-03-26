package io.kestra.core.junit.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

import io.kestra.core.junit.extensions.FlowLoaderExtension;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(FlowLoaderExtension.class)
public @interface LoadFlows {
    String[] value();

    String tenantId() default MAIN_TENANT;
}