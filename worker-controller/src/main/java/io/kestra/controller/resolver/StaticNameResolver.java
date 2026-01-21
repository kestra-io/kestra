package io.kestra.controller.resolver;

import io.grpc.EquivalentAddressGroup;
import io.grpc.NameResolver;

import java.util.List;

/**
 * A gRPC NameResolver that resolves to a static list of controller addresses.
 * <p>
 * This resolver returns the same list of addresses on every resolution,
 * enabling load balancing across a fixed set of controller endpoints.
 */
public class StaticNameResolver extends NameResolver {

    private static final String AUTHORITY = "controllers";

    private final List<EquivalentAddressGroup> addresses;
    private volatile Listener2 listener;

    /**
     * Creates a new StaticNameResolver with the given addresses.
     *
     * @param addresses the list of controller addresses
     */
    public StaticNameResolver(List<EquivalentAddressGroup> addresses) {
        this.addresses = addresses;
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
            listener.onResult(ResolutionResult.newBuilder().setAddresses(addresses).build());
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
