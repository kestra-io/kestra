package io.kestra.repository.h2;

import org.jooq.Condition;

import io.kestra.core.models.namespaces.files.NamespaceFileMetadata;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcNamespaceFileMetadataRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@H2RepositoryEnabled
public class H2NamespaceFileMetadataRepository extends AbstractJdbcNamespaceFileMetadataRepository {
    @Inject
    public H2NamespaceFileMetadataRepository(@Named("namespaceFileMetadata") H2Repository<NamespaceFileMetadata> repository) {
        super(repository);
    }

    @Override
    protected Condition findCondition(String query) {
        return H2NamespaceFileMetadataRepositoryService.findCondition(jdbcRepository, query);
    }
}
