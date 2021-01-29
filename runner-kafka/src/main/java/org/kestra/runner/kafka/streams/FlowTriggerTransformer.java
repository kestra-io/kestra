package org.kestra.runner.kafka.streams;

import com.google.common.collect.Streams;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.kstream.ValueTransformer;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import org.kestra.core.services.FlowService;
import org.kestra.runner.kafka.KafkaExecutor;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class FlowTriggerTransformer implements ValueTransformer<KafkaExecutor.ExecutionWithFlow, List<Execution>> {
    private final String multipleStoreName;
    private final FlowService flowService;

    private KeyValueStore<String, MultipleConditionWindow> multipleStore;
    private KeyValueStore<String, ValueAndTimestamp<Flow>> flowStore;

    public FlowTriggerTransformer(String multipleStoreName, FlowService flowService) {
        this.multipleStoreName = multipleStoreName;
        this.flowService = flowService;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void init(final ProcessorContext context) {
        this.flowStore = (KeyValueStore<String, ValueAndTimestamp<Flow>>) context.getStateStore("flow");
        this.multipleStore = (KeyValueStore<String, MultipleConditionWindow>) context.getStateStore(this.multipleStoreName);
    }

    @SuppressWarnings("UnstableApiUsage")
    @Override
    public List<Execution> transform(final KafkaExecutor.ExecutionWithFlow value) {
        try (KeyValueIterator<String, ValueAndTimestamp<Flow>> flows = this.flowStore.all()) {
            List<Flow> allFlows = flowService
                .keepLastVersion(
                    Streams.stream(flows)
                        .map(kv -> kv.value.value())
                )
                .collect(Collectors.toList());

            Flow currentFlows = allFlows.stream()
                .filter(flow -> flow.uid().equals(Flow.uid(value.getExecution())))
                .findFirst()
                .orElseThrow();

            // multiple conditions storage
            flowService
                .multipleFlowTrigger(allFlows.stream(), currentFlows, value.getExecution())
                .forEach(triggerExecutionWindow -> {
                    this.multipleStore.put(triggerExecutionWindow.uid(), triggerExecutionWindow);


                    log.info("put: {}", triggerExecutionWindow);

                });

            List<Execution> triggeredExecutions = flowService.flowTriggerExecution(
                allFlows.stream(),
                value.getExecution()
            );

            // Trigger is done, remove matching multiple condition
            flowService
                .multipleFlowToDelete(allFlows.stream())
                .forEach(r -> this.multipleStore.delete(r.uid()));

            return triggeredExecutions;
        }
    }

    @Override
    public void close() {
    }
}
