package io.kestra.core.runners;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static io.kestra.core.utils.Rethrow.throwFunction;

/**
 * A wrapper of a property and a run context which exposes null-safe methods on top of a property.
 *
 * @param <T>
 */
@Slf4j
public class RunContextProperty<T> {
    private final Property<T> property;
    private final RunContext runContext;
    private final Task task;
    private final AbstractTrigger trigger;
    
    private final boolean skipCache;

    RunContextProperty(Property<T> property, RunContext runContext) {
        this(property, runContext, false);
    }
    
    RunContextProperty(Property<T> property, RunContext runContext, boolean skipCache) {
        this.property = property;
        this.runContext = runContext;
        this.task = ((DefaultRunContext) runContext).getTask();
        this.trigger = ((DefaultRunContext) runContext).getTrigger();
        this.skipCache = skipCache;
    }

    private void validate() {
        if (task != null) {
            runContext.validate(task);
        } else if (trigger != null) {
            runContext.validate(trigger);
        } else if (log.isTraceEnabled()) {
            // this should never happen, but it happens a lot on unit test
            log.trace("Unable to do validation: no task or trigger found");
        }
    }
    
    /**
     * Returns a new {@link RunContextProperty} that will always be rendered by evaluating
     * its original Pebble expression, without using any previously cached value.
     * <p>
     * This ensures that each time the property is rendered, the underlying
     * expression is re-evaluated to produce a fresh result.
     *
     * @return a new {@link Property} that bypasses the cache
     */
    public RunContextProperty<T> skipCache() {
        return new RunContextProperty<>(this.property, this.runContext, true);
    }

    /**
     * Render a property then convert it to its target type and validate it.<br>
     *
     * Validation will only occur if the runContext has been created with a Task or an AbstractTrigger.<br>
     *
     * This method is safe to be used as many times as you want as the rendering and conversion will be cached.
     * Warning, due to the caching mechanism, this method is not thread-safe.
     */
    public Optional<T> as(Class<T> clazz) throws IllegalVariableEvaluationException {
        var as = Optional.ofNullable(getProperty())
            .map(throwFunction(prop -> Property.as(prop, this.runContext, clazz)));

        validate();
        return as;
    }
    
    /**
     * Render a property with additional variables, then convert it to its target type and validate it.<br>
     *
     * Validation will only occur if the runContext has been created with a Task or an AbstractTrigger.<br>
     *
     * This method is safe to be used as many times as you want as the rendering and conversion will be cached.
     * Warning, due to the caching mechanism, this method is not thread-safe.
     */
    public Optional<T> as(Class<T> clazz, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        var as = Optional.ofNullable(getProperty())
            .map(throwFunction(prop -> Property.as(prop, this.runContext, clazz, variables)));

        validate();
        return as;
    }

    /**
     * Render a property then convert it as a list of target type and validate it.
     * Null properties will return an empty list.<br>
     *
     * Validation will only occur if the runContext has been created with a Task or an AbstractTrigger.<br>
     *
     * This method is safe to be used as many times as you want as the rendering and conversion will be cached.
     * Warning, due to the caching mechanism, this method is not thread-safe.
     */
    @SuppressWarnings("unchecked")
    public <I> T asList(Class<I> itemClazz) throws IllegalVariableEvaluationException {
        var as = Optional.ofNullable(getProperty())
            .map(throwFunction(prop -> Property.asList(prop, this.runContext, itemClazz)))
            .orElse((T) Collections.emptyList());

        validate();
        return as;
    }

    /**
     * Render a property with additional variables, then convert it as a list of target type and validate it.
     * Null properties will return an empty list.<br>
     *
     * Validation will only occur if the runContext has been created with a Task or an AbstractTrigger.<br>
     *
     * This method is safe to be used as many times as you want as the rendering and conversion will be cached.
     * Warning, due to the caching mechanism, this method is not thread-safe.
     */
    @SuppressWarnings("unchecked")
    public <I> T asList(Class<I> itemClazz, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        var as = Optional.ofNullable(getProperty())
            .map(throwFunction(prop -> Property.asList(prop, this.runContext, itemClazz, variables)))
            .orElse((T) Collections.emptyList());

        validate();
        return as;
    }

    /**
     * Render a property then convert it as a map of target types and validate it.
     * Null properties will return an empty map.<br>
     *
     * Validation will only occur if the runContext has been created with a Task or an AbstractTrigger.<br>
     *
     * This method is safe to be used as many times as you want as the rendering and conversion will be cached.
     * Warning, due to the caching mechanism, this method is not thread-safe.
     */
    @SuppressWarnings("unchecked")
    public <K,V> T asMap(Class<K> keyClass, Class<V> valueClass) throws IllegalVariableEvaluationException {
        var as = Optional.ofNullable(getProperty())
            .map(throwFunction(prop -> Property.asMap(prop, this.runContext, keyClass, valueClass)))
            .orElse((T) Collections.emptyMap());

        validate();
        return as;
    }

    /**
     * Render a property with additional variables, then convert it as a map of target types and validate it.
     * Null properties will return an empty map.<br>
     *
     * Validation will only occur if the runContext has been created with a Task or an AbstractTrigger.<br>
     *
     * This method is safe to be used as many times as you want as the rendering and conversion will be cached.
     * Warning, due to the caching mechanism, this method is not thread-safe.
     */
    @SuppressWarnings("unchecked")
    public <K,V> T asMap(Class<K> keyClass, Class<V> valueClass, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        var as = Optional.ofNullable(getProperty())
            .map(throwFunction(prop -> Property.asMap(prop, this.runContext, keyClass, valueClass, variables)))
            .orElse((T) Collections.emptyMap());

        validate();
        return as;
    }
    
    private Property<T> getProperty() {
        return skipCache ? this.property.skipCache() : this.property;
    }
}
