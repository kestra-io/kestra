package io.kestra.repository.h2;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.kv.PersistedKvMetadata;
import io.kestra.jdbc.repository.AbstractJdbcKvMetadataRepository;
import io.kestra.jdbc.services.JdbcFilterService;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Singleton
@H2RepositoryEnabled
public class H2KvMetadataRepository extends AbstractJdbcKvMetadataRepository {
    @Inject
    public H2KvMetadataRepository(@Named("kvMetadata") H2Repository<PersistedKvMetadata> repository,
                            ApplicationContext applicationContext) {
        super(repository, applicationContext);
    }


    @Override
    protected Condition findCondition(String query) {
        return H2KvMetadataRepositoryService.findCondition(jdbcRepository, query);
    }
}
