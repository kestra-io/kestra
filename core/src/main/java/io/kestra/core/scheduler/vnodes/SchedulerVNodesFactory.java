package io.kestra.core.scheduler.vnodes;

import io.kestra.core.lock.LockService;
import io.kestra.core.scheduler.SchedulerConfiguration;
import io.kestra.core.scheduler.queue.SchedulerEventQueue;
import io.kestra.core.server.ServiceLivenessStore;
import io.kestra.core.vnodes.VNodeController;
import io.kestra.core.vnodes.VNodesAssigner;
import io.kestra.core.vnodes.internals.DefaultVNodesAssigner;
import io.kestra.core.vnodes.internals.StaticVNodesAssigner;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * Assigns the vNodes of the schedulers.
 */
@Factory
public class SchedulerVNodesFactory {

    @Singleton
    @Bean(preDestroy = "close")
    public VNodeController schedulerVNodeController(LockService lockService,
        ServiceLivenessStore serviceLivenessStore,
        SchedulerEventQueue schedulerEventQueue,
        SchedulerConfiguration schedulerConfiguration) {
        return new VNodeController(
            lockService,
            serviceLivenessStore,
            new SchedulerVNodesAssignmentChannel(schedulerEventQueue),
            schedulerConfiguration.vnodes(),
            schedulerConfiguration.vnodesRebalanceTimeout()
        );
    }

    @Singleton
    @Requires(property = "kestra.server-type")
    @Bean(preDestroy = "stop")
    public DefaultVNodesAssigner schedulerVNodesAssigner(SchedulerEventQueue schedulerEventQueue) {
        DefaultVNodesAssigner assigner = new DefaultVNodesAssigner(new SchedulerVNodesAssignmentChannel(schedulerEventQueue));
        assigner.start();
        return assigner;
    }

    @Singleton
    @Requires(missingBeans = VNodesAssigner.class)
    public StaticVNodesAssigner staticSchedulerVNodesAssigner(SchedulerConfiguration schedulerConfiguration) {
        return new StaticVNodesAssigner(schedulerConfiguration.vnodes());
    }
}
