package io.kestra.worker.services;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.controller.config.WorkerControllersConfiguration;
import io.kestra.controller.grpc.ConnectControllerServiceGrpc.ConnectControllerServiceBlockingStub;
import io.kestra.controller.grpc.ConnectRequest;
import io.kestra.controller.grpc.ConnectResponse;
import io.kestra.controller.messages.MessageFormats;
import io.kestra.controller.messages.RequestOrResponseHeaderFactory;
import io.kestra.core.encryption.EncryptionConfig;
import io.kestra.core.reporter.UsageReportConfig;
import io.kestra.core.serializers.JacksonMapper;

import io.grpc.Deadline;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * gRPC-based implementation of {@link WorkerConnectionService}.
 * <p>
 * This service communicates with the controller via gRPC to establish the initial
 * worker connection and resolve group subscriptions from the worker group.
 */
@Singleton
@Slf4j
public class GrpcWorkerConnectionService implements WorkerConnectionService {

    private static final ObjectMapper OBJECT_MAPPER = JacksonMapper.ofJson(false);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ConnectControllerServiceBlockingStub connectControllerService;
    private final WorkerControllersConfiguration workerControllersConfiguration;
    @Nullable
    private final WorkerReportableScheduler workerReportableScheduler;
    private final EncryptionConfig encryptionConfig;

    @Inject
    public GrpcWorkerConnectionService(ConnectControllerServiceBlockingStub connectControllerService,
        WorkerControllersConfiguration workerControllersConfiguration,
        @Nullable WorkerReportableScheduler workerReportableScheduler,
        EncryptionConfig encryptionConfig) {
        this.connectControllerService = connectControllerService;
        this.workerControllersConfiguration = workerControllersConfiguration;
        this.workerReportableScheduler = workerReportableScheduler;
        this.encryptionConfig = encryptionConfig;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConnectionResult connect(String workerId) {
        log.info("Connecting to controller");

        ConnectRequest request = ConnectRequest.newBuilder()
            .setHeader(RequestOrResponseHeaderFactory.create(workerId))
            .build();

        try {
            log.debug("Sending connect request to controller for workerId: {}", workerId);
            // Apply deadline per-call: Deadline.after() creates an absolute timestamp, so it must not be baked into a singleton stub.
            ConnectControllerServiceBlockingStub stub = connectControllerService;
            if (workerControllersConfiguration.waitForReady().enabled()) {
                long deadlineMs = workerControllersConfiguration.waitForReady().deadline().toMillis();
                stub = stub.withDeadline(Deadline.after(deadlineMs, TimeUnit.MILLISECONDS));
            }
            ConnectResponse response = stub.connect(request);
            processConnectResponse(response);
            String workerGroupId = response.getWorkerGroupId();
            log.info("Connected to controller, workerGroup: {}", workerGroupId);
            return new ConnectionResult(workerGroupId);
        } catch (Exception e) {
            log.error("Failed to send connect request to controller", e);
            throw new WorkerConnectionFailedException("Failed connecting to Kestra controller. Cause: " + e.getMessage());
        }
    }

    /**
     * Processes the connect response from the controller.
     * <p>
     * Subclasses can override this to extract additional fields from the response.
     *
     * @param response the gRPC connect response
     */
    protected void processConnectResponse(ConnectResponse response) {
        // Extract telemetry configuration from serialized worker configs
        if (workerReportableScheduler != null && !response.getWorkerConfigs().isEmpty()) {
            Map<String, Object> configs = MessageFormats.JSON.fromByteString(response.getWorkerConfigs(), MAP_TYPE);
            if (configs != null) {
                // Extract telemetry configuration
                if (workerReportableScheduler != null && configs.containsKey(UsageReportConfig.ANONYMOUS_USAGE_REPORT)) {
                    UsageReportConfig config = OBJECT_MAPPER.convertValue(
                        configs.get(UsageReportConfig.ANONYMOUS_USAGE_REPORT), UsageReportConfig.class
                    );
                    workerReportableScheduler.init(config);
                    log.debug("Worker usage reporting is {}", config.enabled() ? "enabled" : "disabled");
                }

                // Extract encryption key from controller
                if (configs.containsKey(EncryptionConfig.WORKER_CONFIG_KEY)) {
                    String key = (String) configs.get(EncryptionConfig.WORKER_CONFIG_KEY);
                    encryptionConfig.initialize(key);
                    log.debug("Encryption key received from controller");
                }
            }
        }
    }
}
