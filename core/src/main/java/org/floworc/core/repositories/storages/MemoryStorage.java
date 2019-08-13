package org.floworc.core.repositories.storages;

import org.floworc.core.repositories.RepositoryStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MemoryStorage implements RepositoryStorage {
    private Map<String, Map<String, Object>> storage = new HashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getByKey(Class<T> clz, String id) {
        return (T) this.storage.get(clz.getName()).get(id);
    }

    @Override
    @SuppressWarnings("unchecked")

    public <T> List<T> getAll(Class<T> clz) {
        return new ArrayList<>(this.storage.get(clz.getName()).values())
            .stream()
            .map(o -> (T) o)
            .collect(Collectors.toList());
    }
}
