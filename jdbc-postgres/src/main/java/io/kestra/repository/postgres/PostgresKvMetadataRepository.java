package io.kestra.repository.postgres;

import org.jooq.Condition;

import io.kestra.core.models.kv.PersistedKvMetadata;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcKvMetadataRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresKvMetadataRepository extends AbstractJdbcKvMetadataRepository {
    @Inject
    public PostgresKvMetadataRepository(
        @Named("kvMetadata") PostgresRepository<PersistedKvMetadata> repository) {
        super(repository);
    }

    @Override
    protected Condition findCondition(String query) {
        return PostgresKvMetadataRepositoryService.findCondition(jdbcRepository, query);
    }
}
