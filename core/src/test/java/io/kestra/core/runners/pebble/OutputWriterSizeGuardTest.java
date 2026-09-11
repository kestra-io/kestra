package io.kestra.core.runners.pebble;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutputWriterSizeGuardTest {
    @Test
    void jsonWriterShouldThrowWhenAccumulatedOutputExceedsMaxSize() {
        JsonWriter writer = new JsonWriter(10L);

        assertThatThrownBy(() -> {
            for (int i = 0; i < 100; i++) {
                writer.writeSpecialized("xx");
            }
        }).isInstanceOf(RenderLimitExceededException.class)
            .hasMessageContaining("maximum allowed size");
    }

    @Test
    void jsonWriterShouldNotThrowWhenOutputStaysUnderMaxSize() {
        JsonWriter writer = new JsonWriter(10L);

        assertThatCode(() -> writer.writeSpecialized("under")).doesNotThrowAnyException();
    }

    @Test
    void jsonWriterWithoutLimitIsUnbounded() {
        JsonWriter writer = new JsonWriter();

        assertThatCode(() -> {
            for (int i = 0; i < 100; i++) {
                writer.writeSpecialized("xxxxxxxxxx");
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void typedObjectWriterShouldThrowWhenConcatenatedOutputExceedsMaxSize() {
        TypedObjectWriter writer = new TypedObjectWriter(10L);

        assertThatThrownBy(() -> {
            for (int i = 0; i < 100; i++) {
                writer.writeSpecialized("xx");
            }
        }).isInstanceOf(RenderLimitExceededException.class)
            .hasMessageContaining("maximum allowed size");
    }

    @Test
    void typedObjectWriterWithoutLimitIsUnbounded() {
        TypedObjectWriter writer = new TypedObjectWriter();

        assertThatCode(() -> {
            for (int i = 0; i < 100; i++) {
                writer.writeSpecialized("xxxxxxxxxx");
            }
        }).doesNotThrowAnyException();
    }
}
