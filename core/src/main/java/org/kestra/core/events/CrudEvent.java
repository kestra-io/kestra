package org.kestra.core.events;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class CrudEvent<T> {
    T model;
    CrudEventType type;
}
