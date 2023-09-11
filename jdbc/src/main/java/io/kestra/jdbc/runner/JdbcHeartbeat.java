package io.kestra.jdbc.runner;


import io.kestra.core.runners.WorkerHeartbeat;
import io.kestra.jdbc.repository.AbstractJdbcWorkerHeartbeatRepository;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
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

    private final WorkerHeartbeat workerHeartbeat;


    public JdbcHeartbeat(ApplicationContext applicationContext) throws UnknownHostException {
//        Worker worker = applicationContext.getBean(Worker.class);
        this.workerHeartbeat = WorkerHeartbeat.builder()
            .workerUuid(UUID.randomUUID())
            .hostname(InetAddress.getLocalHost().getHostName())
            .port(applicationContext.getEnvironment().getProperty("micronaut.server.port", Integer.class).orElse(8080))
            .managementPort(applicationContext.getEnvironment().getProperty("endpoints.all.port", Integer.class).orElse(null))
            .build();
    }

    @PostConstruct
    public void initializedWorkerHeartbeat() {
        log.trace("Initialized heartbeat of: {}", workerHeartbeat.getWorkerUuid());

        workerHeartbeatRepository.save(
            workerHeartbeat
        );
    }

    @Scheduled(fixedDelay = "${kestra.heartbeat.frequency}" + "s")
    public void updateHeartbeat() {
        log.trace("Heartbeat of: {}", workerHeartbeat.getWorkerUuid());
        if (workerHeartbeatRepository.heartbeatCheckUp(workerHeartbeat.getWorkerUuid().toString()).isEmpty()) {
            Runtime.getRuntime().exit(1);
        }
    }
}
