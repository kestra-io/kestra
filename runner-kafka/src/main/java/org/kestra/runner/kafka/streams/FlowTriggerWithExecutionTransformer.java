package org.kestra.runner.kafka.streams;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.kstream.ValueTransformer;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import org.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import org.kestra.core.services.FlowService;
import org.kestra.runner.kafka.KafkaMultipleConditionStorage;

import java.util.List;
import java.util.stream.Stream;

@Slf4j
public class FlowTriggerWithExecutionTransformer implements ValueTransformer<ExecutorFlowTrigger, List<Execution>> {
    private final String multipleStoreName;
    private final FlowService flowService;

    private KeyValueStore<String, MultipleConditionWindow> multipleStore;
    private KeyValueStore<String, ValueAndTimestamp<Flow>> flowStore;

    public FlowTriggerWithExecutionTransformer(String multipleStoreName, FlowService flowService) {
        this.multipleStoreName = multipleStoreName;
        this.flowService = flowService;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void init(final ProcessorContext context) {
        this.flowStore = (KeyValueStore<String, ValueAndTimestamp<Flow>>) context.getStateStore("flow");
        this.multipleStore = (KeyValueStore<String, MultipleConditionWindow>) context.getStateStore(this.multipleStoreName);
    }

    @Override
    public List<Execution> transform(final ExecutorFlowTrigger value) {
        ValueAndTimestamp<Flow> flowValueAndTimestamp = this.flowStore.get(Flow.uid(value.getExecution()));
        Flow executionFlow = flowValueAndTimestamp.value();

        MultipleConditionStorageInterface multipleConditionStorage = new KafkaMultipleConditionStorage(this.multipleStore);

        // multiple conditions storage
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
