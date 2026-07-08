package io.kestra.core.lock;

import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.ServiceResourceReleaser;
import io.kestra.core.utils.IdUtils;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Release locks for dead services.
 */
@Slf4j
@Singleton
public class LockServiceResourceReleaser implements ServiceResourceReleaser {
    private final LockService lockService;

    @Inject
    public LockServiceResourceReleaser(LockService lockService) {
        this.lockService = lockService;
    }

    @Override
    public void releaseResources(ServiceInstance serviceInstance, String reason) {
        // Eventually release all owned locks
        lockService.releaseAllLocks(serviceInstance.server().id())
            .forEach(l -> log.info("Released lock '{}' for service instance '{}'. Reason: {}", IdUtils.fromParts(l.getCategory(), l.getId()), serviceInstance.server().id(), reason));

    }
}
