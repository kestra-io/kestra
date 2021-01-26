package org.kestra.runner.kafka.streams;

import com.google.common.collect.Streams;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.kstream.ValueTransformer;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;
import org.apache.kafka.streams.state.WindowStore;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.services.FlowService;
import org.kestra.runner.kafka.KafkaExecutor;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class FlowTriggerTransformer implements ValueTransformer<KafkaExecutor.ExecutionWithFlow, List<Execution>> {
    private final String multipleStoreName;
    private final FlowService flowService;

    private WindowStore<String, Counter> multipleStore;
    private KeyValueStore<String, ValueAndTimestamp<Flow>> flowStore;

    public FlowTriggerTransformer(String multipleStoreName, FlowService flowService) {
        this.multipleStoreName = multipleStoreName;
        this.flowService = flowService;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void init(final ProcessorContext context) {
//        this.multipleStore = (WindowStore<String, Counter>) context.getStateStore("flow");
        this.flowStore = (KeyValueStore<String, ValueAndTimestamp<Flow>>) context.getStateStore("flow");
    }

    @SuppressWarnings("UnstableApiUsage")
    @Override
    public List<Execution> transform(final KafkaExecutor.ExecutionWithFlow value) {
        try (KeyValueIterator<String, ValueAndTimestamp<Flow>> flows = this.flowStore.all()) {
            Stream<Flow> flowStream = flowService.keepLastVersion(
                Streams.stream(flows)
                    .map(kv -> kv.value.value())
            );
            return flowService.flowTriggerExecution(
                flowStream,
                value.getExecution()
            );
//            return
//                Stream.concat(
//                    flowService.flowTriggerExecution(
//                        flowStream,
//                        value.getExecution()
//                    ).stream(),
//                    flowService.flowTriggerExecution(
//                        flowStream,
//                        value.getExecution()
//                    ).stream()
//                )
//                .collect(Collectors.toList());
        }
    }

    @Override
    public void close() {
    }

    public static class Counter {
        String uid;
        ZonedDateTime start;
        ZonedDateTime end;
        Map<String, Long> counts;
        String namespace;
        String flowId;
    }
}
