package io.kestra.core.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

import org.apache.commons.lang3.RandomStringUtils;
import org.bouncycastle.crypto.generators.OpenBSDBCrypt;

public final class AuthUtils {

    public static final int BCRYPT_COST = 12;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private AuthUtils() {}

    /**
     * Computes the SHA-512 hex digest of {@code salt|password} (inner pre-hash).
     * This is the input fed to {@link #bcryptDigest(String)} when storing passwords.
     */
    public static String encodePassword(String salt, String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            byte[] bytes = digest.digest((salt + "|" + password).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-512 not available", e);
        }
    }

    public static String generateSalt() {
        return RandomStringUtils.secure().next(32, true, true);
    }

    /**
     * Wraps a SHA-512 hex digest in bcrypt (cost {@value #BCRYPT_COST}).
     * Used both when persisting a new password and by the in-service migration
     * that upgrades legacy SHA-512-only hashes to bcrypt at startup.
     *
     * <p><strong>Note on input length:</strong> {@code OpenBSDBCrypt} silently truncates its
     * password input to 72 bytes.  The 128-char hex string produced by
     * {@link #encodePassword(String, String)} is 128 bytes in UTF-8, so only the first 72 hex
     * characters are actually fed to the bcrypt key-expansion.  This is safe because both
     * {@code generate} and {@code checkPassword} truncate consistently, and finding two SHA-512
     * hex outputs that share a 72-byte prefix requires breaking SHA-512 preimage resistance.
     *
     * @param sha512Hex the 128-char hex output of {@link #encodePassword(String, String)}
     * @return a bcrypt modular-crypt string starting with {@code $2y$}
     */
    public static String bcryptDigest(String sha512Hex) {
        byte[] salt = new byte[16];
        SECURE_RANDOM.nextBytes(salt);
        return OpenBSDBCrypt.generate("2y", sha512Hex.getBytes(StandardCharsets.UTF_8), salt, BCRYPT_COST);
    }

    /**
     * Hashes {@code password} for storage: {@code bcrypt(sha512(salt|password))}.
     */
    public static String hashPassword(String salt, String password) {
        return bcryptDigest(encodePassword(salt, password));
    }

    /**
     * Verifies {@code password} against a stored bcrypt hash.
     *
     * @return {@code true} if the password matches; {@code false} if it does not or if
     *         {@code storedHash} is not a valid bcrypt string (fail-closed for legacy SHA-512 values)
     */
    public static boolean matches(String salt, String password, String storedHash) {
        try {
            return OpenBSDBCrypt.checkPassword(storedHash, encodePassword(salt, password).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }
}
