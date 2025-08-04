package io.kestra.controller;

import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.kestra.controller.config.GrpcChannelConfiguration;
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
 */
@Singleton
@Requires(property = "kestra.server-type", pattern = "(CONTROLLER|STANDALONE)")
public class DefaultController extends AbstractService implements Controller {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultController.class);

    private Server server;

    protected final GrpcWorkerControllerService workerControllerService;
    protected final GrpcLivenessControllerService livenessControllerService;
    protected final GrpcConnectControllerService connectControllerService;
    private final GrpcChannelConfiguration grpcChannelConfiguration;

    @Inject
    public DefaultController(
        GrpcWorkerControllerService workerControllerService,
        GrpcLivenessControllerService livenessControllerService,
        GrpcConnectControllerService connectControllerService,
        GrpcChannelConfiguration grpcChannelConfiguration,
        ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher) {
        super(ServiceType.CONTROLLER, eventPublisher);
        this.workerControllerService = workerControllerService;
        this.livenessControllerService = livenessControllerService;
        this.connectControllerService = connectControllerService;
        this.grpcChannelConfiguration = grpcChannelConfiguration;
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
        /* The port on which the server should run */
        int port = grpcChannelConfiguration.port();
        try {
            server = buildServer(port);
        } catch (IOException e) {
            throw new UncheckedIOException("Error while building gRPC server", e);
        }
        LOG.info("Controller started, listening on {}", port);
        setState(ServiceState.RUNNING);
    }

    protected Server buildServer(int port) throws IOException {
        return Grpc.newServerBuilderForPort(port, InsecureServerCredentials.create())
            .addService(workerControllerService)
            .addService(livenessControllerService)
            .addService(connectControllerService)
            .build()
            .start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ServiceState doStop() throws InterruptedException {
        if (server != null && !server.isTerminated()) {
            shutdownServerAndWait();
        }
        return ServiceState.TERMINATED_GRACEFULLY;
    }

    private void shutdownServerAndWait() throws InterruptedException {
        server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
    }
}
