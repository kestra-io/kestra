package io.kestra.core.runners;

import org.junit.jupiter.api.Test;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

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

    @Test
    @Property(name = "kestra.server.basic-auth.username", value = "admin@kestra.io")
    @Property(name = "kestra.server.basic-auth.password", value = "password")
    void shouldFallbackToOssBasicAuthWhenSdkAuthIsNotConfigured() {
        RunContext runContext = runContextInitializer.forExecutor(
            (DefaultRunContext) runContextFactory.of()
        );

        assertThat(runContext.sdk().defaultAuthentication()).isPresent();
        assertThat(runContext.sdk().defaultAuthentication().get().apiToken()).isEmpty();
        assertThat(runContext.sdk().defaultAuthentication().get().username())
            .contains("admin@kestra.io");
        assertThat(runContext.sdk().defaultAuthentication().get().password())
            .contains("password");
    }

    @Test
    @Property(name = "kestra.tasks.sdk.authentication.username", value = "sdk-user")
    @Property(name = "kestra.tasks.sdk.authentication.password", value = "sdk-password")
    @Property(name = "kestra.server.basic-auth.username", value = "admin")
    @Property(name = "kestra.server.basic-auth.password", value = "admin-password")
    void shouldPreferSdkAuthenticationOverOssBasicAuth() {
        RunContext runContext = runContextInitializer.forExecutor(
            (DefaultRunContext) runContextFactory.of()
        );

        assertThat(runContext.sdk().defaultAuthentication()).isPresent();
        assertThat(runContext.sdk().defaultAuthentication().get().username())
            .contains("sdk-user");
        assertThat(runContext.sdk().defaultAuthentication().get().password())
            .contains("sdk-password");
    }
}
