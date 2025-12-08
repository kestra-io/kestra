package io.kestra.core.repositories;

import java.util.List;

// FIXME rename it to something like IndexedRepository and only implement it for indexed entities
public interface SaveRepositoryInterface<T> {
    T save(T item);

    int saveBatch(List<T> items);
}
