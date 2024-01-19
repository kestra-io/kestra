package io.kestra.core.crypto;

import com.google.common.primitives.Bytes;
import io.micronaut.context.annotation.Value;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

/**
 * Service for encryption and decryption of secrets.
 * It will not work if the configuration 'kestra.crypto.secret-key' is not set.
 */
@Singleton
public class CryptoService {
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int AUTH_TAG_LENGTH = 128;

    @Value("${kestra.crypto.secret-key}")
    private Optional<String> secretKey;

    private SecretKey key;
    private SecureRandom secureRandom;

    @PostConstruct
    void loadPublicKey() {
        secretKey.ifPresent(s -> {
            this.key = new SecretKeySpec(s.getBytes(), "AES");
            this.secureRandom = new SecureRandom();
        });
    }

    /**
     * Encrypt a String using the AES/GCM/NoPadding algorithm and the ${kestra.crypto.secret-key} key.
     * The IV is concatenated at the beginning of the string.
     */
    public String encrypt(String plainText) throws GeneralSecurityException {
        ensureConfiguration();

        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        byte[] iv = generateIv();
        GCMParameterSpec ivParameter= new GCMParameterSpec(AUTH_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, ivParameter);
        byte[] encrypted = cipher.doFinal(plainText.getBytes());
        byte[] output = Bytes.concat(iv, encrypted);
        return Base64.getEncoder().encodeToString(output);
    }

    /**
     * Decrypt a String using the AES/GCM/NoPadding algorithm and the ${kestra.crypto.secret-key} key.
     * The IV is recovered from the beginning of the string.
     */
    public String decrypt(String cipherText) throws GeneralSecurityException {
        ensureConfiguration();

        byte[] input = Base64.getDecoder().decode(cipherText);
        byte[] iv = Arrays.copyOf(input, IV_LENGTH);
        byte[] encrypted =Arrays.copyOfRange(input, IV_LENGTH, input.length);
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        GCMParameterSpec ivParameter= new GCMParameterSpec(AUTH_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, ivParameter);
        byte[] plainText = cipher.doFinal(encrypted);
        return new String(plainText);
    }

    private void ensureConfiguration() {
        if (secretKey.isEmpty()) {
            throw new IllegalArgumentException("You must configure a base64 encoded AES 256 bit secret key in the 'kestra.crypto.secret-key' " +
                "configuration property to be able to use encryption and decryption facilities" );
        }
    }

    private byte[] generateIv() {
        byte[] iv = new byte[IV_LENGTH];
        this.secureRandom.nextBytes(iv);
        return iv;
    }
}
