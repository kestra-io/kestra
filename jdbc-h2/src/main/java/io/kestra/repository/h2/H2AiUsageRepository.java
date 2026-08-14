package io.kestra.repository.h2;

import io.kestra.core.ai.usage.models.AiUsage;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcAiUsageRepository;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@H2RepositoryEnabled
public class H2AiUsageRepository extends AbstractJdbcAiUsageRepository {
    @Inject
    public H2AiUsageRepository(@Named("aiusage") H2Repository<AiUsage> repository) {
        super(repository);
    }
}
