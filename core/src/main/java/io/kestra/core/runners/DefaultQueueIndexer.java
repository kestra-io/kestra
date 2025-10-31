package io.kestra.core.runners;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.repositories.FlowTopologyRepositoryInterface;
import io.micronaut.context.ApplicationContext;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * This class is responsible to index the queue synchronously at message production time. It is used by the Queue itself<p>
 * Some queue messages are batch-indexed asynchronously via the regular {@link io.kestra.core.runners.Indexer}
 * which listen to (receive) those queue messages.
 */
@Slf4j
@Singleton
public class DefaultQueueIndexer implements QueueIndexer {
    private volatile Map<Class<?>, QueueIndexerRepository<?>> repositories;

    private final MetricRegistry metricRegistry;
    private final ApplicationContext applicationContext;

    @Inject
    public DefaultQueueIndexer(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        this.metricRegistry = applicationContext.getBean(MetricRegistry.class);
    }

    private Map<Class<?>, QueueIndexerRepository<?>> getRepositories() {
        if (repositories == null) {
            synchronized (this) {
                if (repositories == null) {
                    repositories = new HashMap<>();
                    applicationContext.getBeansOfType(QueueIndexerRepository.class)
                        .forEach(saveRepositoryInterface -> {
                            repositories.put(saveRepositoryInterface.getItemClass(), saveRepositoryInterface);
                        });
                }
            }
        }

        return repositories;
    }

    // FIXME this today limit this indexer to only JDBC queue and repository.
    //  to be able to use JDBC queue with another repository we would need to check in each QueueIndexerRepository if it's a Jdbc transaction before casting
    @Override
    public void accept(TransactionContext txContext, Object item) {
        Map<Class<?>, QueueIndexerRepository<?>> repositories = getRepositories();
        if (repositories.containsKey(item.getClass())) {
            this.metricRegistry.counter(MetricRegistry.METRIC_INDEXER_REQUEST_COUNT, MetricRegistry.METRIC_INDEXER_REQUEST_COUNT_DESCRIPTION, "type", item.getClass().getName()).increment();
            this.metricRegistry.counter(MetricRegistry.METRIC_INDEXER_MESSAGE_IN_COUNT, MetricRegistry.METRIC_INDEXER_MESSAGE_IN_COUNT_DESCRIPTION, "type", item.getClass().getName()).increment();

            this.metricRegistry.timer(MetricRegistry.METRIC_INDEXER_REQUEST_DURATION, MetricRegistry.METRIC_INDEXER_REQUEST_DURATION_DESCRIPTION, "type", item.getClass().getName()).record(() -> {
                QueueIndexerRepository<?> indexerRepository = repositories.get(item.getClass());
                if (indexerRepository instanceof FlowTopologyRepositoryInterface) {
                    // we allow flow topology to fail indexation
                    try {
                        save(indexerRepository, txContext, item);
                    } catch (Exception e) {
                        log.error("Unable to index a flow topology, skipping it", e);
                    }
                } else {
                    save(indexerRepository, txContext, cast(item));
                }

                this.metricRegistry.counter(MetricRegistry.METRIC_INDEXER_MESSAGE_OUT_COUNT, MetricRegistry.METRIC_INDEXER_MESSAGE_OUT_COUNT_DESCRIPTION, "type", item.getClass().getName()).increment();
            });

        }
    }

    private void save(QueueIndexerRepository<?> indexerRepository, TransactionContext txContext, Object item) {
        if (indexerRepository.supports(txContext.getClass())) {
            indexerRepository.save(txContext, cast(item));
        } else {
            indexerRepository.save(cast(item));
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object message) {
        return (T) message;
    }
}
