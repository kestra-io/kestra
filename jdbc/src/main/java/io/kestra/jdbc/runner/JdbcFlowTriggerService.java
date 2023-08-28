package io.kestra.jdbc.runner;

import io.kestra.core.services.AbstractFlowTriggerService;
import jakarta.inject.Singleton;

@JdbcRunnerEnabled
@Singleton
public class JdbcFlowTriggerService extends AbstractFlowTriggerService {
}
