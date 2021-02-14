package org.kestra.core.repositories;

public interface SaveRepositoryInterface <T> {
    T save(T flow);
}
