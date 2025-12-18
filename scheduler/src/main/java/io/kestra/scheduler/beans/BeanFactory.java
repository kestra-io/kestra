package io.kestra.scheduler.beans;

import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.scheduler.SchedulerConfiguration;
import io.kestra.core.scheduler.store.TriggerStateStore;
import io.kestra.scheduler.stores.CachedFlowMetaStore;
import io.kestra.scheduler.stores.CachedTriggerStateStore;
import io.kestra.scheduler.stores.DefaultFlowMetaStore;
import io.kestra.scheduler.stores.FlowMetaStore;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import jakarta.inject.Inject;
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
        return schedulerConfiguration.isCacheDisable() ? store: new CachedFlowMetaStore(store, schedulerConfiguration);
    }

    @Singleton
    public static class TriggerStateStoreBeanDecorator implements BeanCreatedEventListener<TriggerStateStore> {

        @Inject
        SchedulerConfiguration configuration;
        
        @Override
        public TriggerStateStore onCreated(BeanCreatedEvent<TriggerStateStore> event) {
            TriggerStateStore bean = event.getBean();
            return configuration.isCacheDisable() ? bean : new CachedTriggerStateStore(bean, configuration);
        }
    }
}
