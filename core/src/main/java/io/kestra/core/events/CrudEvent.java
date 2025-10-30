package io.kestra.core.events;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.context.ServerRequestContext;
import lombok.Getter;

import java.util.Objects;

@Getter
public class CrudEvent<T> {
    private final T model;
    @Nullable
    private final T previousModel;
    private final CrudEventType type;
    private final HttpRequest<?> request;
    
    /**
     * Static helper method for creating a new {@link CrudEventType#UPDATE} CrudEvent.
     *
     * @param model the new created model.
     * @param <T>   type of the model.
     * @return the new {@link CrudEvent}.
     */
    public static <T> CrudEvent<T> create(T model) {
        Objects.requireNonNull(model, "Can't create CREATE event with a null model");
        return new CrudEvent<>(model, null, CrudEventType.CREATE);
    }
    
    /**
     * Static helper method for creating a new {@link CrudEventType#DELETE} CrudEvent.
     *
     * @param model the deleted model.
     * @param <T>   type of the model.
     * @return the new {@link CrudEvent}.
     */
    public static <T> CrudEvent<T> delete(T model) {
        Objects.requireNonNull(model, "Can't create DELETE event with a null model");
        return new CrudEvent<>(null, model, CrudEventType.DELETE);
    }
    
    /**
     * Static helper method for creating a new CrudEvent.
     *
     * @param before the model before the update.
     * @param after  the model after the update.
     * @param <T>   type of the model.
     * @return the new {@link CrudEvent}.
     */
    public static <T> CrudEvent<T> of(T before, T after) {
        
        if (before == null && after == null) {
            throw new IllegalArgumentException("Both before and after cannot be null");
        }
        
        if (before == null) {
            return create(after);
        }
        
        if (after == null) {
            return delete(before);
        }
        
        return new CrudEvent<>(after, before, CrudEventType.UPDATE);
    }
    
    /**
     * @deprecated use the static factory methods.
     */
    @Deprecated
    public CrudEvent(T model, CrudEventType type) {
        this(
            CrudEventType.DELETE.equals(type) ? null : model,
            CrudEventType.DELETE.equals(type) ? model : null, 
            type, 
            ServerRequestContext.currentRequest().orElse(null)
        );
    }

    public CrudEvent(T model, T previousModel, CrudEventType type) {
        this(model, previousModel, type, ServerRequestContext.currentRequest().orElse(null));
    }
    
    public CrudEvent(T model, T previousModel, CrudEventType type, HttpRequest<?> request) {
        this.model = model;
        this.previousModel = previousModel;
        this.type = type;
        this.request = request;
    }
}
