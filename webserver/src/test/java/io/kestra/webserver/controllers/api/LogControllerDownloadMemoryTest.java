package io.kestra.webserver.controllers.api;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.LogDataStoreInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.webserver.tenants.TenantValidationFilter;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;
import reactor.core.publisher.Flux;

import static io.micronaut.http.HttpRequest.GET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@KestraTest
@Property(name = "micronaut.http.client.max-content-length", value = "209715200")
class LogControllerDownloadMemoryTest {
    private static final int LOGS = 1_000;
    private static final int MESSAGE_SIZE = 100_000;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private LogDataStoreInterface logRepository;

    @Inject
    private TenantService tenantService;

    @Inject
    @Client("/")
    private ReactorHttpClient client;

    @MockBean(LogDataStoreInterface.class)
    public LogDataStoreInterface getLogRepository() {
        return mock(LogDataStoreInterface.class);
    }

    @MockBean(TenantService.class)
    public TenantService getTenantService() {
        return mock(TenantService.class);
    }

    @MockBean(TenantValidationFilter.class)
    public TenantValidationFilter getTenantValidationFilter() {
        return mock(TenantValidationFilter.class);
    }

    @Test
    void shouldNotHoldTheLogsInMemoryWhileReadingThem() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        when(tenantService.resolveTenant()).thenReturn(tenant);
        String executionId = IdUtils.create();
        executionRepository.save(
            Execution.builder()
                .id(executionId)
                .namespace("io.kestra.unittest")
                .tenantId(tenant)
                .flowId("full")
                .flowRevision(1)
                .state(new State().withState(State.Type.RUNNING))
                .build()
        );
        String filler = "x".repeat(MESSAGE_SIZE);
        AtomicLong heapBeforeTheFirstLog = new AtomicLong();
        AtomicLong heapAfterTheLastLog = new AtomicLong();
        Flux<LogEntry> logs = Flux.generate(
            () -> 0, (index, sink) ->
            {
                if (index == 0) {
                    heapBeforeTheFirstLog.set(retainedHeap());
                }
                if (index == LOGS) {
                    heapAfterTheLastLog.set(retainedHeap());
                    sink.complete();
                    return index;
                }
                sink.next(
                    LogEntry.builder()
                        .tenantId(tenant)
                        .namespace("io.kestra.unittest")
                        .flowId("full")
                        .executionId(executionId)
                        .timestamp(Instant.now())
                        .level(Level.INFO)
                        .message("line " + index + " " + filler)
                        .build()
                );
                return index + 1;
            }
        );
        when(logRepository.findAsync(eq(tenant), anyList())).thenReturn(logs);

        String body = client.toBlocking().retrieve(GET("/api/v1/" + tenant + "/logs/" + executionId + "/download"), String.class);

        assertThat(body).hasSizeGreaterThan(LOGS * MESSAGE_SIZE).contains("line 999 ");
        long heapHeldByTheDownload = heapAfterTheLastLog.get() - heapBeforeTheFirstLog.get();
        assertThat(heapHeldByTheDownload).isLessThan(LOGS * MESSAGE_SIZE / 4);
    }

    // the heap a download keeps is only visible once the garbage has been collected
    private static long retainedHeap() {
        System.gc();
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }
}
