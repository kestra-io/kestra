package io.kestra.repository.mysql;

import io.kestra.core.repositories.RepositoryBean;
import io.kestra.core.searchable.SearchableData;

import io.kestra.jdbc.repository.AbstractJdbcSearchableDataRepository;
import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlSearchableDataRepository extends AbstractJdbcSearchableDataRepository {
    public MysqlSearchableDataRepository(@Named("searchabledata") MysqlRepository<SearchableData> jdbcRepository) {
        super(jdbcRepository);
    }
}
