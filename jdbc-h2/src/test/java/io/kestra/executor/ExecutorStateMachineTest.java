package io.kestra.executor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

/**
 * Marks an executor state-machine test: boots a Micronaut/H2 context and sets the gating property
 * that enables the recording-queue factory and the harness factory. Carried on the concrete test
 * class (annotation metadata, including this meta-annotation's {@code @Property}, is read from the
 * test class itself — an inherited {@code @Property} on the base class is not).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@MicronautTest
@Property(name = "kestra.test.executor-state-machine-harness", value = "true")
public @interface ExecutorStateMachineTest {
}
