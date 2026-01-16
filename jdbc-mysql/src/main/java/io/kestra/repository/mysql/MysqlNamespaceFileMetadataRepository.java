package io.kestra.repository.mysql;

import io.kestra.core.models.namespaces.files.NamespaceFileMetadata;
import io.kestra.jdbc.repository.AbstractJdbcNamespaceFileMetadataRepository;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jooq.Condition;

@Singleton
@MysqlRepositoryEnabled
public class MysqlNamespaceFileMetadataRepository extends AbstractJdbcNamespaceFileMetadataRepository {
    @Inject
    public MysqlNamespaceFileMetadataRepository(
        @Named("namespaceFileMetadata") MysqlRepository<NamespaceFileMetadata> repository
    ) {
        super(repository);
    }

    @Override
    protected Condition findCondition(String query) {
        return MysqlNamespaceFileMetadataRepositoryService.findCondition(jdbcRepository, query);
    }
}
