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
     *
     * @see #encrypt(String, byte[])
     */
    public static String encrypt(String key, String plainText) throws GeneralSecurityException {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        byte[] output = encrypt(key, plainText.getBytes());
        return Base64.getEncoder().encodeToString(output);
    }

    /**
     * Encrypt a byte array using the AES/GCM/NoPadding algorithm and the provided key.
     * The key must be base64 encoded.
     * The IV is concatenated at the beginning of the string.
     *
     * @see #encrypt(String, String)
     */
    public static byte[] encrypt(String key, byte[] plainText) throws GeneralSecurityException {
        if (plainText == null) {
            return plainText;
        }

        byte[] keyBytes = Base64.getDecoder().decode(key);
        SecretKey secretKey = new SecretKeySpec(keyBytes, KEY_ALGORITHM);
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        byte[] iv = generateIv();
        GCMParameterSpec ivParameter= new GCMParameterSpec(AUTH_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameter);
        byte[] encrypted = cipher.doFinal(plainText);
        return Bytes.concat(iv, encrypted);
    }

    /**
     * Decrypt a String using the AES/GCM/NoPadding algorithm and the provided key.
     * The key must be base64 encoded.
     * The IV is recovered from the beginning of the string.
     *
     * @see #decrypt(String, byte[])
     */
    public static String decrypt(String key, String cipherText) throws GeneralSecurityException {
        if (cipherText == null || cipherText.isEmpty()) {
            return cipherText;
        }

        byte[] input = Base64.getDecoder().decode(cipherText);
        byte[] plainText = decrypt(key, input);
        return new String(plainText);
    }

    /**
     * Decrypt a byte array using the AES/GCM/NoPadding algorithm and the provided key.
     * The key must be base64 encoded.
     * The IV is recovered from the beginning of the byte array.
     *
     * @see #decrypt(String, String)
     */
    public static byte[] decrypt(String key, byte[] cipherText) throws GeneralSecurityException {
        if (cipherText == null) {
            return cipherText;
        }

        byte[] keyBytes = Base64.getDecoder().decode(key);
        SecretKey secretKey = new SecretKeySpec(keyBytes, KEY_ALGORITHM);
        byte[] iv = Arrays.copyOf(cipherText, IV_LENGTH);
        byte[] encrypted =Arrays.copyOfRange(cipherText, IV_LENGTH, cipherText.length);
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        GCMParameterSpec ivParameter= new GCMParameterSpec(AUTH_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameter);
        return cipher.doFinal(encrypted);
    }

    private static byte[] generateIv() {
        byte[] iv = new byte[IV_LENGTH];
        SECURE_RANDOM.nextBytes(iv);
        return iv;
    }
}
