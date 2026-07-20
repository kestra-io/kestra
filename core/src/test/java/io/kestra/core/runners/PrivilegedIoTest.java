package io.kestra.core.runners;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrivilegedIoTest {
    @Test
    void shouldNotBeActiveByDefault() {
        assertThat(PrivilegedIo.isActive()).isFalse();
    }

    @Test
    void shouldRejectCallFromUntrustedCaller() {
        var exception = assertThrows(SecurityException.class, () -> PrivilegedIo.call(() -> "should never run"));

        assertThat(exception.getMessage()).contains(PrivilegedIoTest.class.getName());
    }

    @Test
    void shouldRejectRunFromUntrustedCaller() {
        var exception = assertThrows(SecurityException.class, () -> PrivilegedIo.run(() -> {
            throw new AssertionError("should never run");
        }));

        assertThat(exception.getMessage()).contains(PrivilegedIoTest.class.getName());
    }
}
