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
}