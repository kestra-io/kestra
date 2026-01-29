package io.kestra.controller.resolver;

import io.grpc.EquivalentAddressGroup;
import io.grpc.NameResolver;
import io.kestra.controller.grpc.resolver.StaticNameResolverProvider;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

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
    void providerShouldCreateResolver() {
        List<EquivalentAddressGroup> addresses = List.of(
            new EquivalentAddressGroup(new InetSocketAddress("localhost", 9096))
        );

        StaticNameResolverProvider provider = new StaticNameResolverProvider(addresses);

        assertThat(provider.getDefaultScheme()).isEqualTo("static");
        assertThat(provider.isAvailable()).isTrue();

        NameResolver resolver = provider.newNameResolver(URI.create("static:///controllers"), null);
        assertThat(resolver).isNotNull();
        assertThat(resolver).isInstanceOf(StaticNameResolver.class);
    }

    @Test
    void providerShouldReturnNullForWrongScheme() {
        StaticNameResolverProvider provider = new StaticNameResolverProvider(List.of());

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
