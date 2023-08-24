package io.kestra.runner.memory;

import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.services.AbstractFlowTriggerService;
import io.kestra.core.services.ConditionService;
import io.kestra.core.services.FlowService;
import jakarta.inject.Singleton;

import javax.annotation.PostConstruct;
import java.util.Optional;

@MemoryQueueEnabled
@Singleton
public class MemoryFlowTriggerService extends AbstractFlowTriggerService<MemoryMultipleConditionStorage> {
    public MemoryFlowTriggerService(Optional<MemoryMultipleConditionStorage> multipleConditionStorage, ConditionService conditionService, RunContextFactory runContextFactory, FlowService flowService) {
        super(multipleConditionStorage, conditionService, runContextFactory, flowService);
    }

    @PostConstruct
    private void init() {
        this.multipleConditionStorage = Optional.of(new MemoryMultipleConditionStorage());
    }
}
