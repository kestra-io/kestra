package io.kestra.controller.discovery;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import io.kestra.controller.config.ControllerAdvertiseConfiguration;
import io.kestra.controller.config.ControllerConfiguration;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.ServiceLivenessListener;
import io.kestra.core.server.ServiceType;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.Network;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Requires;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;


/**
 * Publishes the controller's gRPC endpoint to internal storage on every successful
 * liveness update, so that workers configured with
 * {@code kestra.worker.controllers.type=STORAGE} can discover it dynamically.
 * <p>
 * Hooks into {@link io.kestra.core.server.ServiceLivenessManager} as a
 * {@link ServiceLivenessListener} rather than running its own scheduler: the
 * liveness heartbeat becomes the single source of truth driving storage refreshes,
 * so a storage entry cannot outlive the controller's liveness state.
 * <p>
 * Storage writes are throttled to
 * {@link ControllerAdvertiseConfiguration#heartbeatInterval()} because the liveness
 * tick fires at a much higher cadence (~seconds) than is desirable for object
 * storage round-trips (~minutes).
 */
@Singleton
@Slf4j
@Requires(property = "kestra.server-type", pattern = "(CONTROLLER|STANDALONE)")
@Requires(property = "kestra.controller.advertise.enabled", value = "true", defaultValue = "false")
public class ControllerStorageRegistrar implements ServiceLivenessListener, Closeable {

    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    private final Provider<StorageInterface> storageInterface;
    private final ControllerAdvertiseConfiguration advertiseConfig;
    private final ControllerConfiguration controllerConfig;

    private volatile String registrationId;
    private volatile Instant lastWrittenAt;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private final String resolvedHost;

    @Inject
    public ControllerStorageRegistrar(final Provider<StorageInterface> storageInterface,
                                      final ControllerAdvertiseConfiguration advertiseConfig,
                                      final ControllerConfiguration controllerConfig) {
        this.storageInterface = Objects.requireNonNull(storageInterface, "StorageInterface cannot be null");
        this.advertiseConfig = Objects.requireNonNull(advertiseConfig, "ControllerAdvertiseConfiguration cannot be null");
        this.controllerConfig = Objects.requireNonNull(controllerConfig, "ControllerConfiguration cannot be null");

        String host = advertiseConfig.host();
        this.resolvedHost =  host != null && !host.isBlank() ? host : Network.localHostname();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLivenessUpdate(final Instant now,
        final ServiceInstance instance,
        final Service.ServiceState newState) {
        if (closed.get() ||
            instance.type() != ServiceType.CONTROLLER ||
            newState != Service.ServiceState.RUNNING ||
            !isDueForRefresh(now)) {
            return;
        }

        final String id = instance.uid();
        try {
            writeEntry(now, id);
        } catch (IOException e) {
            // Do NOT advance lastWrittenAt — next liveness tick will retry.
            log.warn("Failed to refresh controller [{}] registration in internal storage", id, e);
            return;
        }

        if (lastWrittenAt == null) {
            log.info("Controller [{}] registered in internal storage at [{}]; heartbeat every {}",
                id, resolvedHost, advertiseConfig.heartbeatInterval());
        }
        this.registrationId = id;
        this.lastWrittenAt = now;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @PreDestroy
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (registrationId == null) {
            return;
        }
        try {
            storageInterface.get().deleteInstanceResource(null, ControllerRegistry.entryUri(registrationId));
            log.info("Controller [{}] deregistered from internal storage", registrationId);
        } catch (IOException e) {
            log.warn("Failed to deregister controller [{}] from internal storage", registrationId, e);
        }
    }

    private boolean isDueForRefresh(final Instant now) {
        if (lastWrittenAt == null) {
            return true;
        }
        return !now.isBefore(lastWrittenAt.plus(advertiseConfig.heartbeatInterval()));
    }

    private void writeEntry(final Instant now, final String id) throws IOException {
        final URI uri = ControllerRegistry.entryUri(id);
        final ControllerRegistration registration = new ControllerRegistration(
            id,
            resolvedHost,
            controllerConfig.port(),
            now,
            now.plus(advertiseConfig.ttl())
        );
        final byte[] payload = MAPPER.writeValueAsBytes(registration);
        storageInterface.get().putInstanceResource(null, uri, new ByteArrayInputStream(payload));
    }
}
