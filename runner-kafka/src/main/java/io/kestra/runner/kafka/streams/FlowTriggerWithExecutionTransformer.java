package io.kestra.runner.kafka.streams;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.kestra.core.services.FlowService;
import io.kestra.runner.kafka.KafkaFlowExecutor;
import io.kestra.runner.kafka.KafkaMultipleConditionStorage;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.kstream.ValueTransformer;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;

import java.util.List;
import java.util.stream.Stream;

@Slf4j
public class FlowTriggerWithExecutionTransformer implements ValueTransformer<ExecutorFlowTrigger, List<Execution>> {
    private final String multipleStoreName;
    private final FlowService flowService;
    private final KafkaFlowExecutor kafkaFlowExecutor;

    private KeyValueStore<String, MultipleConditionWindow> multipleStore;

    public FlowTriggerWithExecutionTransformer(String multipleStoreName, KafkaFlowExecutor kafkaFlowExecutor, FlowService flowService) {
        this.multipleStoreName = multipleStoreName;
        this.kafkaFlowExecutor = kafkaFlowExecutor;
        this.flowService = flowService;
    }

    @Override
    public void init(final ProcessorContext context) {
        this.multipleStore = context.getStateStore(this.multipleStoreName);
    }

    @Override
    public List<Execution> transform(final ExecutorFlowTrigger value) {
        MultipleConditionStorageInterface multipleConditionStorage = new KafkaMultipleConditionStorage(this.multipleStore);

        // multiple conditions storage
        kafkaFlowExecutor.findByExecution(value.getExecution())
            .ifPresent(flow -> {
                flowService
                    .multipleFlowTrigger(
                        Stream.of(value.getFlowHavingTrigger()),
                        flow,
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
