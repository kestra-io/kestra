package io.kestra.repository.postgres;

import io.kestra.core.ai.usage.models.AiUsage;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcAiUsageRepository;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresAiUsageRepository extends AbstractJdbcAiUsageRepository {
    @Inject
    public PostgresAiUsageRepository(@Named("aiusage") PostgresRepository<AiUsage> repository) {
        super(repository);
    }
}
