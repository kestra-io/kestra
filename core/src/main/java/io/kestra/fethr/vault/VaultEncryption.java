package io.kestra.fethr.vault;

import java.security.GeneralSecurityException;

import io.kestra.core.encryption.EncryptionConfig;
import io.kestra.core.encryption.EncryptionService;

import io.micronaut.context.annotation.Context;
import jakarta.inject.Inject;

/**
 * Encrypts and decrypts stored vault material.
 *
 * <p>
 * Jackson instantiates {@link CryptographicValueSerializer} and
 * {@link CryptographicValueDeserializer} itself, so they cannot be injected. This bean bridges that
 * gap: it is eagerly created with the application context and parks {@link EncryptionConfig} where
 * those two can reach it.
 *
 * <p>
 * Going through {@code EncryptionConfig} rather than reading
 * {@code kestra.encryption.secret-key} out of the context, as the 1.x fork did, means workers are
 * handled too -- they receive the key over gRPC and {@code EncryptionConfig.initialize} installs it,
 * which a direct property read would miss.
 */
@Context
public class VaultEncryption {

    private static volatile EncryptionConfig encryptionConfig;

    @Inject
    public VaultEncryption(EncryptionConfig encryptionConfig) {
        VaultEncryption.encryptionConfig = encryptionConfig;
    }

    /**
     * Encrypts a stored value.
     *
     * @throws IllegalStateException if no encryption key is configured, because storing vault
     *         material unencrypted is never the safer fallback
     */
    static String encrypt(String plaintext) throws GeneralSecurityException {
        return EncryptionService.encrypt(key(), plaintext);
    }

    static String decrypt(String ciphertext) throws GeneralSecurityException {
        return EncryptionService.decrypt(key(), ciphertext);
    }

    private static String key() {
        if (encryptionConfig == null) {
            throw new IllegalStateException(
                "Vault encryption was used before the application context was available. "
                    + "A CryptographicValue cannot be read or written this early."
            );
        }
        return encryptionConfig.get();
    }
}
