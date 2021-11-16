package io.kestra.runner.kafka.streams;

import io.kestra.runner.kafka.services.SafeKeyValueStore;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.kstream.ValueTransformer;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.kestra.core.services.FlowService;
import io.kestra.runner.kafka.KafkaMultipleConditionStorage;

import java.util.List;
import java.util.stream.Stream;

@Slf4j
public class FlowTriggerWithExecutionTransformer implements ValueTransformer<ExecutorFlowTrigger, List<Execution>> {
    private final String multipleStoreName;
    private final FlowService flowService;

    private KeyValueStore<String, MultipleConditionWindow> multipleStore;
    private SafeKeyValueStore<String, ValueAndTimestamp<Flow>> flowStore;

    public FlowTriggerWithExecutionTransformer(String multipleStoreName, FlowService flowService) {
        this.multipleStoreName = multipleStoreName;
        this.flowService = flowService;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void init(final ProcessorContext context) {
        var flowStore = (KeyValueStore<String, ValueAndTimestamp<Flow>>) context.getStateStore("flow");
        this.flowStore = new SafeKeyValueStore<>(flowStore, flowStore.name());
        this.multipleStore = (KeyValueStore<String, MultipleConditionWindow>) context.getStateStore(this.multipleStoreName);
    }

    @Override
    public List<Execution> transform(final ExecutorFlowTrigger value) {
        MultipleConditionStorageInterface multipleConditionStorage = new KafkaMultipleConditionStorage(this.multipleStore);

        // multiple conditions storage
        this.flowStore.get(Flow.uid(value.getExecution()))
            .ifPresent(flowValueAndTimestamp -> {
            Flow executionFlow = flowValueAndTimestamp.value();

            flowService
                .multipleFlowTrigger(
                    Stream.of(value.getFlowHavingTrigger()),
                    executionFlow,
                    value.getExecution(),
                    multipleConditionStorage
                )
                .forEach(triggerExecutionWindow -> {
                    this.multipleStore.put(triggerExecutionWindow.uid(), triggerExecutionWindow);
                });
        });

        List<Execution> triggeredExecutions = flowService.flowTriggerExecution(
            Stream.of(value.getFlowHavingTrigger()),
            value.getExecution(),
            multipleConditionStorage
        );

        // Trigger is done, remove matching multiple condition
        flowService
            .multipleFlowToDelete(Stream.of(value.getFlowHavingTrigger()), multipleConditionStorage)
            .forEach(r -> this.multipleStore.delete(r.uid()));

        return triggeredExecutions;
    }

    @Override
    public void close() {
    }
}
