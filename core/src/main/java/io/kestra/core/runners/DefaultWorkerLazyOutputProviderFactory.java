package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.services.TaskOutputService;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Default {@link WorkerLazyOutputProviderFactory} for standalone/executor mode.
 * Fetches the {@link Execution} from the repository and delegates to {@link TaskOutputService}.
 */
@Singleton
@Requires(property = "kestra.server-type", notEquals = "WORKER")
public class DefaultWorkerLazyOutputProviderFactory implements WorkerLazyOutputProviderFactory {

    private final TaskOutputService taskOutputService;
    private final ExecutionRepositoryInterface executionRepository;

    @Inject
    public DefaultWorkerLazyOutputProviderFactory(TaskOutputService taskOutputService,
                                                   ExecutionRepositoryInterface executionRepository) {
        this.taskOutputService = taskOutputService;
        this.executionRepository = executionRepository;
    }

    /** {@inheritDoc} */
    @Override
    public LazyOutputProvider create(String tenantId, String executionId) {
        Execution execution = executionRepository.findByIdWithoutAcl(tenantId, executionId).orElse(null);
        return new DefaultLazyOutputProvider(taskOutputService, execution);
    }
}
