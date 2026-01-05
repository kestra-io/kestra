package io.kestra.repository.h2;

import io.kestra.core.models.kv.PersistedKvMetadata;
import io.kestra.core.queues.QueueService;
import io.kestra.jdbc.repository.AbstractJdbcKvMetadataRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jooq.Condition;

@Singleton
@H2RepositoryEnabled
public class H2KvMetadataRepository extends AbstractJdbcKvMetadataRepository {
    @Inject
    public H2KvMetadataRepository(@Named("kvMetadata") H2Repository<PersistedKvMetadata> repository, QueueService queueService, ApplicationContext applicationContext) {
        super(repository, queueService);
    }


    @Override
    protected Condition findCondition(String query) {
        return H2KvMetadataRepositoryService.findCondition(jdbcRepository, query);
    }
}
