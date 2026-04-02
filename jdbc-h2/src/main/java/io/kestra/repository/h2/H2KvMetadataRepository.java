package io.kestra.repository.h2;

import org.jooq.Condition;

import io.kestra.core.models.kv.PersistedKvMetadata;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcKvMetadataRepository;

import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@H2RepositoryEnabled
public class H2KvMetadataRepository extends AbstractJdbcKvMetadataRepository {
    @Inject
    public H2KvMetadataRepository(@Named("kvMetadata") H2Repository<PersistedKvMetadata> repository, ApplicationContext applicationContext) {
        super(repository);
    }

    @Override
    protected Condition findCondition(String query) {
        return H2KvMetadataRepositoryService.findCondition(jdbcRepository, query);
    }
}
