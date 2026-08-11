package io.kestra.controller.grpc.resolver;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.grpc.EquivalentAddressGroup;
import io.grpc.NameResolver;
import io.grpc.NameResolverProvider;

/**
 * A gRPC NameResolverProvider that resolves a static list of controller endpoints
 * encoded in the channel target URI.
 * <p>
 * This provider uses the "static" scheme and is fully stateless: the endpoints are
 * carried by each channel's target (e.g. {@code static:///host1:9096,host2:9097}).
 * <p>
 * Usage:
 *
 * <pre>
 * ManagedChannelBuilder.forTarget(StaticNameResolverProvider.targetFor(endpoints))
 * </pre>
 */
public class StaticNameResolverProvider extends NameResolverProvider {

    private static final String SCHEME = "static";
    private static final int PRIORITY = 5;

    /**
     * Builds the channel target URI encoding the given endpoints.
     * <p>
     * Addresses may be unresolved ({@link InetSocketAddress#createUnresolved(String, int)});
     * only their host string and port are encoded.
     *
     * @param endpoints the controller endpoints, in {@code host:port} form once encoded
     * @return the channel target, e.g. {@code static:///host1:9096,host2:9097}
     */
    public static String targetFor(List<InetSocketAddress> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) {
            throw new IllegalArgumentException("At least one controller endpoint is required");
        }
        return SCHEME + ":///" + endpoints.stream()
            // strip the brackets of a bracketed IPv6 literal ("[::1]"): "[" is not a valid URI
            // path character, and parseAddresses() splits host from port on the last colon anyway
            .map(e -> e.getHostString().replace("[", "").replace("]", "") + ":" + e.getPort())
            .collect(Collectors.joining(","));
    }

    @Override
    public NameResolver newNameResolver(URI targetUri, NameResolver.Args args) {
        if (!SCHEME.equals(targetUri.getScheme())) {
            return null;
        }
        return new StaticNameResolver(parseAddresses(targetUri));
    }

    /**
     * Parses the {@code host:port} list encoded in the target URI path by {@link #targetFor(List)}.
     *
     * @param targetUri the channel target URI, e.g. {@code static:///host1:9096,host2:9097}
     * @return the decoded addresses
     * @throws IllegalArgumentException if the URI does not encode a valid endpoint list
     */
    static List<EquivalentAddressGroup> parseAddresses(URI targetUri) {
        String path = targetUri.getPath();
        if (path == null || path.length() <= 1) {
            throw new IllegalArgumentException("No controller endpoint encoded in target URI: " + targetUri);
        }
        return Stream.of(path.substring(1).split(","))
            .map(endpoint ->
            {
                int separator = endpoint.lastIndexOf(':');
                if (separator <= 0 || separator == endpoint.length() - 1) {
                    throw new IllegalArgumentException("Invalid controller endpoint '" + endpoint + "' in target URI: " + targetUri);
                }
                String host = endpoint.substring(0, separator);
                int port;
                try {
                    port = Integer.parseInt(endpoint.substring(separator + 1));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid controller endpoint port in '" + endpoint + "' from target URI: " + targetUri, e);
                }
                return new EquivalentAddressGroup(new InetSocketAddress(host, port));
            })
            .toList();
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
