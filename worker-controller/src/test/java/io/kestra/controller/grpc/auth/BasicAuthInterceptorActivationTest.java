package io.kestra.controller.grpc.auth;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.PropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The interceptors are what enforce the credentials, so their presence has to track the enabled property
 * exactly: absent while it is off, present as soon as it is on.
 */
class BasicAuthInterceptorActivationTest {

    @Test
    void shouldInstallInterceptorsWhenEnabled() {
        try (ApplicationContext context = run(true)) {
            assertThat(context.containsBean(BasicAuthServerInterceptor.class)).isTrue();
            assertThat(context.containsBean(BasicAuthClientInterceptor.class)).isTrue();
        }
    }

    @Test
    void shouldNotInstallInterceptorsWhenDisabled() {
        try (ApplicationContext context = run(false)) {
            assertThat(context.containsBean(BasicAuthServerInterceptor.class)).isFalse();
            assertThat(context.containsBean(BasicAuthClientInterceptor.class)).isFalse();
        }
    }

    private static ApplicationContext run(boolean enabled) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("kestra.server-type", "STANDALONE");
        properties.put("kestra.grpc.basic-auth.enabled", String.valueOf(enabled));
        if (enabled) {
            properties.put("kestra.grpc.basic-auth.username", "worker");
            properties.put("kestra.grpc.basic-auth.password", "Passw0rd");
        }
        return ApplicationContext.run(PropertySource.of("test", properties));
    }
}
