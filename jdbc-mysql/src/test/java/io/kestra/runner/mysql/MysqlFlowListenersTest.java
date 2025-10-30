package io.kestra.runner.mysql;

import io.kestra.core.runners.FlowListeners;
import io.kestra.core.runners.FlowListenersTest;
import jakarta.inject.Inject;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class MysqlFlowListenersTest extends FlowListenersTest {
    @Inject
    FlowListeners flowListenersService;

    @Test
    public void all() throws TimeoutException {
        this.suite(flowListenersService);
    }
}