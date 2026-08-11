package io.kestra.controller.discovery;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.controller.config.ControllerAdvertiseConfiguration;
import io.kestra.controller.config.ControllerConfiguration;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.server.ServerConfig;
import io.kestra.core.server.ServerInstance;
import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.ServiceType;
import io.kestra.core.server.WorkerTaskRestartStrategy;
import io.kestra.core.storages.StorageInterface;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ControllerStorageRegistrarTest {

    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();
    private static final int GRPC_PORT = 50051;

    private StorageInterface storage;

    @BeforeEach
    void setUp() {
        storage = mock(StorageInterface.class);
    }

    @Test
    void shouldRegisterOnFirstRunningLivenessUpdate() throws Exception {
        // Given
        ControllerStorageRegistrar registrar = newRegistrar("controller-1.example.com", Duration.ofMinutes(5));
        ServiceInstance instance = controllerInstance();

        // When
        registrar.onLivenessUpdate(Instant.now(), instance, Service.ServiceState.RUNNING);

        // Then
        ControllerRegistration written = captureWritten();
        assertThat(written.id()).isEqualTo(instance.uid());
        assertThat(written.host()).isEqualTo("controller-1.example.com");
        assertThat(written.port()).isEqualTo(GRPC_PORT);
        assertThat(written.expiresAt()).isAfter(written.heartbeatAt());

        registrar.close();
    }

    @Test
    void shouldIgnoreNonControllerServices() throws Exception {
        // Given
        ControllerStorageRegistrar registrar = newRegistrar("host", Duration.ofMinutes(5));

        // When — a worker instance in RUNNING state
        ServiceInstance workerInstance = instanceFor(ServiceType.WORKER);
        registrar.onLivenessUpdate(Instant.now(), workerInstance, Service.ServiceState.RUNNING);

        // Then
        verify(storage, never()).putInstanceResource(any(), any(), any(InputStream.class));

        registrar.close();
    }

    @Test
    void shouldIgnoreNonRunningStates() throws Exception {
        // Given
        ControllerStorageRegistrar registrar = newRegistrar("host", Duration.ofMinutes(5));
        ServiceInstance instance = controllerInstance();

        // When
        registrar.onLivenessUpdate(Instant.now(), instance, Service.ServiceState.CREATED);
        registrar.onLivenessUpdate(Instant.now(), instance, Service.ServiceState.TERMINATING);

        // Then
        verify(storage, never()).putInstanceResource(any(), any(), any(InputStream.class));

        registrar.close();
    }

    @Test
    void shouldThrottleWritesWithinHeartbeatInterval() throws Exception {
        // Given — 1-minute advertise interval
        ControllerStorageRegistrar registrar = newRegistrar("host", Duration.ofMinutes(1));
        ServiceInstance instance = controllerInstance();

        Instant t0 = Instant.parse("2026-04-15T10:00:00Z");

        // When — first call writes, second call within interval does not
        registrar.onLivenessUpdate(t0, instance, Service.ServiceState.RUNNING);
        registrar.onLivenessUpdate(t0.plusSeconds(5), instance, Service.ServiceState.RUNNING);
        registrar.onLivenessUpdate(t0.plusSeconds(30), instance, Service.ServiceState.RUNNING);

        // Then
        verify(storage, times(1)).putInstanceResource(isNull(), any(URI.class), any(InputStream.class));

        registrar.close();
    }

    @Test
    void shouldWriteAgainAfterHeartbeatInterval() throws Exception {
        // Given
        ControllerStorageRegistrar registrar = newRegistrar("host", Duration.ofMinutes(1));
        ServiceInstance instance = controllerInstance();

        Instant t0 = Instant.parse("2026-04-15T10:00:00Z");

        // When — calls spaced by exactly the heartbeat interval
        registrar.onLivenessUpdate(t0, instance, Service.ServiceState.RUNNING);
        registrar.onLivenessUpdate(t0.plus(Duration.ofMinutes(1)), instance, Service.ServiceState.RUNNING);
        registrar.onLivenessUpdate(t0.plus(Duration.ofMinutes(2)), instance, Service.ServiceState.RUNNING);

        // Then
        verify(storage, times(3)).putInstanceResource(isNull(), any(URI.class), any(InputStream.class));

        registrar.close();
    }

    @Test
    void shouldRetryOnNextTickAfterIoException() throws Exception {
        // Given
        ControllerStorageRegistrar registrar = newRegistrar("host", Duration.ofMinutes(1));
        ServiceInstance instance = controllerInstance();

        doThrow(new IOException("boom"))
            .doReturn(URI.create("/controllers/registry/" + instance.uid() + ".json"))
            .when(storage).putInstanceResource(isNull(), any(URI.class), any(InputStream.class));

        Instant t0 = Instant.parse("2026-04-15T10:00:00Z");

        // When — first call fails, second call (still within "interval") should retry
        // because lastWrittenAt was not advanced on failure.
        registrar.onLivenessUpdate(t0, instance, Service.ServiceState.RUNNING);
        registrar.onLivenessUpdate(t0.plusSeconds(1), instance, Service.ServiceState.RUNNING);

        // Then
        verify(storage, times(2)).putInstanceResource(isNull(), any(URI.class), any(InputStream.class));

        registrar.close();
    }

    @Test
    void shouldDeleteEntryOnClose() throws Exception {
        // Given
        ControllerStorageRegistrar registrar = newRegistrar("host", Duration.ofMinutes(5));
        ServiceInstance instance = controllerInstance();
        registrar.onLivenessUpdate(Instant.now(), instance, Service.ServiceState.RUNNING);

        // When
        registrar.close();

        // Then
        verify(storage, times(1)).deleteInstanceResource(isNull(), any(URI.class));
    }

    @Test
    void shouldSkipDeleteWhenNeverRegistered() throws Exception {
        // Given — no RUNNING update ever fired
        ControllerStorageRegistrar registrar = newRegistrar("host", Duration.ofMinutes(5));

        // When
        registrar.close();

        // Then
        verify(storage, never()).deleteInstanceResource(any(), any());
    }

    private ControllerStorageRegistrar newRegistrar(String host, Duration heartbeatInterval) {
        ControllerAdvertiseConfiguration advertise = new ControllerAdvertiseConfiguration(
            true,
            host,
            heartbeatInterval
        );
        ControllerConfiguration controllerConfig = new ControllerConfiguration(
            GRPC_PORT,
            Duration.ZERO,
            Duration.ZERO
        );
        return new ControllerStorageRegistrar(() -> storage, advertise, controllerConfig);
    }

    private ControllerRegistration captureWritten() throws IOException {
        ArgumentCaptor<InputStream> payloadCaptor = ArgumentCaptor.forClass(InputStream.class);
        verify(storage).putInstanceResource(isNull(), any(URI.class), payloadCaptor.capture());
        return MAPPER.readValue(payloadCaptor.getValue(), ControllerRegistration.class);
    }

    private ServiceInstance controllerInstance() {
        return instanceFor(ServiceType.CONTROLLER);
    }

    private ServiceInstance instanceFor(ServiceType type) {
        ServerConfig config = new ServerConfig(
            Duration.ZERO,
            WorkerTaskRestartStrategy.AFTER_TERMINATION_GRACE_PERIOD,
            new ServerConfig.Liveness(
                true,
                Duration.ZERO,
                Duration.ofSeconds(10),
                Duration.ofSeconds(5),
                Duration.ofSeconds(5)
            ),
            null,
            null,
            null
        );
        return new ServiceInstance(
            UUID.randomUUID().toString(),
            type,
            Service.ServiceState.RUNNING,
            new ServerInstance(
                ServerInstance.Type.SERVER,
                "N/A",
                "localhost",
                Map.of(),
                Set.of()
            ),
            Instant.now(),
            Instant.now(),
            List.of(),
            config,
            Map.of(),
            Set.of()
        );
    }
}
