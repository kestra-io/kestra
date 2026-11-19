package io.kestra.core.secret;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretEncodingTest {

    @Test
    void shouldDecodeWhenBase64() {
        // Given
        String value = "cGFzc3dvcmQ=";

        // When
        String decoded = SecretEncoding.BASE64.decode(value);

        // Then
        assertThat(decoded).isEqualTo("password");
    }

    @Test
    void shouldIgnoreLineBreaksWhenBase64() {
        // Given
        String value = "cGFzc3dv\ncmQ=";

        // When
        String decoded = SecretEncoding.BASE64.decode(value);

        // Then
        assertThat(decoded).isEqualTo("password");
    }

    @Test
    void shouldThrowWhenBase64AndValueIsNotEncoded() {
        assertThatThrownBy(() -> SecretEncoding.BASE64.decode("not base64!"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldReturnValueUntouchedWhenRaw() {
        // Given
        String value = "cGFzc3dvcmQ=";

        // When
        String decoded = SecretEncoding.RAW.decode(value);

        // Then
        assertThat(decoded).isEqualTo(value);
    }
}
