package io.kestra.scheduler.beans;

import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.scheduler.SchedulerConfiguration;
import io.kestra.scheduler.stores.CachedFlowMetaStore;
import io.kestra.scheduler.stores.CachedTriggerStateStore;
import io.kestra.scheduler.stores.DefaultFlowMetaStore;
import io.kestra.scheduler.stores.DefaultTriggerStateStore;
import io.kestra.scheduler.stores.FlowMetaStore;
import io.kestra.scheduler.stores.TriggerStateStore;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Factory
public class BeanFactory {
    
    @Inject
    SchedulerConfiguration schedulerConfiguration;
    
    @Inject
    TriggerRepositoryInterface triggerRepository;
    
    @Inject
    FlowRepositoryInterface flowRepositoryInterface;
    
    @Singleton
    public TriggerStateStore triggerStateStore() {
        DefaultTriggerStateStore store = new DefaultTriggerStateStore(schedulerConfiguration, triggerRepository);
        return schedulerConfiguration.isCacheDisable() ? store : new CachedTriggerStateStore(store, schedulerConfiguration);
    }
    
    @Singleton
    public FlowMetaStore flowMetaStore() {
        DefaultFlowMetaStore store = new DefaultFlowMetaStore(schedulerConfiguration, flowRepositoryInterface);
        return schedulerConfiguration.isCacheDisable() ? store: new CachedFlowMetaStore(store, schedulerConfiguration);
    }
}
