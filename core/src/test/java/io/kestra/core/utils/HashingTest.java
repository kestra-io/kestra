package io.kestra.core.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HashingTest {

    @Test
    void shouldReturnConsistentHashString() {
        assertEquals(Hashing.hashToString("random"), "52a21b70c71a4e7819b310ddc9f83874");
    }

    @Test
    void shouldReturnConsistentHashLong() {
        assertEquals(Hashing.hashToLong("random"), 8668895776616456786L);
    }

    @Test
    void shouldReturnConsistentHashStringForMultipleValues() {
        // Values are joined without a separator, so ("ran", "dom") equals ("random")
        assertEquals("52a21b70c71a4e7819b310ddc9f83874", Hashing.hashToString("ran", "dom"));
        assertEquals(Hashing.hashToString("random"), Hashing.hashToString("ran", "dom"));
        // Different orderings produce different hashes
        assertNotEquals(Hashing.hashToString("foo", "bar"), Hashing.hashToString("bar", "foo"));
    }
}