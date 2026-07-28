package io.kestra.controller.resolver;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.kestra.controller.grpc.resolver.StaticNameResolver;
import io.kestra.controller.grpc.resolver.StaticNameResolverProvider;

import io.grpc.EquivalentAddressGroup;
import io.grpc.NameResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StaticNameResolverTest {

    @Test
    void shouldResolveToStaticAddresses() {
        List<EquivalentAddressGroup> addresses = List.of(
            new EquivalentAddressGroup(new InetSocketAddress("controller-1.example.com", 9096)),
            new EquivalentAddressGroup(new InetSocketAddress("controller-2.example.com", 9097))
        );

        StaticNameResolver resolver = new StaticNameResolver(addresses);

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

        StaticNameResolver resolver = new StaticNameResolver(addresses);

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
        StaticNameResolver resolver = new StaticNameResolver(List.of());
        assertThat(resolver.getServiceAuthority()).isEqualTo("controllers");
    }

    @Test
    void providerShouldCreateResolverFromEndpointsEncodedInTargetUri() {
        StaticNameResolverProvider provider = new StaticNameResolverProvider();

        assertThat(provider.getDefaultScheme()).isEqualTo("static");

        NameResolver resolver = provider.newNameResolver(URI.create("static:///localhost:9096"), null);
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

        NameResolver resolver = new StaticNameResolverProvider().newNameResolver(URI.create(target), null);
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

        NameResolver resolver = new StaticNameResolverProvider().newNameResolver(URI.create(target), null);
        AtomicReference<NameResolver.ResolutionResult> result = new AtomicReference<>();
        resolver.start(new TestListener(result));
        assertThat(result.get().getAddresses()).containsExactly(
            new EquivalentAddressGroup(new InetSocketAddress("::1", 9096))
        );
    }

    @Test
    void providerShouldRejectTargetUriWithoutEndpoints() {
        StaticNameResolverProvider provider = new StaticNameResolverProvider();

        assertThatThrownBy(() -> provider.newNameResolver(URI.create("static:///"), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No controller endpoint");
        assertThatThrownBy(() -> provider.newNameResolver(URI.create("static:///localhost"), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid controller endpoint");
        assertThatThrownBy(() -> provider.newNameResolver(URI.create("static:///localhost:abc"), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid controller endpoint");
    }

    @Test
    void providerShouldReturnNullForWrongScheme() {
        StaticNameResolverProvider provider = new StaticNameResolverProvider();

        NameResolver resolver = provider.newNameResolver(URI.create("dns:///localhost"), null);
        assertThat(resolver).isNull();
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
