package io.kestra.repository.postgres;

import io.kestra.core.models.kv.PersistedKvMetadata;
import io.kestra.jdbc.repository.AbstractJdbcKvMetadataRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.List;

@Singleton
@PostgresRepositoryEnabled
public class PostgresKvMetadataRepository extends AbstractJdbcKvMetadataRepository {
    @Inject
    public PostgresKvMetadataRepository(
        @Named("kvMetadata") PostgresRepository<PersistedKvMetadata> repository
    ) {
        super(repository);
    }

    @Override
    protected Condition findCondition(String query) {
        return PostgresKvMetadataRepositoryService.findCondition(jdbcRepository, query);
    }
}
