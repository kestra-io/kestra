package io.kestra.controller.grpc.resolver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

import io.grpc.EquivalentAddressGroup;
import io.grpc.ProxiedSocketAddress;
import io.grpc.ProxyDetector;
import lombok.extern.slf4j.Slf4j;

/**
 * Routes controller endpoints through the HTTP proxy selected by the channel's {@link ProxyDetector}.
 * <p>
 * gRPC only ever consults the detector from a name resolver, never from the transport, so a resolver
 * that skips this step dials its endpoints directly and silently ignores {@code https.proxyHost} and
 * the rest of the standard JVM proxy configuration.
 */
@Slf4j
final class ProxiedAddresses {

    private ProxiedAddresses() {
    }

    /**
     * Returns the given groups with every endpoint the detector selects a proxy for replaced by the
     * matching {@link ProxiedSocketAddress}. Endpoints with no proxy are kept as they are, and so is an
     * endpoint whose detection fails, so that a proxy configuration gRPC cannot read degrades to a
     * direct dial rather than to an empty address list.
     */
    static List<EquivalentAddressGroup> proxied(final ProxyDetector proxyDetector, final List<EquivalentAddressGroup> groups) {
        List<EquivalentAddressGroup> proxied = new ArrayList<>(groups.size());
        for (EquivalentAddressGroup group : groups) {
            List<SocketAddress> addresses = new ArrayList<>(group.getAddresses().size());
            for (SocketAddress address : group.getAddresses()) {
                addresses.add(proxiedOrDirect(proxyDetector, address));
            }
            proxied.add(new EquivalentAddressGroup(addresses, group.getAttributes()));
        }
        return proxied;
    }

    private static SocketAddress proxiedOrDirect(final ProxyDetector proxyDetector, final SocketAddress address) {
        if (!(address instanceof InetSocketAddress inetAddress)) {
            return address;
        }

        // The detector hands the address it was given back as the CONNECT target, so it must be unresolved:
        // a resolved one makes the tunnel request carry an IP, and needs the DNS lookup that an endpoint
        // reachable only through the proxy cannot satisfy.
        InetSocketAddress target = InetSocketAddress.createUnresolved(inetAddress.getHostString(), inetAddress.getPort());
        try {
            ProxiedSocketAddress proxiedAddress = proxyDetector.proxyFor(target);
            if (proxiedAddress == null) {
                return address;
            }
            log.debug("Routing controller endpoint {} through proxy {}", target, proxiedAddress);
            return proxiedAddress;
        } catch (IOException e) {
            log.warn("Cannot determine the proxy for controller endpoint {}: connecting directly.", target, e);
            return address;
        }
    }
}
