package io.kestra.core.storages;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;

import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link StorageInterface} methods carry {@code @Retryable} ({@code @Around} AOP advice), and the bean is
 * produced by a {@code @Factory} method in {@code KestraBeansFactory}. This asserts that the bean injected
 * from the Micronaut context is still an {@code instanceof} of an extra capability interface (here
 * {@link SignedUrlCapable}) implemented by the concrete storage, rather than an AOP proxy that only
 * implements the declared {@code StorageInterface} type.
 */
@KestraTest(rebuildContext = true)
class StorageInterfaceProxyTest {
    @Inject
    private StorageInterface storageInterface;

    @Test
    void shouldExposeExtraCapabilityInterfaceOnInjectedBean() {
        assertThat(storageInterface).isInstanceOf(SignedUrlCapable.class);
    }

    /**
     * Declared to return the {@code StorageInterface} type, exactly like
     * {@code KestraBeansFactory#storageInterface}, so this reproduces whether Micronaut's AOP proxy for
     * {@code @Retryable} hides the concrete {@link TestSignedUrlStorage}'s extra {@link SignedUrlCapable}.
     */
    @Singleton
    public StorageInterface testStorageInterface() {
        return new TestSignedUrlStorage();
    }

    public static class TestSignedUrlStorage extends NoopStorageInterface implements SignedUrlCapable {
        @Override
        public Optional<SignedUrl> sign(String tenantId, @Nullable String namespace, URI uri, Operation operation, Duration ttl) {
            return Optional.of(new SignedUrl(uri, Map.of(), Instant.now().plus(ttl)));
        }
    }
}
