package io.kestra.repository.h2;

import io.kestra.core.models.executions.ExecutionIndexedField;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcIndexedFieldRepository;

import jakarta.inject.Named;

@RepositoryBean
@H2RepositoryEnabled
public class H2IndexedFieldRepository extends AbstractJdbcIndexedFieldRepository {
    public H2IndexedFieldRepository(@Named("indexedfields") H2Repository<ExecutionIndexedField> jdbcRepository) {
        super(jdbcRepository);
    }
}
