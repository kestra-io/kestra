package io.kestra.core.utils;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Base62EncoderTest {

    private static final String BASE62_CHARSET_REGEX = "^[0-9A-Za-z]+$";
    private static final int ID_MAX_LENGTH = 22;

    @Test
    void shouldReturnZeroWhenEncodingZeroUuid() {
        // Given
        UUID zeroUuid = new UUID(0L, 0L);
        String expectedEncoded = "0";

        // When
        String encoded = Base62Encoder.encode(zeroUuid);

        // Then
        assertThat(encoded).isEqualTo(expectedEncoded);
    }

    @Test
    void shouldThrowNullPointerExceptionWhenEncodingNullUuid() {
        // Given
        UUID nullUuid = null;

        // When / Then
        assertThatThrownBy(() -> Base62Encoder.encode(nullUuid))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("UUID cannot be null");
    }

    @Test
    void shouldEncodeUuidToBase62() {
        // Given
        UUID uuid = UUID.randomUUID();

        // When
        String encoded = Base62Encoder.encode(uuid);

        // Then
        assertThat(encoded).matches(BASE62_CHARSET_REGEX);
    }

    @Test
    void shouldCreateValidAndNotNullId() {
        // When
        String id = Base62Encoder.createId();

        // Then
        assertThat(id).isNotNull();
        assertThat(id).matches(BASE62_CHARSET_REGEX);
    }

    @Test
    void shouldCreateUniqueIds() {
        // When
        String id1 = Base62Encoder.createId();
        String id2 = Base62Encoder.createId();

        // Then
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void shouldCreateIdOfWithinExpectedLengthOf22() {
        // When
        String id = Base62Encoder.createId();

        // Then
        assertThat(id.length()).isLessThanOrEqualTo(ID_MAX_LENGTH);
    }
}
