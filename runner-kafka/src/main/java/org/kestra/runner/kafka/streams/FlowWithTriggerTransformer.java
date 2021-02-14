package org.kestra.runner.kafka.streams;

import com.google.common.collect.Streams;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.services.FlowService;
import org.kestra.runner.kafka.KafkaExecutor;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class FlowWithTriggerTransformer implements Transformer<String, KafkaExecutor.ExecutionWithFlow, Iterable<KeyValue<String, ExecutorFlowTrigger>>> {
    private final FlowService flowService;

    private KeyValueStore<String, ValueAndTimestamp<Flow>> flowStore;

    public FlowWithTriggerTransformer(FlowService flowService) {
        this.flowService = flowService;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void init(final ProcessorContext context) {
        this.flowStore = (KeyValueStore<String, ValueAndTimestamp<Flow>>) context.getStateStore("flow");
    }

    @SuppressWarnings("UnstableApiUsage")
    @Override
    public Iterable<KeyValue<String, ExecutorFlowTrigger>> transform(String key, KafkaExecutor.ExecutionWithFlow value) {
        try (KeyValueIterator<String, ValueAndTimestamp<Flow>> flows = this.flowStore.all()) {
            List<Flow> allFlows = flowService
                .keepLastVersion(
                    Streams.stream(flows)
                        .map(kv -> kv.value.value())
                )
                .collect(Collectors.toList());

            return flowService.flowWithFlowTrigger(allFlows.stream())
                .stream()
                .map(f -> KeyValue.pair(f.getFlow().uidWithoutRevision(), new ExecutorFlowTrigger(f.getFlow(), value.getExecution())))
                .collect(Collectors.toList());
        }
    }

    @Override
    public void close() {
    }

}
