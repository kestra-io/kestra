package io.kestra.controller;

import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.kestra.controller.config.GrpcChannelConfiguration;
import io.kestra.core.contexts.KestraContext;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
@Slf4j
public class GrpcChannelManager {

    private volatile ManagedChannel defaultChannel;
    private volatile ExecutorService defaultExecutorService;

    private final AtomicBoolean stopped = new AtomicBoolean(false);

    private final GrpcChannelConfiguration config;

    /**
     * Creates a new {@link GrpcChannelManager} instance.
     *
     * @param config the gRPC channel configuration
     */
    @Inject
    public GrpcChannelManager(GrpcChannelConfiguration config) {
        this.config = config;
    }

    /**
     * Return a shared gRPC Channel.
     * <p>
     * This method will create the channel if necessary.
     *
     * @return the {@link Channel}
     */
    public Channel createOrGetDefault() {
        if (this.defaultChannel == null) {
            synchronized (this) {
                if (this.defaultChannel == null) {
                    defaultExecutorService = Executors.newSingleThreadExecutor();
                    defaultChannel = createNewManagedChannel();
                }
            }
        }
        return defaultChannel;
    }

    /**
     * Create a new gRPC Channel.
     *
     * @return the {@link ManagedChannel}
     */
    public ManagedChannel createNewManagedChannel() {
        return ManagedChannelBuilder.forAddress(config.host(), config.port())
            .usePlaintext()
            .enableRetry()
            .maxRetryAttempts(config.maxRetryAttempts())
            .userAgent(getUserAgent())
            .keepAliveTime(config.keepAliveTime().toSeconds(), TimeUnit.SECONDS)
            .keepAliveWithoutCalls(true)
            .executor(defaultExecutorService)
            .build();
    }

    @PreDestroy
    public void close() {
        if (!stopped.compareAndSet(false, true)) {
            return; // Method called twice
        }

        if (this.defaultChannel != null && !this.defaultChannel.isShutdown()) {
            try {
                shutdownServerAndWait();
            } catch (Exception e) {
                log.debug("Error while stopping default gRPC channel", e);
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
            this.defaultExecutorService.shutdownNow();
        }
    }

    private void shutdownServerAndWait() throws InterruptedException {
        this.defaultChannel.shutdown().awaitTermination(30, TimeUnit.SECONDS);
    }

    protected static String getUserAgent() {
        return "Kestra/" + KestraContext.getContext().getVersion();
    }
}
