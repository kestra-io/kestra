package io.kestra.controller.grpc.resolver;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import io.grpc.EquivalentAddressGroup;
import io.grpc.NameResolver;
import io.grpc.NameResolverProvider;

/**
 * A gRPC {@link NameResolverProvider} that serves the {@code storage:///} scheme, backed by
 * Kestra internal storage.
 * <p>
 * Usage:
 *
 * <pre>
 * ManagedChannelBuilder.forTarget("storage:///controllers")
 * </pre>
 */
public class StorageNameResolverProvider extends NameResolverProvider {

    private static final String SCHEME = "storage";
    private static final int PRIORITY = 5;

    private final Supplier<List<EquivalentAddressGroup>> addressSupplier;
    private final Duration refreshInterval;

    /**
     * Creates a new provider.
     *
     * @param addressSupplier supplies the current list of controller endpoints, polled on a schedule.
     * @param refreshInterval how often the returned resolver polls the supplier.
     */
    public StorageNameResolverProvider(
        final Supplier<List<EquivalentAddressGroup>> addressSupplier,
        final Duration refreshInterval) {
        this.addressSupplier = Objects.requireNonNull(addressSupplier);
        this.refreshInterval = Objects.requireNonNull(refreshInterval);
    }

    @Override
    public NameResolver newNameResolver(URI targetUri, NameResolver.Args args) {
        if (!SCHEME.equals(targetUri.getScheme())) {
            return null;
        }
        return new StorageNameResolver(addressSupplier, refreshInterval, args.getSynchronizationContext());
    }

    @Override
    public String getDefaultScheme() {
        return SCHEME;
    }

    @Override
    protected boolean isAvailable() {
        return true;
    }

    @Override
    protected int priority() {
        return PRIORITY;
    }
}
