package io.kestra.worker;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.kestra.core.runners.pebble.functions.KestraFunction;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanProvider;
import jakarta.inject.Provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Every Pebble function is rendered on the worker, so everything it depends on has to exist there.
 * Functions inject their collaborators through a {@link Provider}, which defers resolution to render
 * time and so hides a collaborator that cannot exist on a worker until a user renders the function —
 * this test resolves those providers eagerly so the gap fails the build instead.
 */
class WorkerPebbleFunctionDependenciesTest {

    @Test
    void shouldResolveEveryPebbleFunctionDependencyWhenServerTypeIsWorker() {
        try (ApplicationContext context = ApplicationContext.builder()
            .deduceEnvironment(false)
            .environments("test", "workerservertype")
            .start()) {

            assertThat(context.getRequiredProperty("kestra.server-type", String.class))
                .as("a wrong environment name would leave the context on its default server type and pass vacuously")
                .isEqualTo("WORKER");

            Collection<?> functions = context.getBeansOfType(KestraFunction.class);

            assertThat(functions).isNotEmpty();
            assertThat(functions).allSatisfy(function ->
                assertThat(lazyDependenciesOf(function)).allSatisfy(dependency ->
                    assertThatCode(() -> context.getBean(dependency))
                        .as("%s renders on the worker, so its dependency %s must resolve there", function.getClass().getName(), dependency.getName())
                        .doesNotThrowAnyException()
                )
            );
        }
    }

    private static Set<Class<?>> lazyDependenciesOf(Object function) {
        Set<Class<?>> dependencies = new LinkedHashSet<>();
        Class<?> type = function.getClass();

        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            for (Type parameter : constructor.getGenericParameterTypes()) {
                providedType(parameter).ifPresent(dependencies::add);
            }
        }
        for (Field field : type.getDeclaredFields()) {
            providedType(field.getGenericType()).ifPresent(dependencies::add);
        }
        return dependencies;
    }

    private static Optional<Class<?>> providedType(Type type) {
        if (!(type instanceof ParameterizedType parameterized) || !(parameterized.getRawType() instanceof Class<?> raw)) {
            return Optional.empty();
        }
        if (!Provider.class.isAssignableFrom(raw) && !BeanProvider.class.isAssignableFrom(raw)) {
            return Optional.empty();
        }
        return parameterized.getActualTypeArguments()[0] instanceof Class<?> argument ? Optional.of(argument) : Optional.empty();
    }
}
