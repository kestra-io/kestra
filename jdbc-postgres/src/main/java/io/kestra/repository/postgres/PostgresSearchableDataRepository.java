package io.kestra.repository.postgres;

import io.kestra.core.repositories.RepositoryBean;
import io.kestra.core.searchable.SearchableData;

import io.kestra.jdbc.repository.AbstractJdbcSearchableDataRepository;
import jakarta.inject.Named;

@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresSearchableDataRepository extends AbstractJdbcSearchableDataRepository {
    public PostgresSearchableDataRepository(@Named("searchabledata") PostgresRepository<SearchableData> jdbcRepository) {
        super(jdbcRepository);
    }
}
