package io.kestra.repository.postgres;

import io.kestra.core.models.namespaces.files.NamespaceFileMetadata;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcNamespaceFileMetadataRepository;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jooq.Condition;

@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresNamespaceFileMetadataRepository extends AbstractJdbcNamespaceFileMetadataRepository {
    @Inject
    public PostgresNamespaceFileMetadataRepository(
        @Named("namespaceFileMetadata") PostgresRepository<NamespaceFileMetadata> repository
    ) {
        super(repository);
    }

    @Override
    protected Condition findCondition(String query) {
        return PostgresNamespaceFileMetadataRepositoryService.findCondition(jdbcRepository, query);
    }
}
