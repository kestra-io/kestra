package io.kestra.jdbc.runner;


import io.kestra.core.runners.Worker;
import io.kestra.core.runners.WorkerHeartbeat;
import io.kestra.jdbc.repository.AbstractJdbcWorkerHeartbeatRepository;
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
    AbstractJdbcWorkerHeartbeatRepository workerHeartbeatRepository;

    private WorkerHeartbeat workerHeartbeat;

    private final ApplicationContext applicationContext;


    public JdbcHeartbeat(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public void registerWorkerHeartbeat(Worker worker) throws UnknownHostException {
        this.workerHeartbeat = WorkerHeartbeat.builder()
            .workerUuid(UUID.randomUUID())
            .hostname(InetAddress.getLocalHost().getHostName())
            .port(applicationContext.getEnvironment().getProperty("micronaut.server.port", Integer.class).orElse(8080))
            .managementPort(applicationContext.getEnvironment().getProperty("endpoints.all.port", Integer.class).orElse(null))
            .workerGroup(worker.getWorkerGroup())
            .build();

        log.trace("Registered WorkerHeartbeat of: {}", workerHeartbeat.getWorkerUuid());

        workerHeartbeatRepository.save(
            workerHeartbeat
        );
    }

    @Scheduled(initialDelay = "${kestra.heartbeat.frequency}" + "s", fixedDelay = "${kestra.heartbeat.frequency}" + "s")
    public void updateHeartbeat() throws UnknownHostException {
        if (applicationContext.containsBean(Worker.class)) {
            if (workerHeartbeat == null) {
                registerWorkerHeartbeat(applicationContext.getBean(Worker.class));
            }
            log.trace("Heartbeat of: {}", workerHeartbeat.getWorkerUuid());
            if (workerHeartbeatRepository.heartbeatCheckUp(workerHeartbeat.getWorkerUuid().toString()).isEmpty()) {
                Runtime.getRuntime().exit(1);
            }
        }
    }

    public WorkerHeartbeat get() throws UnknownHostException {
        if (workerHeartbeat == null) {
            registerWorkerHeartbeat(applicationContext.getBean(Worker.class));
        }
        return workerHeartbeat;
    }

}
