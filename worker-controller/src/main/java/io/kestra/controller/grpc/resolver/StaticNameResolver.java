package io.kestra.controller.grpc.resolver;

import java.util.List;
import java.util.Objects;

import io.grpc.EquivalentAddressGroup;
import io.grpc.NameResolver;
import io.grpc.ProxyDetector;
import io.grpc.StatusOr;

/**
 * A gRPC NameResolver that resolves to a static list of controller addresses.
 * <p>
 * This resolver returns the same list of addresses on every resolution,
 * enabling load balancing across a fixed set of controller endpoints.
 * <p>
 * Each address is passed through the channel's {@link ProxyDetector} on every resolution, so that the
 * standard JVM proxy configuration is honoured as it is by gRPC's own {@code dns:///} resolver.
 */
public class StaticNameResolver extends NameResolver {

    private static final String AUTHORITY = "controllers";

    private final List<EquivalentAddressGroup> addresses;
    private final ProxyDetector proxyDetector;
    private volatile Listener2 listener;

    /**
     * Creates a new StaticNameResolver with the given addresses.
     *
     * @param addresses the list of controller addresses
     * @param proxyDetector the channel's proxy detector
     */
    public StaticNameResolver(List<EquivalentAddressGroup> addresses, ProxyDetector proxyDetector) {
        this.addresses = addresses;
        this.proxyDetector = Objects.requireNonNull(proxyDetector);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start(Listener2 listener) {
        this.listener = listener;
        resolve();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void refresh() {
        resolve();
    }

    private void resolve() {
        if (listener != null) {
            listener.onResult2(
                ResolutionResult.newBuilder()
                    .setAddressesOrError(StatusOr.fromValue(ProxiedAddresses.proxied(proxyDetector, addresses)))
                    .build()
            );
        }
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
        // No resources to clean up
    }
}
