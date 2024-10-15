package io.kestra.core.repositories;

import java.util.List;

public interface SaveRepositoryInterface<T> {
    T save(T item);

    default int saveBatch(List<T> items) {
        throw new UnsupportedOperationException();
    }
}
