package io.kestra.controller.config;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.PropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrpcBasicAuthConfigurationTest {

    @Test
    void shouldBeDisabledByDefault() {
        try (ApplicationContext context = ApplicationContext.run()) {
            assertThat(context.getBean(GrpcBasicAuthConfiguration.class).enabled()).isFalse();
        }
    }

    @Test
    void shouldRejectBlankCredentialsWhenEnabled() {
        assertThatThrownBy(
            () -> load(
                Map.of(
                    "kestra.grpc.basicAuth.enabled", "true",
                    "kestra.grpc.basicAuth.password", "Passw0rd"
                )
            )
        )
            .rootCause()
            .hasMessageContaining("kestra.grpc.basicAuth.username is required");

        assertThatThrownBy(
            () -> load(
                Map.of(
                    "kestra.grpc.basicAuth.enabled", "true",
                    "kestra.grpc.basicAuth.username", "worker"
                )
            )
        )
            .rootCause()
            .hasMessageContaining("kestra.grpc.basicAuth.password is required");
    }

    @Test
    void shouldBindCredentialsWhenEnabled() {
        try (
            ApplicationContext context = ApplicationContext.run(
                PropertySource.of(
                    "test", Map.of(
                        "kestra.grpc.basicAuth.enabled", "true",
                        "kestra.grpc.basicAuth.username", "worker",
                        "kestra.grpc.basicAuth.password", "Passw0rd"
                    )
                )
            )
        ) {
            GrpcBasicAuthConfiguration config = context.getBean(GrpcBasicAuthConfiguration.class);

            assertThat(config.enabled()).isTrue();
            assertThat(config.username()).isEqualTo("worker");
            assertThat(config.password()).isEqualTo("Passw0rd");
        }
    }

    private static void load(Map<String, Object> properties) {
        try (ApplicationContext context = ApplicationContext.run(PropertySource.of("test", properties))) {
            context.getBean(GrpcBasicAuthConfiguration.class);
        }
    }
}
