package io.kestra.jdbc.runner;

import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.services.AbstractFlowTriggerService;
import io.kestra.core.services.ConditionService;
import io.kestra.core.services.FlowService;
import jakarta.inject.Singleton;

import java.util.Optional;

@JdbcRunnerEnabled
@Singleton
public class JdbcFlowTriggerService extends AbstractFlowTriggerService<AbstractJdbcMultipleConditionStorage> {
    public JdbcFlowTriggerService(Optional<AbstractJdbcMultipleConditionStorage> multipleConditionStorage, ConditionService conditionService, RunContextFactory runContextFactory, FlowService flowService) {
        super(multipleConditionStorage, conditionService, runContextFactory, flowService);
    }
}
