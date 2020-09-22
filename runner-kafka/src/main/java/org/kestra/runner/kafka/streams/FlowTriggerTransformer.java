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
import org.kestra.core.services.FlowService;
import org.kestra.runner.kafka.KafkaExecutor;

import java.util.List;

@Slf4j
public class FlowTriggerTransformer implements ValueTransformer<KafkaExecutor.ExecutionWithFlow, List<Execution>> {
    private KeyValueStore<String, ValueAndTimestamp<Flow>> store;
    private final FlowService flowService;

    public FlowTriggerTransformer(FlowService flowService) {
        this.flowService = flowService;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void init(final ProcessorContext context) {
        this.store = (KeyValueStore<String, ValueAndTimestamp<Flow>>) context.getStateStore("flow");
    }

    @SuppressWarnings("UnstableApiUsage")
    @Override
    public List<Execution> transform(final KafkaExecutor.ExecutionWithFlow value) {
        try (KeyValueIterator<String, ValueAndTimestamp<Flow>> flows = this.store.all()) {
            return flowService.flowTriggerExecution(
                flowService.keepLastVersion(
                    Streams.stream(flows)
                        .map(kv -> kv.value.value())
                ),
                value.getExecution()
            );
        }
    }

    @Override
    public void close() {
    }
}
