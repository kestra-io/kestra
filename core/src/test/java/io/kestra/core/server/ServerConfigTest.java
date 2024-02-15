package io.kestra.core.server;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

@MicronautTest
class ServerConfigTest {

    @Inject
    ServerConfig config;

    @Test
    void test() {
        Assertions.assertNotNull(config);
        Assertions.assertEquals(config.liveness().interval(), Duration.ofSeconds(5));
        Assertions.assertNotNull(config.liveness().initialDelay());
        Assertions.assertNotNull(config.liveness().timeout());
        Assertions.assertNotNull(config.liveness().heartbeatInterval());
    }

}