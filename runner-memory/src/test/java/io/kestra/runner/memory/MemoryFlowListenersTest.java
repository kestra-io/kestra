package io.kestra.runner.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.kestra.core.runners.FlowListeners;
import io.kestra.core.runners.FlowListenersTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

class MemoryFlowListenersTest extends FlowListenersTest {
    @Inject
    FlowListeners flowListenersService;

    @Test
    public void all() throws JsonProcessingException {
        this.suite(flowListenersService);
    }
}