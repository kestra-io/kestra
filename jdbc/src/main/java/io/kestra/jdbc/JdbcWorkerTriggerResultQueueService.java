package io.kestra.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.WorkerTriggerResult;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.Either;
import io.kestra.jdbc.repository.AbstractJdbcWorkerJobRunningRepository;
import io.kestra.jdbc.runner.JdbcQueue;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

@Singleton
@Slf4j
public class JdbcWorkerTriggerResultQueueService {
    private final static ObjectMapper MAPPER = JacksonMapper.ofJson();

    private final JdbcQueue<WorkerTriggerResult> workerTriggerResultQueue;
    @Inject
    private AbstractJdbcWorkerJobRunningRepository jdbcWorkerJobRunningRepository;
    private Runnable queueStop;

    @SuppressWarnings("unchecked")
    public JdbcWorkerTriggerResultQueueService(ApplicationContext applicationContext) {
        this.workerTriggerResultQueue = (JdbcQueue<WorkerTriggerResult>) applicationContext.getBean(
            QueueInterface.class,
            Qualifiers.byName(QueueFactoryInterface.WORKERTRIGGERRESULT_NAMED));
    }

    public Runnable receive(String consumerGroup, Class<?> queueType, Consumer<Either<WorkerTriggerResult, DeserializationException>> consumer) {
        this.queueStop = workerTriggerResultQueue.receiveTransaction(consumerGroup, queueType, (dslContext, eithers) -> {
            eithers.forEach(either -> {
                if (either.isRight()) {
                    log.error("Unable to deserialize a worker job: {}", either.getRight().getMessage());
                    try {
                        JsonNode json = MAPPER.readTree(either.getRight().getRecord());
                        var triggerContext = MAPPER.treeToValue(json.get("triggerContext"), TriggerContext.class);
                        jdbcWorkerJobRunningRepository.deleteByKey(triggerContext.uid());
                    } catch (JsonProcessingException e) {
                        // ignore the message if we cannot do anything about it
                        log.error("Unexpected exception when trying to handle a deserialization error", e);
                    }
                    return;
                }

                WorkerTriggerResult workerTriggerResult = either.getLeft();
                jdbcWorkerJobRunningRepository.deleteByKey(workerTriggerResult.getTriggerContext().uid());
            });

            eithers.forEach(consumer);
        });
        return this.queueStop;
    }

    public void pause() {
        this.stopQueue();
    }

    private void stopQueue() {
        synchronized (this) {
            if (this.queueStop != null) {
                this.queueStop.run();
                this.queueStop = null;
            }
        }
    }

    public void cleanup() { }

    public void close() {
        this.stopQueue();
    }
}
