package io.kestra.controller.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.annotation.Bindable;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

/**
 * Configuration properties for gRPC TLS encryption.
 * <p>
 * When enabled, both the controller (server) and worker (client) use TLS for gRPC communication.
 * Supports one-way TLS and mutual TLS (mTLS).
 *
 * @param enabled                      Whether TLS is enabled for gRPC communication. Defaults to false (plaintext).
 * @param keyStore                     Keystore configuration containing the certificate and private key.
 *                                     Required on the server side when TLS is enabled.
 *                                     Required on the client side only for mTLS.
 * @param trustStore                   Truststore configuration containing trusted CA certificates.
 *                                     Optional — falls back to the system default trust store if not set.
 * @param clientAuth                   Client authentication mode for the server side. Defaults to NONE.
 *                                     Set to REQUIRE for mutual TLS (mTLS).
 * @param insecureTrustAllCertificates When true, the client skips CA certificate verification.
 *                                     For development use only. Defaults to false.
 * @param authorityOverride            Overrides the hostname used for TLS certificate verification on the client side.
 *                                     Required for static discovery where the synthetic URI authority ("controllers")
 *                                     does not match the server certificate's SANs. Set this to a hostname present
 *                                     in the server certificate's Subject Alternative Names (e.g., "kestra-controller").
 *                                     Not needed for DNS discovery (authority is derived from the DNS hostname).
 */
@ConfigurationProperties("kestra.grpc.tls")
public record GrpcTlsConfiguration(
    @Bindable(defaultValue = "false")
    boolean enabled,

    @Nullable
    KeyStoreConfig keyStore,

    @Nullable
    TrustStoreConfig trustStore,

    @Bindable(defaultValue = "NONE")
    ClientAuth clientAuth,

    @Bindable(defaultValue = "false")
    boolean insecureTrustAllCertificates,

    @Nullable
    String authorityOverride
) {
    /**
     * Client authentication mode for mTLS.
     */
    public enum ClientAuth {
        /** No client certificate required. */
        NONE,
        /** Client certificate requested but not required. */
        OPTIONAL,
        /** Client certificate required (mTLS). */
        REQUIRE
    }

    /**
     * Keystore configuration holding a certificate chain and private key.
     *
     * @param path        Path to the keystore file.
     * @param type        Keystore type (e.g., PKCS12, JKS). Defaults to PKCS12.
     * @param password    Password for the keystore.
     * @param keyPassword Password for the private key entry. If not set, falls back to the keystore password.
     *                    Typically needed only for JKS keystores where key and store passwords differ.
     */
    @ConfigurationProperties("key-store")
    @Requires(property = "kestra.grpc.tls.key-store.path")
    public record KeyStoreConfig(
        String path,
        @Bindable(defaultValue = "PKCS12")
        String type,
        @Nullable
        String password,
        @Nullable
        String keyPassword
    ) {
        @Override
        public String toString() {
            return "KeyStoreConfig[path=" + path + ", type=" + type + "]";
        }
    }

    /**
     * Truststore configuration holding trusted CA certificates.
     *
     * @param path     Path to the truststore file.
     * @param type     Truststore type (e.g., PKCS12, JKS). Defaults to PKCS12.
     * @param password Password for the truststore.
     */
    @ConfigurationProperties("trust-store")
    @Requires(property = "kestra.grpc.tls.trust-store.path")
    public record TrustStoreConfig(
        String path,
        @Bindable(defaultValue = "PKCS12")
        String type,
        @Nullable
        String password
    ) {
        @Override
        public String toString() {
            return "TrustStoreConfig[path=" + path + ", type=" + type + "]";
        }
    }

    /**
     * Loads a {@link KeyManagerFactory} from the given keystore configuration.
     *
     * @param config the keystore configuration.
     * @return a configured {@link KeyManagerFactory}.
     */
    public static KeyManagerFactory loadKeyManagerFactory(KeyStoreConfig config) throws IOException, GeneralSecurityException {
        KeyStore ks = KeyStore.getInstance(config.type());
        char[] storePassword = config.password() != null ? config.password().toCharArray() : null;
        try (var fis = new FileInputStream(config.path())) {
            ks.load(fis, storePassword);
        }
        // Use keyPassword if set, otherwise fall back to the keystore password
        char[] keyPassword = config.keyPassword() != null ? config.keyPassword().toCharArray() : storePassword;
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, keyPassword);
        return kmf;
    }

    /**
     * Loads a {@link TrustManagerFactory} from the given truststore configuration.
     *
     * @param config the truststore configuration.
     * @return a configured {@link TrustManagerFactory}.
     */
    public static TrustManagerFactory loadTrustManagerFactory(TrustStoreConfig config) throws IOException, GeneralSecurityException {
        KeyStore ts = KeyStore.getInstance(config.type());
        try (var fis = new FileInputStream(config.path())) {
            ts.load(fis, config.password() != null ? config.password().toCharArray() : null);
        }
        if (ts.size() == 0) {
            throw new IllegalStateException(
                "Truststore at '" + config.path() + "' contains no trusted certificate entries. "
                    + "Verify that the file contains at least one CA certificate stored as a trusted entry."
            );
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ts);
        return tmf;
    }
}
