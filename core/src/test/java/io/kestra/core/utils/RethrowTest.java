package io.kestra.core.utils;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RethrowTest {
    @Test
    void failAwareConsumer() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setErr(new PrintStream(out));

        RuntimeException runtimeException = assertThrows(RuntimeException.class, () -> Rethrow.failAwareConsumer(c -> {
            throw new RuntimeException("loud");
        }, false).accept("anything"));
        assertThat(runtimeException.getMessage()).isEqualTo("loud");

        assertDoesNotThrow(() -> Rethrow.failAwareConsumer(c -> {
            throw new RuntimeException("silent");
        }, true).accept("anything"));
        assertThat(out.toString()).contains("Suppressed exception from consumer");
        assertThat(out.toString()).contains("java.lang.RuntimeException: silent");
    }
}
