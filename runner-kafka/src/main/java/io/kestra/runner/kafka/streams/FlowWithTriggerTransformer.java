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
        return flowService.flowWithFlowTrigger(kafkaFlowExecutor.allLastVersion().stream())
            .stream()
            .map(f -> KeyValue.pair(f.getFlow().uidWithoutRevision(), new ExecutorFlowTrigger(f.getFlow(), value.getExecution())))
            .collect(Collectors.toList());
    }

    @Override
    public void close() {
    }
}
