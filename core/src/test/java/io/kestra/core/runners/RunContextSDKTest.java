package io.kestra.core.runners;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(runContext.sdk().defaultAuthentication().get().apiToken()).isEmpty();
        assertThat(runContext.sdk().defaultAuthentication().get().username()).isPresent();
        assertThat(runContext.sdk().defaultAuthentication().get().password()).isPresent();
        assertThat(runContext.sdk().defaultAuthentication().get().username().get()).isEqualTo("username");
        assertThat(runContext.sdk().defaultAuthentication().get().password().get()).isEqualTo("password");
    }
}
