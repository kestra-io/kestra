package io.kestra.repository.postgres;

import io.kestra.core.models.executions.ExecutionIndexedField;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcIndexedFieldRepository;

import jakarta.inject.Named;

@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresIndexedFieldRepository extends AbstractJdbcIndexedFieldRepository {
    public PostgresIndexedFieldRepository(@Named("indexedfields") PostgresRepository<ExecutionIndexedField> jdbcRepository) {
        super(jdbcRepository);
    }
}
