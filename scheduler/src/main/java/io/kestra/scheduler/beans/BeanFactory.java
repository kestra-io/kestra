package io.kestra.scheduler.beans;

import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.scheduler.SchedulerConfiguration;
import io.kestra.core.scheduler.store.TriggerStateStore;
import io.kestra.scheduler.stores.CachedFlowMetaStore;
import io.kestra.scheduler.stores.CachedTriggerStateStore;
import io.kestra.scheduler.stores.DefaultFlowMetaStore;
import io.kestra.scheduler.stores.FlowMetaStore;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Secondary;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Factory
public class BeanFactory {

    @Inject
    SchedulerConfiguration schedulerConfiguration;

    @Inject
    FlowRepositoryInterface flowRepositoryInterface;

    @Singleton
    public FlowMetaStore flowMetaStore() {
        DefaultFlowMetaStore store = new DefaultFlowMetaStore(schedulerConfiguration, flowRepositoryInterface);
        return schedulerConfiguration.isCacheDisable() ? store : new CachedFlowMetaStore(store, schedulerConfiguration);
    }

    @Named("cached")
    @Singleton
    @Secondary
    public TriggerStateStore triggerStateStore(@Primary TriggerStateStore triggerStateStore, SchedulerConfiguration configuration) {
        return configuration.isCacheDisable() ? triggerStateStore : new CachedTriggerStateStore(triggerStateStore, configuration);
    }
}
