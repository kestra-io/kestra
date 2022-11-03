package io.kestra.runner.kafka;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.runners.Executor;
import io.kestra.runner.kafka.services.KafkaStreamService;
import io.kestra.runner.kafka.streams.ExecutorFlowTrigger;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.RecordTooLargeException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.runner.kafka.configs.TopicsConfig;
import io.kestra.runner.kafka.serializers.JsonDeserializer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

@Slf4j
public class KafkaExecutorProductionExceptionHandler implements org.apache.kafka.streams.errors.ProductionExceptionHandler {
    private ApplicationContext applicationContext;
    private KafkaQueue<Execution> executionQueue;
    private KafkaQueue<Executor> executorQueue;
    private KafkaQueue<LogEntry> logQueue;

    @Override
    public ProductionExceptionHandlerResponse handle(ProducerRecord<byte[], byte[]> record, Exception exception) {
        if (log.isDebugEnabled()) {
            log.error(
                "Failed to produced message on topic '{}', partition '{}', key '{}', value '{}' with exception '{}'",
                record.topic(),
                record.partition(),
                new String(record.key()),
                new String(record.value()),
                exception.getMessage(),
                exception
            );
        } else {
            log.error(
                "Failed to produced message on topic '{}', partition '{}', key '{}' with exception '{}'",
                record.topic(),
                record.partition(),
                new String(record.key()),
                exception.getMessage(),
                exception
            );
        }

        try {
            TopicsConfig topicsConfig = KafkaQueue.topicsConfigByTopicName(applicationContext, record.topic());


            if (topicsConfig.getCls() == Executor.class || topicsConfig.getCls() == ExecutorFlowTrigger.class) {
                return ProductionExceptionHandlerResponse.CONTINUE;
            } else if (topicsConfig.getCls() == Execution.class) {
                Execution execution = getObject(Execution.class, record);

                Execution.FailedExecutionWithLog failedExecutionWithLog = execution.failedExecutionFromExecutor(exception);
                Execution sendExecution = failedExecutionWithLog.getExecution();

                failedExecutionWithLog.getLogs().forEach(logEntry -> logQueue.emitAsync(logEntry));

                if (exception instanceof RecordTooLargeException) {
                    boolean exit = false;
                    while (!exit) {
                        try {
                            sendExecution = reduceOutputs(sendExecution);
                            executionQueue.emit(sendExecution);
                            executorQueue.emit(null);
                            exit = true;
                        } catch (Exception e) {
                            exit = sendExecution.getTaskRunList().size() > 0;
                        }
                    }

                    return ProductionExceptionHandlerResponse.CONTINUE;
                } else {
                    executionQueue.emit(sendExecution);
                }
            }
        } catch (Exception e) {
            log.warn("Can't resolve failed produce with exception '{}'", e.getMessage(), e);
        }

        return ProductionExceptionHandlerResponse.FAIL;
    }

    private Execution reduceOutputs(Execution execution) throws InternalException {
        if (execution.getTaskRunList().size() == 0) {
            return execution;
        }

        ArrayList<TaskRun> reverse = new ArrayList<>(execution.getTaskRunList());
        Collections.reverse(reverse);

        TaskRun taskRun = reverse.get(0).withOutputs(ImmutableMap.of());

        return execution.withTaskRun(taskRun);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> T getObject(Class<T> cls, ProducerRecord<byte[], byte[]> record) {
        try (JsonDeserializer jsonDeserializer = new JsonDeserializer(cls)) {
            return (T) jsonDeserializer.deserialize(record.topic(), record.value());
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void configure(Map<String, ?> configs) {
        applicationContext = (ApplicationContext) configs.get(KafkaStreamService.APPLICATION_CONTEXT_CONFIG);

        executionQueue = (KafkaQueue<Execution>) applicationContext.getBean(
            QueueInterface.class,
            Qualifiers.byName(QueueFactoryInterface.EXECUTION_NAMED)
        );

        executorQueue = (KafkaQueue<Executor>) applicationContext.getBean(
            QueueInterface.class,
            Qualifiers.byName(QueueFactoryInterface.EXECUTOR_NAMED)
        );

        logQueue = (KafkaQueue<LogEntry>) applicationContext.getBean(
            QueueInterface.class,
            Qualifiers.byName(QueueFactoryInterface.WORKERTASKLOG_NAMED)
        );
    }
}
