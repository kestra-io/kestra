package io.kestra.core.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthUtilsTest {

    private static final String SALT = "testSalt0123456789ABCDEF012345ab";
    private static final String PASSWORD = "Password123";

    // --- encodePassword ---

    @Test
    void shouldProduceDeterministicSha512Digest() {
        String h1 = AuthUtils.encodePassword(SALT, PASSWORD);
        String h2 = AuthUtils.encodePassword(SALT, PASSWORD);
        assertThat(h1).isEqualTo(h2);
        // SHA-512 hex is 128 characters
        assertThat(h1).hasSize(128);
    }

    @Test
    void shouldProduceDifferentDigestForDifferentSalt() {
        assertThat(AuthUtils.encodePassword("saltA", PASSWORD))
            .isNotEqualTo(AuthUtils.encodePassword("saltB", PASSWORD));
    }

    @Test
    void shouldProduceDifferentDigestForDifferentPassword() {
        assertThat(AuthUtils.encodePassword(SALT, "Password123"))
            .isNotEqualTo(AuthUtils.encodePassword(SALT, "Password456"));
    }

    // --- generateSalt ---

    @Test
    void shouldGenerateDistinctSalts() {
        assertThat(AuthUtils.generateSalt()).isNotEqualTo(AuthUtils.generateSalt());
    }

    @Test
    void shouldGenerate32CharAlphanumericSalt() {
        String salt = AuthUtils.generateSalt();
        assertThat(salt).hasSize(32).matches("[a-zA-Z0-9]{32}");
    }

    // --- bcryptDigest ---

    @Test
    void shouldProduceBcryptModularCryptString() {
        String hash = AuthUtils.bcryptDigest(AuthUtils.encodePassword(SALT, PASSWORD));
        assertThat(hash).startsWith("$2y$");
        // bcrypt modular-crypt format is 60 characters
        assertThat(hash).hasSize(60);
    }

    @Test
    void shouldProduceNonDeterministicBcryptOutput() {
        String sha512 = AuthUtils.encodePassword(SALT, PASSWORD);
        // bcrypt generates a fresh random salt each time, so two calls must differ
        assertThat(AuthUtils.bcryptDigest(sha512)).isNotEqualTo(AuthUtils.bcryptDigest(sha512));
    }

    // --- hashPassword ---

    @Test
    void shouldHashPasswordToBcrypt() {
        String hash = AuthUtils.hashPassword(SALT, PASSWORD);
        assertThat(hash).startsWith("$2y$");
    }

    // --- matches ---

    @Test
    void shouldReturnTrueForCorrectPassword() {
        String hash = AuthUtils.hashPassword(SALT, PASSWORD);
        assertThat(AuthUtils.matches(SALT, PASSWORD, hash)).isTrue();
    }

    @Test
    void shouldReturnFalseForWrongPassword() {
        String hash = AuthUtils.hashPassword(SALT, PASSWORD);
        assertThat(AuthUtils.matches(SALT, "WrongPassword1", hash)).isFalse();
    }

    @Test
    void shouldReturnFalseForWrongSalt() {
        String hash = AuthUtils.hashPassword(SALT, PASSWORD);
        assertThat(AuthUtils.matches("differentSalt00000000000000000000", PASSWORD, hash)).isFalse();
    }

    @Test
    void shouldReturnFalseForMalformedStoredHash() {
        // A legacy SHA-512 hex string is not a valid bcrypt hash – must fail closed
        String legacyHash = AuthUtils.encodePassword(SALT, PASSWORD);
        assertThat(AuthUtils.matches(SALT, PASSWORD, legacyHash)).isFalse();
    }

    @Test
    void shouldReturnFalseForEmptyStoredHash() {
        assertThat(AuthUtils.matches(SALT, PASSWORD, "")).isFalse();
    }

    // --- round-trip: bcryptDigest → matches ---

    @Test
    void migrationRoundTrip_shouldVerifyAgainstWrappedSha512() {
        // Simulates what the migration does: wrap a stored SHA-512 hex in bcrypt
        // then verify that the original password still matches.
        String existingSha512 = AuthUtils.encodePassword(SALT, PASSWORD);
        String migratedHash = AuthUtils.bcryptDigest(existingSha512);
        assertThat(AuthUtils.matches(SALT, PASSWORD, migratedHash)).isTrue();
        assertThat(AuthUtils.matches(SALT, "WrongPassword1", migratedHash)).isFalse();
    }
}
