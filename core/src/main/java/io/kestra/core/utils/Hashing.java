package io.kestra.core.utils;

import com.google.common.base.Charsets;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utilities for hashing.
 */
public final class Hashing {

    /**
     * Returns a consistent hash value for the given input, using
     * a non-cryptographic hash function.
     *
     * @param value the value to be hashed.
     * @return the string hash value.
     */
    public static String hashToString(final String value) {
        return getHashString(value).toString();
    }

    /**
     * Returns a consistent hash value for the given input, using
     * a non-cryptographic hash function.
     *
     * @param value the value to be hashed.
     * @return the long hash value.
     */
    public static long hashToLong(final String value) {
        return getHashString(value).asLong();
    }

    /**
     * Hashes the given value using SHA-512 algorithm.
     *
     * @param value     the value to be hashed.
     * @param salt      an optional salt to be added to the value.
     * @return          the digest.
     */
    public static byte[] sha512Hash(final byte[] value, byte[] salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            if (salt != null && salt.length > 0) {
                md.update(salt);
            }
            return md.digest(value);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    public static String encodeBytesToHex(final byte[] bytes) {
        return HashCode.fromBytes(bytes).toString();
    }

    public static byte[] decodeHexToBytes(final String value) {
        return HashCode.fromString(value).asBytes();
    }

    private static HashCode getHashString(String value) {
        return com.google.common.hash.Hashing.murmur3_128().hashString(value, Charsets.UTF_8);
    }
}
