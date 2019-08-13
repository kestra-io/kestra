package org.floworc.core.repositories;

import java.util.List;

public interface RepositoryStorage {
    <T> T getByKey(Class<T> clz, String id);

    <T> List<T> getAll(Class<T> clz);
}
