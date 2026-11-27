package io.kestra.webserver.services.ai.agent.internals;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolResultLimitTest {

    @Test
    void shouldReturnTextUnchangedWhenWithinLimit() {
        String text = "x".repeat(ToolResultLimit.MAX_CHARS);

        assertThat(ToolResultLimit.cap(text)).isSameAs(text);
    }

    @Test
    void shouldTruncateAndNoticeWhenOverLimit() {
        String text = "x".repeat(ToolResultLimit.MAX_CHARS * 3);

        String capped = ToolResultLimit.cap(text);

        assertThat(capped).startsWith("x".repeat(ToolResultLimit.MAX_CHARS)).contains("[truncated:");
        assertThat(capped.length()).isLessThan(text.length());
    }

    @Test
    void shouldReturnNullWhenTextIsNull() {
        assertThat(ToolResultLimit.cap(null)).isNull();
    }
}
