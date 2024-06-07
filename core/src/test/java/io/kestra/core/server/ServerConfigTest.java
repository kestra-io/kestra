package io.kestra.core.server;

import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

@KestraTest
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