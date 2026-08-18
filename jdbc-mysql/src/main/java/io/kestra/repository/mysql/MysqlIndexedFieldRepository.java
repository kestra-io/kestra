package io.kestra.repository.mysql;

import io.kestra.core.models.executions.ExecutionIndexedField;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcIndexedFieldRepository;

import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlIndexedFieldRepository extends AbstractJdbcIndexedFieldRepository {
    public MysqlIndexedFieldRepository(@Named("indexedfields") MysqlRepository<ExecutionIndexedField> jdbcRepository) {
        super(jdbcRepository);
    }
}
