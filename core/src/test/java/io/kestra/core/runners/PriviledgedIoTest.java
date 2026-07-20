package io.kestra.core.runners;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PriviledgedIoTest {
    @Test
    void shouldNotBeActiveByDefault() {
        assertThat(PriviledgedIo.isActive()).isFalse();
    }

    @Test
    void shouldBeActiveInsideCall() throws Exception {
        boolean active = PriviledgedIo.call(() -> PriviledgedIo.isActive());

        assertThat(active).isTrue();
    }

    @Test
    void shouldBeActiveInsideRun() {
        boolean[] active = new boolean[1];

        PriviledgedIo.run(() -> active[0] = PriviledgedIo.isActive());

        assertThat(active[0]).isTrue();
    }

    @Test
    void shouldNotBeActiveAfterCall() throws Exception {
        PriviledgedIo.call(() -> PriviledgedIo.isActive());

        assertThat(PriviledgedIo.isActive()).isFalse();
    }

    @Test
    void shouldPropagateCheckedExceptionFromCall() {
        assertThrows(IOException.class, () -> PriviledgedIo.call(() ->
        {
            throw new IOException("boom");
        }));
    }
}
