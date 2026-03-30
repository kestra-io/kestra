package io.kestra.repository.mysql;

import org.jooq.Condition;

import io.kestra.core.models.kv.PersistedKvMetadata;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcKvMetadataRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlKvMetadataRepository extends AbstractJdbcKvMetadataRepository {
    @Inject
    public MysqlKvMetadataRepository(
        @Named("kvMetadata") MysqlRepository<PersistedKvMetadata> repository) {
        super(repository);
    }

    @Override
    protected Condition findCondition(String query) {
        return MysqlKvMetadataRepositoryService.findCondition(jdbcRepository, query);
    }
}
