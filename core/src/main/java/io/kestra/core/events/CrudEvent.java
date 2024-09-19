package io.kestra.core.events;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.context.ServerRequestContext;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class CrudEvent<T> {
    T model;
    @Nullable
    T previousModel;
    CrudEventType type;
    HttpRequest<?> request;

    public CrudEvent(T model, CrudEventType type) {
        this.model = model;
        this.type = type;
        this.previousModel = null;
        this.request = ServerRequestContext.currentRequest().orElse(null);
    }

    public CrudEvent(T model, T previousModel, CrudEventType type) {
        this.model = model;
        this.previousModel = previousModel;
        this.type = type;
        this.request = ServerRequestContext.currentRequest().orElse(null);
    }

}
