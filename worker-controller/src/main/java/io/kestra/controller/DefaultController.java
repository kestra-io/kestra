package io.kestra.controller;

import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.protobuf.services.HealthStatusManager;
import io.kestra.controller.config.ControllerConfiguration;
import io.kestra.controller.grpc.WorkerControllerService;
import io.kestra.core.server.AbstractService;
import io.kestra.core.server.ServiceStateChangeEvent;
import io.kestra.core.server.ServiceType;
import io.kestra.core.worker.Controller;
import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The Controller service that manages worker nodes.
 *
 * @see <a href="https://github.com/grpc/grpc-java/blob/master/examples/src/main/java/io/grpc/examples/healthservice/HealthServiceServer.java">.HealthServiceServer</a>
 */
@Singleton
public class DefaultController extends AbstractService implements Controller {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultController.class);

    /**
     * Service name used for health checks.
     */
    protected static final String HEALTH_SERVICE_NAME = "kestra.controller";

    private Server server;

    private final List<WorkerControllerService> workerControllerServices;

    protected final HealthStatusManager healthStatusManager;

    protected final ControllerConfiguration controllerConfiguration;

    @Inject
    public DefaultController(
        List<WorkerControllerService> workerControllerServices,
        ControllerConfiguration controllerConfiguration,
        ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher) {
        super(ServiceType.CONTROLLER, eventPublisher);
        this.workerControllerServices = workerControllerServices;
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
            server = buildServer(port).build().start();
            // Mark as serving after server starts
            healthStatusManager.setStatus(HEALTH_SERVICE_NAME, ServingStatus.SERVING);
            healthStatusManager.setStatus("", ServingStatus.SERVING);
        } catch (IOException e) {
            throw new UncheckedIOException("Error while building gRPC server", e);
        }
        LOG.info("Controller started, listening on {}", port);
        setState(ServiceState.RUNNING);
    }

    protected ServerBuilder<?> buildServer(int port) {
        ServerBuilder<?> serverBuilder = Grpc.newServerBuilderForPort(port, InsecureServerCredentials.create())
            .addService(healthStatusManager.getHealthService());

        // Configure maxConnectionAge for load balancing across multiple controllers
        // This forces workers to periodically reconnect, redistributing them across available controllers
        if (controllerConfiguration.maxConnectionAge() != null && !controllerConfiguration.maxConnectionAge().isZero()) {
            long maxAgeMillis = controllerConfiguration.maxConnectionAge().toMillis();
            serverBuilder
                .maxConnectionAge(maxAgeMillis, TimeUnit.MILLISECONDS)
                // Grace period allows in-flight RPCs to complete before forcing disconnect
                .maxConnectionAgeGrace(controllerConfiguration.maxConnectionAgeGrace().toMillis(), TimeUnit.MILLISECONDS);
            LOG.info("Controller configured with maxConnectionAge={}ms", maxAgeMillis);
        }

        for (WorkerControllerService service : workerControllerServices) {
            serverBuilder = serverBuilder.addService(service);
        }
        return serverBuilder;
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

        // Close all worker controller services to complete active worker streams,
        // so gRPC has no more open RPCs to wait for.
        for (WorkerControllerService service : workerControllerServices) {
            try {
                service.close();
            } catch (Exception e) {
                LOG.warn("Error closing worker controller service: {}", e.getMessage());
            }
        }

        if (server != null && !server.isTerminated()) {
            shutdownServerAndWait();
        }
        return ServiceState.TERMINATED_GRACEFULLY;
    }

    private void shutdownServerAndWait() throws InterruptedException {
        server.shutdown();
        if (!server.awaitTermination(controllerConfiguration.maxConnectionAgeGrace().toMillis(), TimeUnit.MILLISECONDS)) {
            LOG.warn("gRPC server did not terminate within grace period, forcing shutdown");
            server.shutdownNow();
        }
    }
}
