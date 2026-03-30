package io.kestra.runner.postgres;

import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.kestra.jdbc.runner.AbstractJdbcMultipleStateStore;
import io.kestra.repository.postgres.PostgresRepository;
import io.kestra.repository.postgres.PostgresRepositoryEnabled;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@PostgresRepositoryEnabled
public class PostgresMultipleStateStore extends AbstractJdbcMultipleStateStore {
    public PostgresMultipleStateStore(@Named("multipleconditions") PostgresRepository<MultipleConditionWindow> repository) {
        super(repository);
    }
}
