package io.kestra.core.vnodes;

import io.kestra.core.lock.LockService;
import io.kestra.core.server.ServiceLivenessStore;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

/**
 * Assigns the vNodes of the worker controllers.
 */
@Factory
public class ControllerVNodesFactory {

    @Singleton
    @Bean(preDestroy = "close")
    public VNodeController controllerVNodeController(LockService lockService,
        ServiceLivenessStore serviceLivenessStore,
        ControllerVNodesAssignmentChannel channel,
        ControllerVNodesConfiguration configuration) {
        return new VNodeController(lockService, serviceLivenessStore, channel, configuration.count(), configuration.rebalanceTimeout());
    }
}
