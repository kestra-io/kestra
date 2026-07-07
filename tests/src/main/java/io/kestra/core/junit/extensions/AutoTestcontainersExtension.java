package io.kestra.core.junit.extensions;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.lifecycle.Startable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

public class AutoTestcontainersExtension
        implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeAll(ExtensionContext context) {
        context.getTestClass()
            .filter(c -> !hasExplicitAnnotation(c))
            .ifPresent(c -> startContainers(c, true, null));
    }

    @Override
    public void afterAll(ExtensionContext context) {
        context.getTestClass()
            .filter(c -> !hasExplicitAnnotation(c))
            .ifPresent(c -> stopContainers(c, true, null));
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        context.getTestInstance().ifPresent(instance -> {
            if (!hasExplicitAnnotation(instance.getClass())) {
                startContainers(instance.getClass(), false, instance);
            }
        });
    }

    @Override
    public void afterEach(ExtensionContext context) {
        context.getTestInstance().ifPresent(instance -> {
            if (!hasExplicitAnnotation(instance.getClass())) {
                stopContainers(instance.getClass(), false, instance);
            }
        });
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Returns true when the test class already has @Testcontainers — let the standard extension win. */
    private boolean hasExplicitAnnotation(Class<?> testClass) {
        return AnnotationSupport.isAnnotated(testClass, Testcontainers.class);
    }

    private void startContainers(Class<?> testClass, boolean staticOnly, Object instance) {
        findContainerFields(testClass, staticOnly).stream()
            .map(f -> getValue(f, instance))
            .filter(Startable.class::isInstance)
            .map(Startable.class::cast)
            .filter(c -> !isRunning(c))
            .forEach(Startable::start);
    }

    private void stopContainers(Class<?> testClass, boolean staticOnly, Object instance) {
        findContainerFields(testClass, staticOnly).stream()
            .map(f -> getValue(f, instance))
            .filter(Startable.class::isInstance)
            .map(Startable.class::cast)
            .forEach(Startable::stop);
    }

    private List<Field> findContainerFields(Class<?> testClass, boolean staticOnly) {
        return AnnotationSupport.findAnnotatedFields(
            testClass,
            Container.class,
            f -> staticOnly == Modifier.isStatic(f.getModifiers())
        );
    }

    private Object getValue(Field field, Object instance) {
        try {
            field.setAccessible(true);
            return field.get(instance);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read @Container field: " + field.getName(), e);
        }
    }

    private boolean isRunning(Startable container) {
        return container instanceof GenericContainer<?> gc && gc.isRunning();
    }
}
