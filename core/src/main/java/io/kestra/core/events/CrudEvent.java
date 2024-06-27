package io.kestra.core.events;

import io.micronaut.core.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class CrudEvent<T> {
    T model;
    @Nullable
    T previousModel;
    CrudEventType type;

    public CrudEvent(T model, CrudEventType type) {
        this.model = model;
        this.type = type;
        this.previousModel = null;
    }
}
