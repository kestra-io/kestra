package io.kestra.controller.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrpcTlsConfigurationTest {

    private static final String TEST_KEYSTORE = "tls/server-keystore.p12";
    private static final String TEST_TRUSTSTORE = "tls/server-truststore.p12";
    private static final String KEYSTORE_PASSWORD = "testpass";
    private static final String TRUSTSTORE_PASSWORD = "trustpass";

    private String testResource(String name) {
        return getClass().getClassLoader().getResource(name).getPath();
    }

    // -- loadKeyManagerFactory --

    @Test
    void shouldLoadKeyManagerFactoryFromPkcs12() throws Exception {
        // Given
        var config = new GrpcTlsConfiguration.KeyStoreConfig(
            testResource(TEST_KEYSTORE), "PKCS12", KEYSTORE_PASSWORD, null
        );

        // When
        KeyManagerFactory kmf = GrpcTlsConfiguration.loadKeyManagerFactory(config);

        // Then
        assertThat(kmf).isNotNull();
        assertThat(kmf.getKeyManagers()).isNotEmpty();
    }

    @Test
    void shouldLoadKeyManagerFactoryWithSeparateKeyPassword() throws Exception {
        // Given - PKCS12 uses same password for store and key, so passing it as keyPassword should work
        var config = new GrpcTlsConfiguration.KeyStoreConfig(
            testResource(TEST_KEYSTORE), "PKCS12", KEYSTORE_PASSWORD, KEYSTORE_PASSWORD
        );

        // When
        KeyManagerFactory kmf = GrpcTlsConfiguration.loadKeyManagerFactory(config);

        // Then
        assertThat(kmf).isNotNull();
        assertThat(kmf.getKeyManagers()).isNotEmpty();
    }

    @Test
    void shouldFailWhenKeystoreFileDoesNotExist() {
        // Given
        var config = new GrpcTlsConfiguration.KeyStoreConfig(
            "/nonexistent/keystore.p12", "PKCS12", "password", null
        );

        // When/Then
        assertThatThrownBy(() -> GrpcTlsConfiguration.loadKeyManagerFactory(config))
            .isInstanceOf(IOException.class);
    }

    @Test
    void shouldFailWhenKeystorePasswordIsWrong() {
        // Given
        var config = new GrpcTlsConfiguration.KeyStoreConfig(
            testResource(TEST_KEYSTORE), "PKCS12", "wrongpassword", null
        );

        // When/Then
        assertThatThrownBy(() -> GrpcTlsConfiguration.loadKeyManagerFactory(config))
            .isInstanceOf(IOException.class);
    }

    // -- loadTrustManagerFactory --

    @Test
    void shouldLoadTrustManagerFactoryFromPkcs12() throws Exception {
        // Given
        var config = new GrpcTlsConfiguration.TrustStoreConfig(
            testResource(TEST_TRUSTSTORE), "PKCS12", TRUSTSTORE_PASSWORD
        );

        // When
        TrustManagerFactory tmf = GrpcTlsConfiguration.loadTrustManagerFactory(config);

        // Then
        assertThat(tmf).isNotNull();
        assertThat(tmf.getTrustManagers()).isNotEmpty();
    }

    @Test
    void shouldFailWhenTruststoreFileDoesNotExist() {
        // Given
        var config = new GrpcTlsConfiguration.TrustStoreConfig(
            "/nonexistent/truststore.p12", "PKCS12", "password"
        );

        // When/Then
        assertThatThrownBy(() -> GrpcTlsConfiguration.loadTrustManagerFactory(config))
            .isInstanceOf(IOException.class);
    }

    @Test
    void shouldFailWhenTruststorePasswordIsWrong() {
        // Given
        var config = new GrpcTlsConfiguration.TrustStoreConfig(
            testResource(TEST_TRUSTSTORE), "PKCS12", "wrongpassword"
        );

        // When/Then
        assertThatThrownBy(() -> GrpcTlsConfiguration.loadTrustManagerFactory(config))
            .isInstanceOf(IOException.class);
    }

    @Test
    void shouldFailWhenTruststoreHasNoCertificateEntries(@TempDir Path tempDir) throws Exception {
        // Given - create an empty PKCS12 truststore (no certificate entries)
        Path emptyTruststore = tempDir.resolve("empty-truststore.p12");
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, "password".toCharArray());
        try (var fos = new FileOutputStream(emptyTruststore.toFile())) {
            ks.store(fos, "password".toCharArray());
        }
        var config = new GrpcTlsConfiguration.TrustStoreConfig(
            emptyTruststore.toString(), "PKCS12", "password"
        );

        // When/Then
        assertThatThrownBy(() -> GrpcTlsConfiguration.loadTrustManagerFactory(config))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("contains no trusted certificate entries");
    }

    // -- toString redaction --

    @Test
    void shouldRedactPasswordInKeyStoreConfigToString() {
        // Given
        var config = new GrpcTlsConfiguration.KeyStoreConfig(
            "/path/to/keystore.p12", "PKCS12", "supersecret", "keysecret"
        );

        // When
        String str = config.toString();

        // Then
        assertThat(str).doesNotContain("supersecret");
        assertThat(str).doesNotContain("keysecret");
        assertThat(str).contains("path=/path/to/keystore.p12");
        assertThat(str).contains("type=PKCS12");
    }

    @Test
    void shouldRedactPasswordInTrustStoreConfigToString() {
        // Given
        var config = new GrpcTlsConfiguration.TrustStoreConfig(
            "/path/to/truststore.p12", "PKCS12", "supersecret"
        );

        // When
        String str = config.toString();

        // Then
        assertThat(str).doesNotContain("supersecret");
        assertThat(str).contains("path=/path/to/truststore.p12");
        assertThat(str).contains("type=PKCS12");
    }
}
