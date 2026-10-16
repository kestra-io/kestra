package io.kestra.core.checkpoint;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckpointTest {
    @Test
    void shouldThrowExceptionWhenValueIsNull() {
        assertThatThrownBy(() -> new Checkpoint("tenant", "execution", "taskrun", "offset", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("The value of the checkpoint 'offset' cannot be null.");
    }
}
