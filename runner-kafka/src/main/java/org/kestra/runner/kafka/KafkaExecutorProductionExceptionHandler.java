package org.kestra.runner.kafka;

import com.google.common.collect.ImmutableMap;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.kestra.core.exceptions.InternalException;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.LogEntry;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.runner.kafka.configs.TopicsConfig;
import org.kestra.runner.kafka.serializers.JsonDeserializer;
import org.kestra.runner.kafka.services.KafkaStreamSourceService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

@Slf4j
public class KafkaExecutorProductionExceptionHandler implements org.apache.kafka.streams.errors.ProductionExceptionHandler {
    public static final String APPLICATION_CONTEXT_CONFIG = "application.context";

    private ApplicationContext applicationContext;
    private KafkaQueue<Execution> executorQueue;
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

            if (topicsConfig.getKey().equals(KafkaStreamSourceService.TOPIC_EXECUTOR) || topicsConfig.getCls() == Execution.class) {
                Execution execution = getObject(Execution.class, record);

                Execution.FailedExecutionWithLog failedExecutionWithLog = execution.failedExecutionFromExecutor(exception);
                Execution sendExecution = failedExecutionWithLog.getExecution();

                failedExecutionWithLog.getLogs().forEach(logEntry -> logQueue.emit(logEntry));

                if (exception instanceof RecordTooLargeException) {
                    boolean exit = false;
                    while (!exit) {
                        try {
                            sendExecution = reduceOutputs(sendExecution);
                            executorQueue.emit(sendExecution);
                            exit = true;
                        } catch (Exception e) {
                            exit = sendExecution.getTaskRunList().size() > 0;
                        }
                    }

                    return ProductionExceptionHandlerResponse.CONTINUE;
                } else {
                    executorQueue.emit(sendExecution);
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
        applicationContext = (ApplicationContext) configs.get(APPLICATION_CONTEXT_CONFIG);

        executorQueue = (KafkaQueue<Execution>) applicationContext.getBean(
            QueueInterface.class,
            Qualifiers.byName(QueueFactoryInterface.EXECUTOR_NAMED)
        );

        logQueue = (KafkaQueue<LogEntry>) applicationContext.getBean(
            QueueInterface.class,
            Qualifiers.byName(QueueFactoryInterface.LOG_NAMED)
        );
    }
}
