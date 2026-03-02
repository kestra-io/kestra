package io.kestra.core.junit.annotations;

import io.kestra.core.junit.extensions.FlowExecutorExtension;
import io.kestra.core.models.executions.ExecutionKind;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(FlowExecutorExtension.class)
public @interface ExecuteFlow {

    String value();

    String timeout() default "PT60S";

    String tenantId() default MAIN_TENANT;

    ExecutionKind executionKind() default ExecutionKind.NORMAL;
}
