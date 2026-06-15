package io.kestra.worker.systemworker;

import java.util.List;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.server.ServerConfig;
import io.kestra.core.server.ServiceStateChangeEvent;
import io.kestra.core.services.MaintenanceService;
import io.kestra.core.worker.WorkerQueues;
import io.kestra.worker.WorkerJobExecutor;
import io.kestra.worker.senders.WorkerIOSender;

import io.micronaut.context.event.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SystemWorkerTest {

    @Test
    void shouldResolveReservedSystemWorkerQueue() {
        TestableSystemWorker worker = newSystemWorker();

        assertThat(worker.resolveWorkerGroupIdForTest()).isEqualTo(WorkerQueues.SYSTEM_ID);
    }

    @SuppressWarnings("unchecked")
    private static TestableSystemWorker newSystemWorker() {
        return new TestableSystemWorker(
            mock(ApplicationEventPublisher.class),
            mock(WorkerJobExecutor.class),
            mock(DirectQueueJobFetcher.class),
            List.of(),
            mock(MaintenanceService.class),
            mock(MetricRegistry.class),
            mock(ServerConfig.class)
        );
    }

    /** Test seam that exposes the protected resolveWorkerGroup hook. */
    private static class TestableSystemWorker extends SystemWorker {

        TestableSystemWorker(
            ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher,
            WorkerJobExecutor workerJobExecutor,
            DirectQueueJobFetcher directQueueJobFetcher,
            List<DirectQueueWorkerIOSender<?>> workerIOSenders,
            MaintenanceService maintenanceService,
            MetricRegistry metricRegistry,
            ServerConfig serverConfig
        ) {
            super(eventPublisher, workerJobExecutor, directQueueJobFetcher,
                workerIOSenders, maintenanceService, metricRegistry, serverConfig);
        }

        String resolveWorkerGroupIdForTest() {
            return resolveWorkerGroupId();
        }
    }
}
