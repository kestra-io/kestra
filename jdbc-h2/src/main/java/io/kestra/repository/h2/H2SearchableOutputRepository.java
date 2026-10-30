package io.kestra.repository.h2;

import io.kestra.core.repositories.RepositoryBean;
import io.kestra.core.searchable.SearchableOutput;

import io.kestra.jdbc.repository.AbstractJdbcSearchableOutputRepository;
import jakarta.inject.Named;

@RepositoryBean
@H2RepositoryEnabled
public class H2SearchableOutputRepository extends AbstractJdbcSearchableOutputRepository {
    public H2SearchableOutputRepository(@Named("searchableoutputs") H2Repository<SearchableOutput> jdbcRepository) {
        super(jdbcRepository);
    }
}
