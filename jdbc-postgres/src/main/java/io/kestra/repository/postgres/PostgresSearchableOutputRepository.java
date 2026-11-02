package io.kestra.repository.postgres;

import io.kestra.core.repositories.RepositoryBean;
import io.kestra.core.searchable.SearchableOutput;

import io.kestra.jdbc.repository.AbstractJdbcSearchableOutputRepository;
import jakarta.inject.Named;

@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresSearchableOutputRepository extends AbstractJdbcSearchableOutputRepository {
    public PostgresSearchableOutputRepository(@Named("searchableoutputs") PostgresRepository<SearchableOutput> jdbcRepository) {
        super(jdbcRepository);
    }
}
