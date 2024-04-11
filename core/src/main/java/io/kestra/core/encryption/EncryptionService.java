package io.kestra.core.encryption;

import com.google.common.primitives.Bytes;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Service for encryption and decryption of secrets.
 */
public class EncryptionService {
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final int IV_LENGTH = 12;
    private static final int AUTH_TAG_LENGTH = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Encrypt a String using the AES/GCM/NoPadding algorithm and the provided key.
     * The key must be base64 encoded.
     * The IV is concatenated at the beginning of the string.
     */
    public static String encrypt(String key, String plainText) throws GeneralSecurityException {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        byte[] keyBytes = Base64.getDecoder().decode(key);
        SecretKey secretKey = new SecretKeySpec(keyBytes, KEY_ALGORITHM);
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        byte[] iv = generateIv();
        GCMParameterSpec ivParameter= new GCMParameterSpec(AUTH_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameter);
        byte[] encrypted = cipher.doFinal(plainText.getBytes());
        byte[] output = Bytes.concat(iv, encrypted);
        return Base64.getEncoder().encodeToString(output);
    }

    /**
     * Decrypt a String using the AES/GCM/NoPadding algorithm and the provided key.
     * The key must be base64 encoded.
     * The IV is recovered from the beginning of the string.
     */
    public static String decrypt(String key, String cipherText) throws GeneralSecurityException {
        if (cipherText == null || cipherText.isEmpty()) {
            return cipherText;
        }

        byte[] keyBytes = Base64.getDecoder().decode(key);
        SecretKey secretKey = new SecretKeySpec(keyBytes, KEY_ALGORITHM);
        byte[] input = Base64.getDecoder().decode(cipherText);
        byte[] iv = Arrays.copyOf(input, IV_LENGTH);
        byte[] encrypted =Arrays.copyOfRange(input, IV_LENGTH, input.length);
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        GCMParameterSpec ivParameter= new GCMParameterSpec(AUTH_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameter);
        byte[] plainText = cipher.doFinal(encrypted);
        return new String(plainText);
    }

    private static byte[] generateIv() {
        byte[] iv = new byte[IV_LENGTH];
        SECURE_RANDOM.nextBytes(iv);
        return iv;
    }
}
