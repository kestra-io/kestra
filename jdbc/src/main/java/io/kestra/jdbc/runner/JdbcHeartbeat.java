package io.kestra.jdbc.runner;


import io.kestra.core.runners.Worker;
import io.kestra.core.runners.WorkerInstance;
import io.kestra.jdbc.repository.AbstractJdbcWorkerInstanceRepository;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
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
    private static final String HOSTNAME;

    static {
        try {
            HOSTNAME = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    @Inject
    AbstractJdbcWorkerInstanceRepository workerInstanceRepository;

    private volatile WorkerInstance workerInstance;

    private final ApplicationContext applicationContext;

    public JdbcHeartbeat(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    private void registerWorkerInstance(Worker worker)  {
        synchronized (this) {
            if (workerInstance == null) {
                this.workerInstance = WorkerInstance.builder()
                    .workerUuid(UUID.randomUUID())
                    .hostname(HOSTNAME)
                    .port(applicationContext.getEnvironment().getProperty("micronaut.server.port", Integer.class).orElse(8080))
                    .managementPort(applicationContext.getEnvironment().getProperty("endpoints.all.port", Integer.class).orElse(8081))
                    .workerGroup(worker.getWorkerGroup())
                    .build();

                if (log.isDebugEnabled()) {
                    log.debug("Registered WorkerInstance of: {}", workerInstance.getWorkerUuid());
                }

                this.workerInstanceRepository.save(workerInstance);
            }
        }
    }

    @Scheduled(fixedDelay = "${kestra.heartbeat.frequency}")
    public void updateHeartbeat() {
        if (applicationContext.containsBean(Worker.class) && !applicationContext.getEnvironment().getActiveNames().contains(Environment.TEST)) {
            if (workerInstance == null) {
                registerWorkerInstance(applicationContext.getBean(Worker.class));
            }

            if (log.isTraceEnabled()) {
                log.trace("Heartbeat of: {}", workerInstance.getWorkerUuid());
            }

            if (workerInstanceRepository.heartbeatCheckUp(workerInstance.getWorkerUuid().toString()).isEmpty()) {
                log.error("heartbeatCheckUp failed, unable to find current instance '{}', Shutting down now!", workerInstance.getWorkerUuid());
                Runtime.getRuntime().exit(1);
            }
        }
    }

    public WorkerInstance get()  {
        if (workerInstance == null) {
            registerWorkerInstance(applicationContext.getBean(Worker.class));
        }

        return workerInstance;
    }
}