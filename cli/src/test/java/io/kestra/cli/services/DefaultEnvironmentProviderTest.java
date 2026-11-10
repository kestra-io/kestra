package io.kestra.cli.services;

import io.micronaut.context.env.Environment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultEnvironmentProviderTest {
    private final DefaultEnvironmentProvider environmentProvider = new DefaultEnvironmentProvider();

    @Test
    void shouldReturnCliEnvironmentWhenNoExtraEnvironmentsProvided() {
        String[] environments = environmentProvider.getCliEnvironments();

        assertThat(environments).containsExactly(Environment.CLI);
    }

    @Test
    void shouldReturnCliAndExtraEnvironmentsWhenExtraEnvironmentsProvided() {
        String[] environments = environmentProvider.getCliEnvironments("test1", "test2");

        assertThat(environments).containsExactly(Environment.CLI, "test1", "test2");
    }

    @Test
    void shouldReturnCliEnvironmentWhenEmptyExtraEnvironmentsProvided() {
        String[] environments = environmentProvider.getCliEnvironments(new String[0]);

        assertThat(environments).containsExactly(Environment.CLI);
    }

    @Test
    void shouldThrowExceptionWhenNullExtraEnvironmentsProvided() {
        assertThatThrownBy(() -> environmentProvider.getCliEnvironments((String[]) null))
            .isInstanceOf(NullPointerException.class);
    }
}
