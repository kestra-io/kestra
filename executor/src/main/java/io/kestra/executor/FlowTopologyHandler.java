package io.kestra.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowTopologyRepositoryInterface;
import io.kestra.core.runners.FlowMetaStoreInterface;
import io.kestra.core.services.PluginDefaultService;
import io.kestra.core.topologies.FlowTopologyService;
import io.kestra.core.utils.Either;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * This component is responsible to compute the flow topology on each flow message received from the Flow queue.
 */
@Singleton
@Slf4j
@Requires(property = "kestra.server-type", pattern = "(EXECUTOR|STANDALONE)")
public class FlowTopologyHandler implements AutoCloseable {
    private static final ObjectMapper MAPPER = ExecutorMapper.of();

    private final QueueInterface<FlowInterface> flowQueue;
    private final FlowTopologyRepositoryInterface flowTopologyRepository;
    private final FlowTopologyService flowTopologyService;
    private final PluginDefaultService pluginDefaultService;
    private final FlowMetaStoreInterface flowMetaStore;

    private Runnable cancellation;

    @Inject
    public FlowTopologyHandler(@Named(QueueFactoryInterface.FLOW_NAMED) QueueInterface<FlowInterface> flowQueue,
                               FlowTopologyRepositoryInterface flowTopologyRepository,
                               FlowTopologyService flowTopologyService,
                               PluginDefaultService pluginDefaultService,
                               FlowMetaStoreInterface flowMetaStore
                               ) {
        this.flowQueue = flowQueue;
        this.flowTopologyRepository = flowTopologyRepository;
        this.flowTopologyService = flowTopologyService;
        this.pluginDefaultService = pluginDefaultService;
        this.flowMetaStore = flowMetaStore;
    }

    // Make it a StartupEvent listener so it starts when Kestra start
    @EventListener
    public void run(StartupEvent event) {
        cancellation = this.flowQueue.receive(FlowTopology.class, this::flowQueue); // TODO it should be FlowTopologyHandler but if we do this we may loose pending messages
    }

    private void flowQueue(Either<FlowInterface, DeserializationException> either) {
        FlowInterface flow;
        if (either.isRight()) {
            log.error("Unable to deserialize a flow: {}", either.getRight().getMessage());
            try {
                var jsonNode = MAPPER.readTree(either.getRight().getRecord());
                flow = FlowWithException.from(jsonNode, either.getRight()).orElseThrow(IOException::new);
            } catch (IOException e) {
                // if we cannot create a FlowWithException, ignore the message
                log.error("Unexpected exception when trying to handle a deserialization error", e);
                return;
            }
        } else {
            flow = either.getLeft();
        }

        try {
            flowTopologyRepository.save(
                flow,
                (flow.isDeleted() ?
                    Stream.<FlowTopology>empty() :
                    flowTopologyService
                        .topology(
                            pluginDefaultService.injectVersionDefaults(flow, true),
                            flowMetaStore.allLastVersion().stream().filter(f -> Objects.equals(f.getTenantId(), flow.getTenantId())).toList()
                        )
                )
                    .distinct()
                    .toList()
            );
        } catch (Exception e) {
            log.error("Unable to save flow topology for flow {}", flow.uid(), e);
        }
    }

    @Override
    @PreDestroy
    public void close() {
        if (cancellation != null) {
            cancellation.run();
        }
    }
}
