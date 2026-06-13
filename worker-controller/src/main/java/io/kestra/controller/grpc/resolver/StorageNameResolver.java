package io.kestra.controller.grpc.resolver;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.grpc.EquivalentAddressGroup;
import io.grpc.NameResolver;
import io.grpc.StatusOr;
import io.grpc.SynchronizationContext;
import io.kestra.core.utils.ExecutorsUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * A gRPC {@link NameResolver} that resolves controller endpoints from Kestra internal storage.
 * <p>
 * On {@link #start(Listener2)} it pushes an initial resolution and schedules a periodic refresh
 * using the configured interval. Each tick queries the supplier and notifies the listener only
 * when the resolved set of addresses actually changes, to avoid churning the gRPC load-balancing
 * pool.
 */
@Slf4j
public class StorageNameResolver extends NameResolver {

    private static final String AUTHORITY = "controllers";

    private final Supplier<List<EquivalentAddressGroup>> addressSupplier;
    private final Duration refreshInterval;
    private final SynchronizationContext syncContext;

    private volatile Listener2 listener;
    private volatile ScheduledExecutorService scheduler;
    private volatile ScheduledFuture<?> refreshFuture;
    private volatile Set<EquivalentAddressGroup> lastAddresses = Set.of();

    /**
     * Creates a new {@link StorageNameResolver}.
     *
     * @param addressSupplier supplies the current list of controller endpoints.
     * @param refreshInterval how often to poll the supplier.
     * @param syncContext     the channel's synchronization context; all listener notifications
     *                        must be delivered on it (see {@link #resolve()}).
     */
    public StorageNameResolver(
        final Supplier<List<EquivalentAddressGroup>> addressSupplier,
        final Duration refreshInterval,
        final SynchronizationContext syncContext) {
        this.addressSupplier = Objects.requireNonNull(addressSupplier);
        this.refreshInterval = Objects.requireNonNull(refreshInterval);
        this.syncContext = Objects.requireNonNull(syncContext);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start(Listener2 listener) {
        this.listener = listener;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("storage-name-resolver-", 0).factory()
        );
        resolve();
        long intervalMillis = refreshInterval.toMillis();
        this.refreshFuture = scheduler.scheduleAtFixedRate(
            this::resolveQuietly,
            intervalMillis,
            intervalMillis,
            TimeUnit.MILLISECONDS
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void refresh() {
        resolveQuietly();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getServiceAuthority() {
        return AUTHORITY;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shutdown() {
        if (refreshFuture != null) {
            refreshFuture.cancel(false);
        }
        if (scheduler != null) {
            ExecutorsUtils.closeExecutorService("storage-name-resolver", scheduler, Duration.ofSeconds(10));
        }
    }

    private void resolveQuietly() {
        try {
            resolve();
        } catch (Exception e) {
            log.warn("Storage name resolver refresh failed", e);
        }
    }

    // Synchronized to serialize concurrent refresh() and scheduled-tick invocations: without the lock,
    // two threads racing on the compare-and-notify can reorder listener.onResult2 calls and leave
    // gRPC with a stale address set as "latest".
    private synchronized void resolve() {
        if (listener == null) {
            return;
        }
        List<EquivalentAddressGroup> addresses = addressSupplier.get();
        Set<EquivalentAddressGroup> next = Set.copyOf(addresses);
        if (next.equals(lastAddresses)) {
            return;
        }
        lastAddresses = next;
        // onResult2 must run on the channel's SynchronizationContext, not our scheduler thread.
        syncContext.execute(() -> listener.onResult2(ResolutionResult.newBuilder()
            .setAddressesOrError(StatusOr.fromValue(addresses))
            .build()));
    }
}
