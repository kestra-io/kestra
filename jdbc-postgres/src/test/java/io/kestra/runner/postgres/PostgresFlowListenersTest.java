package io.kestra.runner.postgres;

import io.kestra.core.runners.FlowListeners;
import io.kestra.core.runners.FlowListenersTest;
import jakarta.inject.Inject;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class PostgresFlowListenersTest extends FlowListenersTest {
    @Inject
    FlowListeners flowListenersService;

    @Test
    public void all() throws TimeoutException {
        this.suite(flowListenersService);
    }
}