package io.kestra.runner.kafka.streams;

import io.kestra.core.runners.Executor;
import io.kestra.core.services.FlowService;
import io.kestra.runner.kafka.KafkaFlowExecutor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;

import java.util.stream.Collectors;

@Slf4j
public class FlowWithTriggerTransformer implements Transformer<String, Executor, Iterable<KeyValue<String, ExecutorFlowTrigger>>> {
    private final FlowService flowService;
    private final KafkaFlowExecutor kafkaFlowExecutor;

    public FlowWithTriggerTransformer(KafkaFlowExecutor kafkaFlowExecutor, FlowService flowService) {
        this.flowService = flowService;
        this.kafkaFlowExecutor = kafkaFlowExecutor;
    }

    @Override
    public void init(ProcessorContext context) {

    }

    @Override
    public Iterable<KeyValue<String, ExecutorFlowTrigger>> transform(String key, Executor value) {
        // flowWithFlowTrigger return 1 result per flow per trigger but since we analysed the whole flow on FlowTrigger
        // we deduplicate by flow
        return flowService.flowWithFlowTrigger(kafkaFlowExecutor.allLastVersion().stream())
            .stream()
            .collect(Collectors.toMap(o -> o.getFlow().uidWithoutRevision(), p -> p, (p, q) -> p)).values()
            .stream()
            .map(f -> KeyValue.pair(f.getFlow().uidWithoutRevision(), new ExecutorFlowTrigger(f.getFlow(), value.getExecution())))
            .collect(Collectors.toList());
    }

    @Override
    public void close() {
    }
}
