package io.kestra.core.junit.annotations;

import io.kestra.core.junit.extensions.KestraTestExtension;
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.test.annotation.TransactionMode;
import io.micronaut.test.condition.TestActiveCondition;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@ExtendWith(KestraTestExtension.class)
@Factory
@Inherited
@Requires(condition = TestActiveCondition.class)
@Executable
public @interface KestraTest {
    Class<?> application() default void.class;

    String[] environments() default {};

    String[] packages() default {};

    String[] propertySources() default {};

    boolean rollback() default true;

    boolean transactional() default false;

    boolean rebuildContext() default false;

    Class<? extends ApplicationContextBuilder>[] contextBuilder() default {};

    TransactionMode transactionMode() default TransactionMode.SEPARATE_TRANSACTIONS;

    boolean startApplication() default true;

    boolean resolveParameters() default true;
}