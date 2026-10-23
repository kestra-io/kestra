package io.kestra.webserver.controllers.api;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
import io.kestra.webserver.services.BasicAuthService;
import io.kestra.webserver.tenants.TenantValidationFilter;

import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@KestraTest
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
    private EmbeddedServer server;

    @Inject
    private BasicAuthService basicAuthService;

    @Inject
    private BasicAuthService.BasicAuthConfiguration basicAuthConfiguration;

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
    void shouldNotReadTheLogsFasterThanTheClient() throws Exception {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = seedExecution(tenant);
        AtomicInteger emitted = new AtomicInteger();
        when(logRepository.findAsync(eq(tenant), anyList())).thenReturn(pushingLogs(tenant, executionId, emitted, new AtomicBoolean()));

        HttpURLConnection connection = download(tenant, executionId);
        try (InputStream body = connection.getInputStream()) {
            body.readNBytes(MESSAGE_SIZE);
            // the client stops reading: once the socket buffers are full, the logs must stop being read too
            Thread.sleep(2_000);
            assertThat(emitted.get()).isLessThan(LOGS / 2);

            String rest = new String(body.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(rest).endsWith("line " + (LOGS - 1) + " " + "x".repeat(MESSAGE_SIZE));
        }
        assertThat(emitted.get()).isEqualTo(LOGS);
    }

    @Test
    void shouldReleaseTheReaderWhenTheClientGoesAway() throws Exception {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = seedExecution(tenant);
        AtomicBoolean terminated = new AtomicBoolean();
        when(logRepository.findAsync(eq(tenant), anyList())).thenReturn(pushingLogs(tenant, executionId, new AtomicInteger(), terminated));

        HttpURLConnection connection = download(tenant, executionId);
        try (InputStream body = connection.getInputStream()) {
            body.readNBytes(MESSAGE_SIZE);
        }
        connection.disconnect();

        await().atMost(Duration.ofSeconds(10)).untilTrue(terminated);
    }

    @Test
    void shouldAnswerAnErrorWhenTheLogsCannotBeRead() throws Exception {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = seedExecution(tenant);
        when(logRepository.findAsync(eq(tenant), anyList())).thenReturn(Flux.error(new IllegalStateException("database unavailable")));

        HttpURLConnection connection = open(tenant, executionId);

        assertThat(connection.getResponseCode()).isEqualTo(500);
    }

    private String seedExecution(String tenant) {
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
        return executionId;
    }

    // like the JDBC findAsync: every log is pushed as soon as it is read, whatever was requested
    private static Flux<LogEntry> pushingLogs(String tenant, String executionId, AtomicInteger emitted, AtomicBoolean terminated) {
        String filler = "x".repeat(MESSAGE_SIZE);
        return Flux.<LogEntry> create(
            sink ->
            {
                for (int index = 0; index < LOGS; index++) {
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
                    emitted.incrementAndGet();
                }
                sink.complete();
            },
            FluxSink.OverflowStrategy.BUFFER
        )
            .doFinally(signal -> terminated.set(true));
    }

    private HttpURLConnection download(String tenant, String executionId) throws Exception {
        HttpURLConnection connection = open(tenant, executionId);
        assertThat(connection.getResponseCode()).isEqualTo(200);
        return connection;
    }

    // a plain connection rather than the test client, so that the body is read only as fast as the test asks
    private HttpURLConnection open(String tenant, String executionId) throws Exception {
        if (basicAuthService.credentials() == null) {
            basicAuthService.init();
        }
        HttpURLConnection connection = (HttpURLConnection) server.getURI()
            .resolve("/api/v1/" + tenant + "/logs/" + executionId + "/download")
            .toURL()
            .openConnection();
        connection.setReadTimeout(30_000);
        String credentials = basicAuthConfiguration.getUsername() + ":" + basicAuthConfiguration.getPassword();
        connection.setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        return connection;
    }
}
