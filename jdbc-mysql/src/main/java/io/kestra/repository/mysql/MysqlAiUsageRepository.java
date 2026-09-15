package io.kestra.repository.mysql;

import io.kestra.core.ai.usage.models.AiUsage;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcAiUsageRepository;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlAiUsageRepository extends AbstractJdbcAiUsageRepository {
    @Inject
    public MysqlAiUsageRepository(@Named("aiusage") MysqlRepository<AiUsage> repository) {
        super(repository);
    }
}
