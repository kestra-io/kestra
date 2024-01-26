package io.kestra.core.utils;

import com.google.common.base.Charsets;
import com.google.common.hash.HashCode;

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

    private static HashCode getHashString(String value) {
        return com.google.common.hash.Hashing.murmur3_128().hashString(value, Charsets.UTF_8);
    }
}
