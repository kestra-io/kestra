package io.kestra.jdbc.runner;


import io.kestra.core.runners.Worker;
import io.kestra.core.runners.WorkerInstance;
import io.kestra.jdbc.repository.AbstractJdbcWorkerInstanceRepository;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

@Singleton
@JdbcRunnerEnabled
@Requires(property = "kestra.server-type", pattern = "(WORKER|STANDALONE)")
@Slf4j
public class JdbcHeartbeat {
    @Inject
    AbstractJdbcWorkerInstanceRepository workerInstanceRepository;

    private WorkerInstance workerInstance;

    private final ApplicationContext applicationContext;


    public JdbcHeartbeat(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public void registerWorkerInstance(Worker worker) throws UnknownHostException {
        this.workerInstance = WorkerInstance.builder()
            .workerUuid(UUID.randomUUID())
            .hostname(InetAddress.getLocalHost().getHostName())
            .port(applicationContext.getEnvironment().getProperty("micronaut.server.port", Integer.class).orElse(8080))
            .managementPort(applicationContext.getEnvironment().getProperty("endpoints.all.port", Integer.class).orElse(null))
            .workerGroup(worker.getWorkerGroup())
            .build();

        log.trace("Registered WorkerInstance of: {}", workerInstance.getWorkerUuid());

        this.workerInstanceRepository.save(
            workerInstance
        );
    }

    @Scheduled(fixedDelay = "${kestra.heartbeat.frequency}")
    public void updateHeartbeat() throws UnknownHostException {
        if (applicationContext.containsBean(Worker.class)) {
            if (workerInstance == null) {
                registerWorkerInstance(applicationContext.getBean(Worker.class));
            }
            log.trace("Heartbeat of: {}", workerInstance.getWorkerUuid());
            if (workerInstanceRepository.heartbeatCheckUp(workerInstance.getWorkerUuid().toString()).isEmpty()) {
                Runtime.getRuntime().exit(1);
            }
        }
    }

    public WorkerInstance get() throws UnknownHostException {
        if (workerInstance == null) {
            registerWorkerInstance(applicationContext.getBean(Worker.class));
        }
        return workerInstance;
    }

}
