package io.kestra.runner.kafka;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.templates.Template;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.runners.*;
import io.kestra.runner.kafka.executors.KafkaExecutorInterface;
import io.kestra.runner.kafka.services.KafkaAdminService;
import io.kestra.runner.kafka.services.KafkaStreamService;
import io.kestra.runner.kafka.services.KafkaStreamSourceService;
import io.kestra.runner.kafka.streams.ExecutorFlowTrigger;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.Topology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

@KafkaQueueEnabled
@Singleton
@Slf4j
public class KafkaExecutor implements ExecutorInterface {
    private List<KafkaStreamService.Stream> streams;

    @Inject
    protected ApplicationContext applicationContext;

    @Inject
    protected KafkaStreamService kafkaStreamService;

    @Inject
    protected KafkaAdminService kafkaAdminService;

    @Inject
    protected List<KafkaExecutorInterface> kafkaExecutors;

    @Inject
    protected ExecutorService executorService;

    @Override
    public void run() {
        kafkaAdminService.createIfNotExist(WorkerTask.class);
        kafkaAdminService.createIfNotExist(WorkerTaskResult.class);
        kafkaAdminService.createIfNotExist(Execution.class);
        kafkaAdminService.createIfNotExist(Flow.class);
        kafkaAdminService.createIfNotExist(KafkaStreamSourceService.TOPIC_FLOWLAST);
        kafkaAdminService.createIfNotExist(Executor.class);
        kafkaAdminService.createIfNotExist(KafkaStreamSourceService.TOPIC_EXECUTOR_WORKERINSTANCE);
        kafkaAdminService.createIfNotExist(ExecutionKilled.class);
        kafkaAdminService.createIfNotExist(WorkerTaskExecution.class);
        kafkaAdminService.createIfNotExist(WorkerTaskRunning.class);
        kafkaAdminService.createIfNotExist(WorkerInstance.class);
        kafkaAdminService.createIfNotExist(Template.class);
        kafkaAdminService.createIfNotExist(LogEntry.class);
        kafkaAdminService.createIfNotExist(Trigger.class);
        kafkaAdminService.createIfNotExist(ExecutorFlowTrigger.class);

        this.streams = this.kafkaExecutors
            .stream()
            .parallel()
            .map(executor -> {
                Properties properties = new Properties();
                // build
                Topology topology = executor.topology().build();

                Logger logger = LoggerFactory.getLogger(executor.getClass());
                KafkaStreamService.Stream stream = kafkaStreamService.of(executor.getClass(), executor.getClass(), topology, properties, logger);
                stream.start();

                executor.onCreated(applicationContext, stream);

                applicationContext.inject(stream);

                return stream;
            })
            .collect(Collectors.toList());
    }

    @Override
    public void close() throws IOException {
        if (streams != null) {
            streams
                .parallelStream()
                .forEach(stream -> stream.close(Duration.ofSeconds(10)));
        }
    }
}
