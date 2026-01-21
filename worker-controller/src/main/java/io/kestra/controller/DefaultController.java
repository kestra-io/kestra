package io.kestra.controller;

import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.protobuf.services.HealthStatusManager;
import io.kestra.controller.config.ControllerConfiguration;
import io.kestra.controller.grpc.server.GrpcConnectControllerService;
import io.kestra.controller.grpc.server.GrpcLivenessControllerService;
import io.kestra.controller.grpc.server.GrpcWorkerControllerService;
import io.kestra.core.server.AbstractService;
import io.kestra.core.server.ServiceStateChangeEvent;
import io.kestra.core.server.ServiceType;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.TimeUnit;

/**
 * The Controller service that manages worker nodes.
 *
 * @see <a href="https://github.com/grpc/grpc-java/blob/master/examples/src/main/java/io/grpc/examples/healthservice/HealthServiceServer.java">.HealthServiceServer</a>
 */
@Singleton
@Requires(property = "kestra.server-type", pattern = "(CONTROLLER|STANDALONE)")
public class DefaultController extends AbstractService implements Controller {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultController.class);

    /**
     * Service name used for health checks.
     */
    protected static final String HEALTH_SERVICE_NAME = "kestra.controller";

    private Server server;

    protected final GrpcWorkerControllerService workerControllerService;
    protected final GrpcLivenessControllerService livenessControllerService;
    protected final GrpcConnectControllerService connectControllerService;
    protected final HealthStatusManager healthStatusManager;

    protected final ControllerConfiguration controllerConfiguration;

    @Inject
    public DefaultController(
        GrpcWorkerControllerService workerControllerService,
        GrpcLivenessControllerService livenessControllerService,
        GrpcConnectControllerService connectControllerService,
        ControllerConfiguration controllerConfiguration,
        ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher) {
        super(ServiceType.CONTROLLER, eventPublisher);
        this.workerControllerService = workerControllerService;
        this.livenessControllerService = livenessControllerService;
        this.connectControllerService = connectControllerService;
        this.controllerConfiguration = controllerConfiguration;
        this.healthStatusManager = new HealthStatusManager();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        if (getState() != ServiceState.CREATED) {
            throw new IllegalStateException("Controller is already started or stopped");
        }

        LOG.info("Starting Controller");
        int port = controllerConfiguration.port();
        try {
            server = buildServer(port);
            // Mark as serving after server starts
            healthStatusManager.setStatus(HEALTH_SERVICE_NAME, ServingStatus.SERVING);
            healthStatusManager.setStatus("", ServingStatus.SERVING);
        } catch (IOException e) {
            throw new UncheckedIOException("Error while building gRPC server", e);
        }
        LOG.info("Controller started, listening on {}", port);
        setState(ServiceState.RUNNING);
    }

    protected Server buildServer(int port) throws IOException {
        ServerBuilder<?> serverBuilder = Grpc.newServerBuilderForPort(port, InsecureServerCredentials.create())
            .addService(workerControllerService)
            .addService(livenessControllerService)
            .addService(connectControllerService)
            .addService(healthStatusManager.getHealthService());

        return serverBuilder.build().start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ServiceState doStop() throws InterruptedException {
        // Mark health service as not serving before shutdown
        LOG.info("Marking controller as NOT_SERVING");
        healthStatusManager.setStatus(HEALTH_SERVICE_NAME, ServingStatus.NOT_SERVING);
        healthStatusManager.setStatus("", ServingStatus.NOT_SERVING);

        if (server != null && !server.isTerminated()) {
            shutdownServerAndWait();
        }
        return ServiceState.TERMINATED_GRACEFULLY;
    }

    private void shutdownServerAndWait() throws InterruptedException {
        server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
    }
}
