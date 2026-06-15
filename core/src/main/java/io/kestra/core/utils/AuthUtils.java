package io.kestra.core.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

import org.apache.commons.lang3.RandomStringUtils;
import org.bouncycastle.crypto.generators.OpenBSDBCrypt;

/**
 * Utility class for BasicAuth password hashing.
 *
 * <p>Passwords at rest are stored as {@code bcrypt(sha512(salt|password))}.
 * The SHA-512 pre-hash step normalises input to a fixed-length 128-char hex string,
 * making bcrypt's 72-byte input truncation unexploitable via SHA-512 preimage resistance
 * (two different plaintexts would need to produce hex strings with identical first-72-char
 * prefixes, which requires breaking SHA-512). The bcrypt outer step provides the required
 * computational work factor against offline dictionary attacks (CWE-916).
 */
public final class AuthUtils {

    /** bcrypt work factor – 2^12 = 4096 key-expansion rounds. */
    public static final int BCRYPT_COST = 12;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private AuthUtils() {}

    /**
     * Computes the SHA-512 hex digest of {@code salt|password} (inner pre-hash).
     * This value is the input to {@link #bcryptDigest(String)}.
     *
     * @param salt     the per-user salt (see {@link #generateSalt()})
     * @param password the plaintext password
     * @return 128-char lower-hex SHA-512 digest
     */
    public static String encodePassword(String salt, String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            byte[] bytes = digest.digest((salt + "|" + password).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-512 is required by the JVM spec — this cannot happen.
            throw new IllegalStateException("SHA-512 not available", e);
        }
    }

    /**
     * Generates a 32-character alphanumeric salt using a cryptographically secure RNG.
     *
     * @return a new random salt
     */
    public static String generateSalt() {
        return RandomStringUtils.secure().next(32, true, true);
    }

    /**
     * Wraps a SHA-512 hex digest in bcrypt (cost {@value #BCRYPT_COST}).
     * Used both when persisting a password and by the migration script.
     *
     * @param sha512Hex the output of {@link #encodePassword(String, String)}
     * @return a bcrypt modular-crypt string starting with {@code $2y$}
     */
    public static String bcryptDigest(String sha512Hex) {
        byte[] salt = new byte[16];
        SECURE_RANDOM.nextBytes(salt);
        return OpenBSDBCrypt.generate("2y", sha512Hex.getBytes(StandardCharsets.UTF_8), salt, BCRYPT_COST);
    }

    /**
     * Hashes {@code password} for storage: {@code bcrypt(sha512(salt|password))}.
     *
     * @param salt     the per-user salt
     * @param password the plaintext password
     * @return a bcrypt modular-crypt string suitable for storing in the database
     */
    public static String hashPassword(String salt, String password) {
        return bcryptDigest(encodePassword(salt, password));
    }

    /**
     * Verifies {@code password} against a stored bcrypt hash.
     *
     * @param salt        the per-user salt used when the hash was created
     * @param password    the plaintext password to verify
     * @param storedHash  the bcrypt modular-crypt string from the database
     * @return {@code true} if the password matches, {@code false} otherwise
     *         (including when {@code storedHash} is not a valid bcrypt string)
     */
    public static boolean matches(String salt, String password, String storedHash) {
        try {
            return OpenBSDBCrypt.checkPassword(storedHash, encodePassword(salt, password).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            // storedHash is malformed (e.g. a legacy SHA-512 hex string) – fail closed
            return false;
        }
    }
}
