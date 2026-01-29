package io.kestra.controller.grpc.resolver;

import io.grpc.EquivalentAddressGroup;
import io.grpc.NameResolver;
import io.grpc.NameResolverProvider;

import java.net.URI;
import java.util.List;

/**
 * A gRPC NameResolverProvider that provides static list of controller endpoints.
 * <p>
 * This provider uses the "static" scheme and allows configuring a fixed list
 * of controller addresses for load balancing.
 * <p>
 * Usage:
 * <pre>
 * ManagedChannelBuilder.forTarget("static:///controllers")
 * </pre>
 */
public class StaticNameResolverProvider extends NameResolverProvider {

    private static final String SCHEME = "static";
    private static final int PRIORITY = 5;

    private final List<EquivalentAddressGroup> addresses;

    /**
     * Creates a new StaticNameResolverProvider with the given addresses.
     *
     * @param addresses the list of controller addresses to resolve to
     */
    public StaticNameResolverProvider(List<EquivalentAddressGroup> addresses) {
        this.addresses = addresses;
    }

    @Override
    public NameResolver newNameResolver(URI targetUri, NameResolver.Args args) {
        if (!SCHEME.equals(targetUri.getScheme())) {
            return null;
        }
        return new StaticNameResolver(addresses);
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
