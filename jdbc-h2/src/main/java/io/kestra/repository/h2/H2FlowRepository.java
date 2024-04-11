package io.kestra.repository.h2;

import io.kestra.core.models.flows.Flow;
import io.kestra.jdbc.repository.AbstractJdbcFlowRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jooq.Condition;

import java.util.Map;

@Singleton
@H2RepositoryEnabled
public class H2FlowRepository extends AbstractJdbcFlowRepository {
    @Inject
    public H2FlowRepository(@Named("flows") H2Repository<Flow> repository,
                            ApplicationContext applicationContext) {
        super(repository, applicationContext);
    }

    @Override
    protected Condition findCondition(String query, Map<String, String> labels) {
        return H2FlowRepositoryService.findCondition(this.jdbcRepository, query, labels);
    }

    @Override
    protected Condition findSourceCodeCondition(String query) {
        return H2FlowRepositoryService.findSourceCodeCondition(this.jdbcRepository, query);
    }
}
