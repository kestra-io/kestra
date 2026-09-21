package io.kestra.controller.config;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.PropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrpcChannelConfigurationTest {

    @Test
    void shouldBindRetryDefaults() {
        try (ApplicationContext context = ApplicationContext.run()) {
            GrpcChannelConfiguration config = context.getBean(GrpcChannelConfiguration.class);

            assertThat(config.retry().enabled()).isTrue();
            assertThat(config.retry().maxAttempts()).isEqualTo(4);
            assertThat(config.retry().initialBackoff()).isEqualTo(Duration.ofMillis(500));
            assertThat(config.retry().maxBackoff()).isEqualTo(Duration.ofSeconds(5));
            assertThat(config.retry().backoffMultiplier()).isEqualTo(2.0);
        }
    }

    @Test
    void shouldStillBindMaxAttemptsBelowSpecMinimum() {
        // A single attempt asks for no retry, so it must bind rather than break the server at startup
        try (ApplicationContext context = ApplicationContext.run(
            PropertySource.of("test", Map.of("kestra.grpc.channel.retry.max-attempts", "1")))) {

            assertThat(context.getBean(GrpcChannelConfiguration.class).retry().maxAttempts()).isEqualTo(1);
        }
    }

    @Test
    void shouldRejectNonPositiveBackoff() {
        assertThatThrownBy(() -> loadRetry(Map.of("kestra.grpc.channel.retry.initial-backoff", "0s")))
            .rootCause()
            .hasMessageContaining("kestra.grpc.channel.retry.initial-backoff must be a positive duration");
    }

    @Test
    void shouldRejectMaxBackoffBelowInitialBackoff() {
        assertThatThrownBy(() -> loadRetry(Map.of("kestra.grpc.channel.retry.max-backoff", "100ms")))
            .rootCause()
            .hasMessageContaining("kestra.grpc.channel.retry.max-backoff must be greater than or equal to initial-backoff");
    }

    private static void loadRetry(Map<String, Object> properties) {
        try (ApplicationContext context = ApplicationContext.run(PropertySource.of("test", properties))) {
            context.getBean(GrpcChannelConfiguration.class).retry();
        }
    }
}
