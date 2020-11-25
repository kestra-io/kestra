package org.kestra.runner.memory;

import org.junit.jupiter.api.Test;
import org.kestra.core.runners.FlowListenersTest;

import javax.inject.Inject;

class MemoryFlowListenersTest extends FlowListenersTest {
    @Inject
    MemoryFlowListeners flowListenersService;

    @Test
    public void all() {
        this.suite(flowListenersService);
    }
}