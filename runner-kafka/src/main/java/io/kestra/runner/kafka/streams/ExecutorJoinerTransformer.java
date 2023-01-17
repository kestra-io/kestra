package io.kestra.runner.kafka.streams;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.runners.Executor;
import io.kestra.core.runners.ExecutorService;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.runner.kafka.services.KafkaStreamSourceService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;

@Slf4j
public class ExecutorJoinerTransformer implements ValueTransformerWithKey<String, Executor, Executor> {
    private final String storeName;
    private final ExecutorService executorService;
    private final KafkaStreamSourceService kafkaStreamSourceService;
    private final MetricRegistry metricRegistry;
    private KeyValueStore<String, Executor> store;
    private ProcessorContext context;

    public ExecutorJoinerTransformer(String storeName, ExecutorService executorService, KafkaStreamSourceService kafkaStreamSourceService, MetricRegistry metricRegistry) {
        this.storeName = storeName;
        this.executorService = executorService;
        this.kafkaStreamSourceService = kafkaStreamSourceService;
        this.metricRegistry = metricRegistry;
    }

    @Override
    public void init(final ProcessorContext context) {
        this.context = context;
        this.store = context.getStateStore(this.storeName);
    }

    @Override
    public Executor transform(final String key, final Executor value) {
        if (value.getExecution() != null) {
            return value;
        }

        WorkerTaskResult workerTaskResult = value.getJoined();

        if (log.isDebugEnabled()) {
            log.debug(
                "<< IN  WorkerTaskResult [key='{}', partition='{}, offset='{}'] : {}",
                key,
                context.partition(),
                context.offset(),
                workerTaskResult.getTaskRun().toStringState()
            );
        }

        Executor executor = this.store.get(key);

        // already purge execution ?
        if (executor == null) {
            log.warn("Unable to find Executor with key '" + key + "' for WorkerTaskResult id '" + workerTaskResult.getTaskRun().getId() + "' '" + workerTaskResult.getTaskRun().toStringState() + "'");
            return null;
        }

        if (!executor.getExecution().hasTaskRunJoinable(value.getJoined().getTaskRun())) {
            return executor;
        }

        kafkaStreamSourceService.joinFlow(executor, true);

        try {
            Execution newExecution = executorService.addDynamicTaskRun(
                executor.getExecution(),
                executor.getFlow(),
                workerTaskResult
            );

            if (newExecution != null) {
                executor = executor.withExecution(newExecution, "addDynamicTaskRun");
            }

            newExecution = executor.getExecution().withTaskRun(workerTaskResult.getTaskRun());
            executor = executor.withExecution(newExecution, "joinWorkerResult");
        } catch (Exception e) {
            return executor.withException(e, "joinWorkerResult");
        }

        // send metrics on terminated
        if (workerTaskResult.getTaskRun().getState().isTerninated()) {
            metricRegistry
                .counter(
                    MetricRegistry.EXECUTOR_TASKRUN_ENDED_COUNT,
                    metricRegistry.tags(workerTaskResult)
                )
                .increment();

            metricRegistry
                .timer(
                    MetricRegistry.EXECUTOR_TASKRUN_ENDED_DURATION,
                    metricRegistry.tags(workerTaskResult)
                )
                .record(workerTaskResult.getTaskRun().getState().getDuration());
        }

        return executor;
    }

    @Override
    public void close() {
    }
}
