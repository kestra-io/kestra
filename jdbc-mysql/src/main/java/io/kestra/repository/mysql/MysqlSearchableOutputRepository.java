package io.kestra.repository.mysql;

import io.kestra.core.repositories.RepositoryBean;
import io.kestra.core.searchable.SearchableOutput;

import io.kestra.jdbc.repository.AbstractJdbcSearchableOutputRepository;
import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlSearchableOutputRepository extends AbstractJdbcSearchableOutputRepository {
    public MysqlSearchableOutputRepository(@Named("searchableoutputs") MysqlRepository<SearchableOutput> jdbcRepository) {
        super(jdbcRepository);
    }
}
