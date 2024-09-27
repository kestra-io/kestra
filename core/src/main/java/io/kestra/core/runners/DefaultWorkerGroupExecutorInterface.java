package io.kestra.core.runners;

import io.kestra.core.services.WorkerGroupService;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Objects;

@Singleton
@Requires(missingBeans = WorkerGroupExecutorInterface.class)
public class DefaultWorkerGroupExecutorInterface implements WorkerGroupExecutorInterface {

    private final WorkerGroupService service;

    /**
     * Creates a new {@link DefaultWorkerGroupExecutorInterface} instance.
     *
     * @param service {@link WorkerGroupService} to use.
     */
    @Inject
    public DefaultWorkerGroupExecutorInterface(final WorkerGroupService service) {
        this.service = Objects.requireNonNull(service, "service cannot be null");
    }

    /** {@inheritDoc} **/
    @Override
    public boolean isWorkerGroupExistForKey(String key) {
        return service.isWorkerGroupExistForKey(key);
    }

    @Override
    public boolean isWorkerGroupAvailableForKey(String key) {
        return service.isWorkerGroupAvailableForKey(key);
    }
}
