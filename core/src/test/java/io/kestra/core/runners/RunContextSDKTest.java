package io.kestra.core.runners;

import org.junit.jupiter.api.Test;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MicronautTest(rebuildContext = true)
class RunContextSDKTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private RunContextInitializer runContextInitializer;

    @Test
    void sdkAuthShouldReturnEmptyWhenNotSet() {
        RunContext runContext = runContextInitializer.forExecutor((DefaultRunContext) runContextFactory.of());

        assertThat(runContext.sdk().defaultAuthentication()).isEmpty();
    }

    @Test
    @Property(name = "kestra.tasks.sdk.authentication.api-token", value = "test-key")
    void sdkAuthShouldReturnApiKeyWhenSet() {
        RunContext runContext = runContextInitializer.forExecutor((DefaultRunContext) runContextFactory.of());

        assertThat(runContext.sdk().defaultAuthentication()).isPresent();
        assertThat(runContext.sdk().defaultAuthentication().get().url()).isEmpty();
        assertThat(runContext.sdk().defaultAuthentication().get().username()).isEmpty();
        assertThat(runContext.sdk().defaultAuthentication().get().password()).isEmpty();
        assertThat(runContext.sdk().defaultAuthentication().get().apiToken()).isPresent();
        assertThat(runContext.sdk().defaultAuthentication().get().apiToken().get()).isEqualTo("test-key");
    }

    @Test
    @Property(name = "kestra.tasks.sdk.authentication.username", value = "username")
    @Property(name = "kestra.tasks.sdk.authentication.password", value = "password")
    void sdkAuthShouldReturnUsernamePasswordKeyWhenSet() {
        RunContext runContext = runContextInitializer.forExecutor((DefaultRunContext) runContextFactory.of());

        assertThat(runContext.sdk().defaultAuthentication()).isPresent();
        assertThat(runContext.sdk().defaultAuthentication().get().url()).isEmpty();
        assertThat(runContext.sdk().defaultAuthentication().get().apiToken()).isEmpty();
        assertThat(runContext.sdk().defaultAuthentication().get().username()).isPresent();
        assertThat(runContext.sdk().defaultAuthentication().get().password()).isPresent();
        assertThat(runContext.sdk().defaultAuthentication().get().username().get()).isEqualTo("username");
        assertThat(runContext.sdk().defaultAuthentication().get().password().get()).isEqualTo("password");
    }

    @Test
    @Property(name = "kestra.tasks.sdk.authentication.url", value = "https://my-instance.io")
    @Property(name = "kestra.tasks.sdk.authentication.api-token", value = "test-key")
    void sdkAuthShouldReturnUrlAlongsideApiKeyWhenSet() {
        RunContext runContext = runContextInitializer.forExecutor((DefaultRunContext) runContextFactory.of());

        assertThat(runContext.sdk().defaultAuthentication()).isPresent();
        assertThat(runContext.sdk().defaultAuthentication().get().url()).contains("https://my-instance.io");
        assertThat(runContext.sdk().defaultAuthentication().get().apiToken()).contains("test-key");
    }

    @Test
    @Property(name = "kestra.tasks.sdk.authentication.url", value = "https://my-instance.io")
    void sdkAuthShouldReturnUrlOnlyWhenSet() {
        RunContext runContext = runContextInitializer.forExecutor((DefaultRunContext) runContextFactory.of());

        assertThat(runContext.sdk().defaultAuthentication()).isPresent();
        assertThat(runContext.sdk().defaultAuthentication().get().url()).contains("https://my-instance.io");
        assertThat(runContext.sdk().defaultAuthentication().get().apiToken()).isEmpty();
        assertThat(runContext.sdk().defaultAuthentication().get().username()).isEmpty();
        assertThat(runContext.sdk().defaultAuthentication().get().password()).isEmpty();
    }

    @Test
    @Property(name = "kestra.tasks.sdk.authentication.url", value = "   ")
    @Property(name = "kestra.tasks.sdk.authentication.api-token", value = "test-key")
    void sdkAuthShouldFilterOutBlankUrl() {
        RunContext runContext = runContextInitializer.forExecutor((DefaultRunContext) runContextFactory.of());

        assertThat(runContext.sdk().defaultAuthentication()).isPresent();
        assertThat(runContext.sdk().defaultAuthentication().get().url()).isEmpty();
    }

    @Test
    void sdkAuthOrThrowShouldFailFastWhenNotSet() {
        RunContext runContext = runContextInitializer.forExecutor((DefaultRunContext) runContextFactory.of());

        assertThatThrownBy(() -> runContext.sdk().defaultAuthenticationOrThrow())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No authentication method provided for the Kestra API");
    }

    @Test
    @Property(name = "kestra.tasks.sdk.authentication.url", value = "https://my-instance.io")
    void sdkAuthOrThrowShouldFailFastWhenUrlOnly() {
        RunContext runContext = runContextInitializer.forExecutor((DefaultRunContext) runContextFactory.of());

        assertThatThrownBy(() -> runContext.sdk().defaultAuthenticationOrThrow())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No authentication method provided for the Kestra API");
    }

    @Test
    @Property(name = "kestra.tasks.sdk.authentication.api-token", value = "test-key")
    void sdkAuthOrThrowShouldReturnAuthWhenSet() {
        RunContext runContext = runContextInitializer.forExecutor((DefaultRunContext) runContextFactory.of());

        assertThat(runContext.sdk().defaultAuthenticationOrThrow().apiToken()).contains("test-key");
    }
}
