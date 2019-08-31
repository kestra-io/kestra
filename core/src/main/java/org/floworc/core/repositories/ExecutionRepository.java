package org.floworc.core.repositories;

import org.floworc.core.models.executions.Execution;

import java.util.List;

public class ExecutionRepository {
    private RepositoryStorage repositoryStorage;

    public ExecutionRepository(RepositoryStorage repositoryStorage) {
        this.repositoryStorage = repositoryStorage;
    }

    public Execution getById(String id) {
        return this.repositoryStorage.getByKey(Execution.class, id);
    }

    public List<Execution> getAll() {
        return this.repositoryStorage.getAll(Execution.class);
    }
}
