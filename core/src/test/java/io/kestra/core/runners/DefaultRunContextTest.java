package io.kestra.core.runners;

import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@MicronautTest
class DefaultRunContextTest {

    @Inject
    ApplicationContext applicationContext;

    @Test
    void shouldGetKestraVersion() {
        DefaultRunContext runContext = new DefaultRunContext();
        runContext.init(applicationContext);
        Assertions.assertNotNull(runContext.version());
    }
}