package io.kestra.controller.resolver;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.kestra.controller.grpc.resolver.StaticNameResolver;
import io.kestra.controller.grpc.resolver.StaticNameResolverProvider;

import io.grpc.EquivalentAddressGroup;
import io.grpc.HttpConnectProxiedSocketAddress;
import io.grpc.NameResolver;
import io.grpc.ProxyDetector;
import io.grpc.SynchronizationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StaticNameResolverTest {

    private static final ProxyDetector NO_PROXY = target -> null;

    @Test
    void shouldResolveToStaticAddresses() {
        List<EquivalentAddressGroup> addresses = List.of(
            new EquivalentAddressGroup(new InetSocketAddress("controller-1.example.com", 9096)),
            new EquivalentAddressGroup(new InetSocketAddress("controller-2.example.com", 9097))
        );

        StaticNameResolver resolver = new StaticNameResolver(addresses, NO_PROXY);

        AtomicReference<NameResolver.ResolutionResult> result = new AtomicReference<>();
        resolver.start(new TestListener(result));

        assertThat(result.get()).isNotNull();
        assertThat(result.get().getAddresses()).hasSize(2);
    }

    @Test
    void shouldRefreshAddresses() {
        List<EquivalentAddressGroup> addresses = List.of(
            new EquivalentAddressGroup(new InetSocketAddress("localhost", 9096))
        );

        StaticNameResolver resolver = new StaticNameResolver(addresses, NO_PROXY);

        AtomicReference<NameResolver.ResolutionResult> result = new AtomicReference<>();
        resolver.start(new TestListener(result));

        // Clear the result
        result.set(null);

        // Refresh should re-resolve
        resolver.refresh();

        assertThat(result.get()).isNotNull();
        assertThat(result.get().getAddresses()).hasSize(1);
    }

    @Test
    void shouldReturnControllersAuthority() {
        StaticNameResolver resolver = new StaticNameResolver(List.of(), NO_PROXY);
        assertThat(resolver.getServiceAuthority()).isEqualTo("controllers");
    }

    @Test
    void providerShouldCreateResolverFromEndpointsEncodedInTargetUri() {
        StaticNameResolverProvider provider = new StaticNameResolverProvider();

        assertThat(provider.getDefaultScheme()).isEqualTo("static");

        NameResolver resolver = provider.newNameResolver(URI.create("static:///localhost:9096"), resolverArgs(NO_PROXY));
        assertThat(resolver).isNotNull();
        assertThat(resolver).isInstanceOf(StaticNameResolver.class);

        AtomicReference<NameResolver.ResolutionResult> result = new AtomicReference<>();
        resolver.start(new TestListener(result));
        assertThat(result.get().getAddresses()).containsExactly(
            new EquivalentAddressGroup(new InetSocketAddress("localhost", 9096))
        );
    }

    @Test
    void providerShouldRoundTripEndpointsThroughTargetUri() {
        // Given
        List<InetSocketAddress> endpoints = List.of(
            InetSocketAddress.createUnresolved("controller-1.example.com", 9096),
            InetSocketAddress.createUnresolved("controller-2.example.com", 9097)
        );

        // When
        String target = StaticNameResolverProvider.targetFor(endpoints);

        // Then
        assertThat(target).isEqualTo("static:///controller-1.example.com:9096,controller-2.example.com:9097");

        NameResolver resolver = new StaticNameResolverProvider().newNameResolver(URI.create(target), resolverArgs(NO_PROXY));
        AtomicReference<NameResolver.ResolutionResult> result = new AtomicReference<>();
        resolver.start(new TestListener(result));
        assertThat(result.get().getAddresses()).containsExactly(
            new EquivalentAddressGroup(new InetSocketAddress("controller-1.example.com", 9096)),
            new EquivalentAddressGroup(new InetSocketAddress("controller-2.example.com", 9097))
        );
    }

    @Test
    void providerShouldRoundTripBracketedIpv6Endpoints() {
        // Given
        String target = StaticNameResolverProvider.targetFor(List.of(InetSocketAddress.createUnresolved("[::1]", 9096)));

        // Then - brackets are stripped so the target stays a valid URI
        assertThat(target).isEqualTo("static:///::1:9096");

        NameResolver resolver = new StaticNameResolverProvider().newNameResolver(URI.create(target), resolverArgs(NO_PROXY));
        AtomicReference<NameResolver.ResolutionResult> result = new AtomicReference<>();
        resolver.start(new TestListener(result));
        assertThat(result.get().getAddresses()).containsExactly(
            new EquivalentAddressGroup(new InetSocketAddress("::1", 9096))
        );
    }

    @Test
    void providerShouldRejectTargetUriWithoutEndpoints() {
        StaticNameResolverProvider provider = new StaticNameResolverProvider();

        assertThatThrownBy(() -> provider.newNameResolver(URI.create("static:///"), resolverArgs(NO_PROXY)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No controller endpoint");
        assertThatThrownBy(() -> provider.newNameResolver(URI.create("static:///localhost"), resolverArgs(NO_PROXY)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid controller endpoint");
        assertThatThrownBy(() -> provider.newNameResolver(URI.create("static:///localhost:abc"), resolverArgs(NO_PROXY)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid controller endpoint");
    }

    @Test
    void providerShouldReturnNullForWrongScheme() {
        StaticNameResolverProvider provider = new StaticNameResolverProvider();

        NameResolver resolver = provider.newNameResolver(URI.create("dns:///localhost"), resolverArgs(NO_PROXY));
        assertThat(resolver).isNull();
    }

    @Test
    void providerShouldRouteEndpointsThroughProxyWhenDetectorSelectsOne() {
        // Given
        InetSocketAddress proxy = new InetSocketAddress(InetAddress.getLoopbackAddress(), 3128);
        List<InetSocketAddress> detected = new ArrayList<>();
        ProxyDetector detector = target ->
        {
            detected.add((InetSocketAddress) target);
            return HttpConnectProxiedSocketAddress.newBuilder()
                .setTargetAddress((InetSocketAddress) target)
                .setProxyAddress(proxy)
                .build();
        };

        // When
        NameResolver resolver = new StaticNameResolverProvider()
            .newNameResolver(URI.create("static:///controller-1.example.com:9096"), resolverArgs(detector));
        AtomicReference<NameResolver.ResolutionResult> result = new AtomicReference<>();
        resolver.start(new TestListener(result));

        // Then
        assertThat(detected).singleElement().satisfies(target ->
        {
            assertThat(target.isUnresolved()).isTrue();
            assertThat(target.getHostString()).isEqualTo("controller-1.example.com");
            assertThat(target.getPort()).isEqualTo(9096);
        });
        assertThat(result.get().getAddresses()).singleElement()
            .satisfies(group -> assertThat(group.getAddresses()).singleElement()
                .isInstanceOfSatisfying(HttpConnectProxiedSocketAddress.class, address ->
                {
                    assertThat(address.getProxyAddress()).isEqualTo(proxy);
                    assertThat(address.getTargetAddress().getHostString()).isEqualTo("controller-1.example.com");
                }));
    }

    private static NameResolver.Args resolverArgs(ProxyDetector proxyDetector) {
        return NameResolver.Args.newBuilder()
            .setDefaultPort(50051)
            .setProxyDetector(proxyDetector)
            .setSynchronizationContext(new SynchronizationContext((t, e) ->
            {
                /* no-op */ }))
            .setServiceConfigParser(new NameResolver.ServiceConfigParser() {
                @Override
                public NameResolver.ConfigOrError parseServiceConfig(Map<String, ?> rawServiceConfig) {
                    return NameResolver.ConfigOrError.fromConfig(rawServiceConfig);
                }
            })
            .build();
    }

    private static class TestListener extends NameResolver.Listener2 {
        private final AtomicReference<NameResolver.ResolutionResult> result;

        TestListener(AtomicReference<NameResolver.ResolutionResult> result) {
            this.result = result;
        }

        @Override
        public void onResult(NameResolver.ResolutionResult resolutionResult) {
            result.set(resolutionResult);
        }

        @Override
        public void onError(io.grpc.Status error) {
            // Not used in tests
        }
    }
}
