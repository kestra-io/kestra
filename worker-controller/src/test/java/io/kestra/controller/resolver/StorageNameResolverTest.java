package io.kestra.controller.resolver;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import io.kestra.controller.grpc.resolver.StorageNameResolver;
import io.kestra.controller.grpc.resolver.StorageNameResolverProvider;

import io.grpc.EquivalentAddressGroup;
import io.grpc.NameResolver;
import io.grpc.SynchronizationContext;

import static org.assertj.core.api.Assertions.assertThat;

class StorageNameResolverTest {

    private final SynchronizationContext syncContext =
        new SynchronizationContext((t, e) -> { /* no-op */ });

    @Test
    void shouldPublishInitialAddressesOnStart() {
        // Given
        List<EquivalentAddressGroup> addresses = List.of(
            new EquivalentAddressGroup(new InetSocketAddress("controller-1", 9096)),
            new EquivalentAddressGroup(new InetSocketAddress("controller-2", 9097))
        );
        StorageNameResolver resolver = new StorageNameResolver(() -> addresses, Duration.ofMinutes(1), syncContext);
        AtomicReference<NameResolver.ResolutionResult> result = new AtomicReference<>();

        // When
        resolver.start(new TestListener(result, new AtomicInteger()));

        // Then
        assertThat(result.get()).isNotNull();
        assertThat(result.get().getAddresses()).hasSize(2);

        resolver.shutdown();
    }

    @Test
    void shouldNotNotifyListenerWhenAddressesAreUnchanged() {
        // Given
        List<EquivalentAddressGroup> addresses = List.of(
            new EquivalentAddressGroup(new InetSocketAddress("controller-1", 9096))
        );
        StorageNameResolver resolver = new StorageNameResolver(() -> addresses, Duration.ofMinutes(1), syncContext);
        AtomicInteger callCount = new AtomicInteger();
        resolver.start(new TestListener(new AtomicReference<>(), callCount));

        // When
        resolver.refresh();
        resolver.refresh();

        // Then — initial start + unchanged refreshes should produce a single notification
        assertThat(callCount.get()).isEqualTo(1);

        resolver.shutdown();
    }

    @Test
    void shouldNotifyListenerWhenAddressesChange() {
        // Given
        AtomicReference<List<EquivalentAddressGroup>> supplied = new AtomicReference<>(List.of(
            new EquivalentAddressGroup(new InetSocketAddress("controller-1", 9096))
        ));
        StorageNameResolver resolver = new StorageNameResolver(supplied::get, Duration.ofMinutes(1), syncContext);
        AtomicInteger callCount = new AtomicInteger();
        AtomicReference<NameResolver.ResolutionResult> result = new AtomicReference<>();
        resolver.start(new TestListener(result, callCount));

        // When
        supplied.set(List.of(
            new EquivalentAddressGroup(new InetSocketAddress("controller-1", 9096)),
            new EquivalentAddressGroup(new InetSocketAddress("controller-2", 9097))
        ));
        resolver.refresh();

        // Then
        assertThat(callCount.get()).isEqualTo(2);
        assertThat(result.get().getAddresses()).hasSize(2);

        resolver.shutdown();
    }

    @Test
    void shouldPollSupplierOnSchedule() {
        // Given — sub-second refresh interval so the scheduled tick fires during the test
        AtomicInteger supplierCalls = new AtomicInteger();
        StorageNameResolver resolver = new StorageNameResolver(() -> {
            supplierCalls.incrementAndGet();
            return List.of(new EquivalentAddressGroup(new InetSocketAddress("controller", 9096)));
        }, Duration.ofMillis(100), syncContext);
        resolver.start(new TestListener(new AtomicReference<>(), new AtomicInteger()));

        // When — wait for the scheduler to tick at least a couple of times
        Awaitility.await()
            .atMost(Duration.ofSeconds(2))
            .pollInterval(50, TimeUnit.MILLISECONDS)
            .until(() -> supplierCalls.get() >= 3);

        resolver.shutdown();

        // Then
        assertThat(supplierCalls.get()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void providerShouldCreateResolverForStorageScheme() {
        // Given
        StorageNameResolverProvider provider = new StorageNameResolverProvider(List::of, Duration.ofMinutes(1));

        // When
        NameResolver resolver = provider.newNameResolver(URI.create("storage:///controllers"), resolverArgs());

        // Then
        assertThat(resolver).isInstanceOf(StorageNameResolver.class);
        assertThat(provider.getDefaultScheme()).isEqualTo("storage");
    }

    @Test
    void providerShouldReturnNullForWrongScheme() {
        // Given
        StorageNameResolverProvider provider = new StorageNameResolverProvider(List::of, Duration.ofMinutes(1));

        // When / Then
        assertThat(provider.newNameResolver(URI.create("dns:///localhost"), null)).isNull();
    }

    @Test
    void shouldDeliverScheduledResolutionOnSynchronizationContext() {
        // Given — a sub-second interval so the scheduled tick (off the sync context thread) fires
        AtomicReference<List<EquivalentAddressGroup>> supplied = new AtomicReference<>(List.of(
            new EquivalentAddressGroup(new InetSocketAddress("controller-1", 9096))
        ));
        StorageNameResolver resolver = new StorageNameResolver(supplied::get, Duration.ofMillis(100), syncContext);
        AtomicBoolean offContext = new AtomicBoolean(false);
        AtomicInteger notifications = new AtomicInteger();
        resolver.start(new ContextCheckingListener(offContext, notifications));

        // When — change the addresses so the scheduled refresh (not start()) notifies the listener
        supplied.set(List.of(
            new EquivalentAddressGroup(new InetSocketAddress("controller-1", 9096)),
            new EquivalentAddressGroup(new InetSocketAddress("controller-2", 9097))
        ));
        Awaitility.await()
            .atMost(Duration.ofSeconds(2))
            .pollInterval(50, TimeUnit.MILLISECONDS)
            .until(() -> notifications.get() >= 2);
        resolver.shutdown();

        // Then — every onResult2 must run on the channel's SynchronizationContext, including the
        // one delivered from the scheduler thread (the regression this guards against).
        assertThat(offContext.get()).isFalse();
    }

    private NameResolver.Args resolverArgs() {
        return NameResolver.Args.newBuilder()
            .setDefaultPort(50051)
            .setProxyDetector(target -> null)
            .setSynchronizationContext(syncContext)
            .setServiceConfigParser(new NameResolver.ServiceConfigParser() {
                @Override
                public NameResolver.ConfigOrError parseServiceConfig(java.util.Map<String, ?> rawServiceConfig) {
                    return NameResolver.ConfigOrError.fromConfig(rawServiceConfig);
                }
            })
            .build();
    }

    private class ContextCheckingListener extends NameResolver.Listener2 {
        private final AtomicBoolean offContext;
        private final AtomicInteger callCount;

        ContextCheckingListener(AtomicBoolean offContext, AtomicInteger callCount) {
            this.offContext = offContext;
            this.callCount = callCount;
        }

        @Override
        public void onResult(NameResolver.ResolutionResult resolutionResult) {
            try {
                syncContext.throwIfNotInThisSynchronizationContext();
            } catch (IllegalStateException e) {
                offContext.set(true);
            }
            callCount.incrementAndGet();
        }

        @Override
        public void onError(io.grpc.Status error) {
            // Not used in tests
        }
    }

    private static class TestListener extends NameResolver.Listener2 {
        private final AtomicReference<NameResolver.ResolutionResult> result;
        private final AtomicInteger callCount;

        TestListener(AtomicReference<NameResolver.ResolutionResult> result, AtomicInteger callCount) {
            this.result = result;
            this.callCount = callCount;
        }

        @Override
        public void onResult(NameResolver.ResolutionResult resolutionResult) {
            result.set(resolutionResult);
            callCount.incrementAndGet();
        }

        @Override
        public void onError(io.grpc.Status error) {
            // Not used in tests
        }
    }
}
