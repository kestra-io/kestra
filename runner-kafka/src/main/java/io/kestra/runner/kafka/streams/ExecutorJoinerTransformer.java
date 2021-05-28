package io.kestra.runner.kafka.streams;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.runners.Executor;
import io.kestra.core.runners.WorkerTaskResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;

@Slf4j
public class ExecutorJoinerTransformer implements ValueTransformerWithKey<String, Executor, Executor> {
    private final String storeName;
    private final MetricRegistry metricRegistry;
    private KeyValueStore<String, Executor> store;
    private ProcessorContext context;

    public ExecutorJoinerTransformer(String storeName, MetricRegistry metricRegistry) {
        this.storeName = storeName;
        this.metricRegistry = metricRegistry;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void init(final ProcessorContext context) {
        this.context = context;
        this.store = (KeyValueStore<String, Executor>) context.getStateStore(this.storeName);
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

        if (executor == null) {
            throw new IllegalStateException("Unable to find executor with key '" + key + "'");
        }

        if (!executor.getExecution().hasTaskRunJoinable(value.getJoined().getTaskRun())) {
            return executor;
        }

        try {
            Execution newExecution = executor.getExecution().withTaskRun(workerTaskResult.getTaskRun());
            executor = executor.withExecution(newExecution, "joinWorkerResult");
        } catch (Exception e) {
            return executor.withException(e, "joinWorkerResult");
        }

        // send metrics
        metricRegistry
            .counter(
                MetricRegistry.KESTRA_EXECUTOR_TASKRUN_ENDED_COUNT,
                metricRegistry.tags(workerTaskResult)
            )
            .increment();

        metricRegistry
            .timer(
                MetricRegistry.KESTRA_EXECUTOR_TASKRUN_ENDED_DURATION,
                metricRegistry.tags(workerTaskResult)
            )
            .record(workerTaskResult.getTaskRun().getState().getDuration());

        return executor;
    }

    @Override
    public void close() {
    }
}
