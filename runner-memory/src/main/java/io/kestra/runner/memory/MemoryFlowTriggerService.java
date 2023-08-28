package io.kestra.runner.memory;

import io.kestra.core.services.AbstractFlowTriggerService;
import jakarta.inject.Singleton;

@MemoryQueueEnabled
@Singleton
public class MemoryFlowTriggerService extends AbstractFlowTriggerService {
}
