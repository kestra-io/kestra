package io.kestra.repository.h2;

import io.kestra.core.repositories.RepositoryBean;
import io.kestra.core.searchable.SearchableData;

import io.kestra.jdbc.repository.AbstractJdbcSearchableDataRepository;
import jakarta.inject.Named;

@RepositoryBean
@H2RepositoryEnabled
public class H2SearchableDataRepository extends AbstractJdbcSearchableDataRepository {
    public H2SearchableDataRepository(@Named("searchabledata") H2Repository<SearchableData> jdbcRepository) {
        super(jdbcRepository);
    }
}
