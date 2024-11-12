package io.kestra.core.runners;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.property.Property;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static io.kestra.core.utils.Rethrow.throwFunction;

/**
 * A wrapper of a property and a run context which exposes null-safe methods on top of a property.
 *
 * @param <T>
 */
public class RunContextProperty<T> {
    private final Property<T> property;
    private final RunContext runContext;

    RunContextProperty(Property<T> property, RunContext runContext) {
        this.property = property;
        this.runContext = runContext;
    }

    /**
     * Render a property then convert it to its target type.<br>
     *
     * This method is safe to be used as many times as you want as the rendering and conversion will be cached.
     * Warning, due to the caching mechanism, this method is not thread-safe.
     */
    public Optional<T> as(Class<T> clazz) throws IllegalVariableEvaluationException {
        return Optional.ofNullable(this.property)
            .map(throwFunction(prop -> Property.as(prop, this.runContext, clazz)));
    }

    /**
     * Render a property with additional variables, then convert it to its target type.<br>
     *
     * This method is safe to be used as many times as you want as the rendering and conversion will be cached.
     * Warning, due to the caching mechanism, this method is not thread-safe.
     */
    public Optional<T> as(Class<T> clazz, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        return Optional.ofNullable(this.property)
            .map(throwFunction(prop -> Property.as(prop, this.runContext, clazz, variables)));
    }

    /**
     * Render a property then convert it as a list of target type.
     * Null properties will return an empty list.<br>
     *
     * This method is safe to be used as many times as you want as the rendering and conversion will be cached.
     * Warning, due to the caching mechanism, this method is not thread-safe.
     */
    public <I> T asList(Class<I> itemClazz) throws IllegalVariableEvaluationException {
        return Optional.ofNullable(this.property)
            .map(throwFunction(prop -> Property.asList(prop, this.runContext, itemClazz)))
            .orElse((T) Collections.emptyList());
    }

    /**
     * Render a property with additional variables, then convert it as a list of target type.
     * Null properties will return an empty list.<br>
     *
     * This method is safe to be used as many times as you want as the rendering and conversion will be cached.
     * Warning, due to the caching mechanism, this method is not thread-safe.
     */
    public <I> T asList(Class<I> itemClazz, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        return Optional.ofNullable(this.property)
            .map(throwFunction(prop -> Property.asList(prop, this.runContext, itemClazz, variables)))
            .orElse((T) Collections.emptyList());
    }

    /**
     * Render a property then convert it as a map of target types.
     * Null properties will return an empty map.<br>
     *
     * This method is safe to be used as many times as you want as the rendering and conversion will be cached.
     * Warning, due to the caching mechanism, this method is not thread-safe.
     */
    public <K,V> T asMap(Class<K> keyClass, Class<V> valueClass) throws IllegalVariableEvaluationException {
        return Optional.ofNullable(this.property)
            .map(throwFunction(prop -> Property.asMap(prop, this.runContext, keyClass, valueClass)))
            .orElse((T) Collections.emptyMap());
    }

    /**
     * Render a property with additional variables, then convert it as a map of target types.
     * Null properties will return an empty map.<br>
     *
     * This method is safe to be used as many times as you want as the rendering and conversion will be cached.
     * Warning, due to the caching mechanism, this method is not thread-safe.
     */
    public <K,V> T asMap(Class<K> keyClass, Class<V> valueClass, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        return Optional.ofNullable(this.property)
            .map(throwFunction(prop -> Property.asMap(prop, this.runContext, keyClass, valueClass, variables)))
            .orElse((T) Collections.emptyMap());
    }
}
